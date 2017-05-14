package com.example.visualaudio;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;


/**
 * Created by icedcap on 13/05/2017.
 */
public class WaveformView extends View {
    //    private static final float DEFAULT_INTERVAL_OF_SECONDS = 12f; //dp
    private static final int DEFAULT_DURATION = 60;// default duration 60s
    private static final float DEFAULT_INTERVAL_OF_SECONDS = 40; //px
    private static final float DEFAULT_DEGREE_OF_SECONDS_WIDTH = 1f; //dp
    private static final float DEFAULT_DEGREE_OF_SECONDS_HEIGHT = 3f; //dp
    private static final float DEFAULT_DEGREE_OF_FIVE_SECONDS_HEIGHT = 7f; //dp
    private static final float DEFAULT_RECTANGLE_LINE_HEIGHT = 0.5F; //dp
    private static final int DEFAULT_RECTANGLE_LINE_COLOR = 0xff4b4b4b;
    private static final int DEFAULT_DEGREE_LINE_COLOR = 0xff4b4b4b;
    private static final int DEFAULT_WAVE_LINE_COLOR = 0xff4b4b4b;
    private static final int DEFAULT_RECTANGLE_CENTER_LINE_COLOR = 0xff000000;
    private static final int DEFAULT_TIME_TEXT_COLOR = 0xffbdbdbd;
    private static final float DEFAULT_TIME_TEXT_SIZE = 8; //sp

    // touch event
    private float mTouchStartX;
    private GestureDetectorCompat mGestureDetector;
    private boolean mForbidTouch;
    private long mLastDrawTime;
    private float mLeft;
    private ValueAnimator mValueAnimator;

    private long mCurrentTime; // seconds
    private int mDuration = DEFAULT_DURATION; // the material time long.
    private int[] mWaveHeights; // 40 wave line per seconds

    // dimension
    private float mMarginLeft;
    private float mMarginRight = 0;
    private float mInterValOfSeconds; // the dimension of seconds
    private float mDegreeOfSecondsWidth;
    private float mDegreeOfSecondsHeight;
    private float mDegreeOfFiveSecondsHeight;
    private float mRectangleLineHeight;

    // color
    private int mRectangleColor;
    private int mDegreeColor;
    private int mWaveColor;
    private int mTimeTextColor;
    private float mTimeTextSize;

    private Bitmap mProgressIndicator;

    // measure
    private int mWidth;
    private int mHeight;
    private int mRectangleWidth;
    private int mRectangleHeight;
    private int mRectanglePaddingTop = 8;
    private int mRectanglePaddingBottom = 8;

    // paint
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setDither(true);
        }
    };

    private final Paint mDegreePaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setDither(true);
//            setStyle(Style.FILL);
        }
    };

    private final Paint mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setDither(true);
        }
    };

    private final Paint mTimeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setDither(true);
        }
    };

    private final Paint mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
        {
            setDither(true);
        }
    };


    private WaveformListener mListener;

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Set preview models
        if (isInEditMode()) {
            measure(0, 0);
        } else {
            init(context, attrs, defStyleAttr);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // Set preview models
        if (isInEditMode()) {
            measure(0, 0);
        } else {
            init(context, attrs, defStyleAttr);
        }
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {

        // Always draw
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ViewCompat.setLayerType(this, ViewCompat.LAYER_TYPE_SOFTWARE, null);

        // Retrieve attributes from xml
        final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveformView);
        try {
            mInterValOfSeconds = typedArray.getDimension(R.styleable.WaveformView_interval_of_seconds,
                    DEFAULT_INTERVAL_OF_SECONDS);

            mDegreeOfSecondsWidth = typedArray.getDimension(R.styleable.WaveformView_degree_of_second_width,
                    DimenUtil.dip2px(getContext(), DEFAULT_DEGREE_OF_SECONDS_WIDTH));

            mDegreeOfSecondsHeight = typedArray.getDimension(R.styleable.WaveformView_degree_of_second_height,
                    DimenUtil.dip2px(getContext(), DEFAULT_DEGREE_OF_SECONDS_HEIGHT));

            mDegreeOfFiveSecondsHeight = typedArray.getDimension(R.styleable.WaveformView_degree_of_five_second_height,
                    DimenUtil.dip2px(getContext(), DEFAULT_DEGREE_OF_FIVE_SECONDS_HEIGHT));

            mRectangleLineHeight = typedArray.getDimension(R.styleable.WaveformView_rectangle_line_height,
                    DimenUtil.dip2px(getContext(), DEFAULT_RECTANGLE_LINE_HEIGHT));

            mRectangleColor = typedArray.getColor(R.styleable.WaveformView_rectangle_line_color, DEFAULT_RECTANGLE_LINE_COLOR);
            mDegreeColor = typedArray.getColor(R.styleable.WaveformView_degree_color, DEFAULT_DEGREE_LINE_COLOR);
            mWaveColor = typedArray.getColor(R.styleable.WaveformView_wave_color, DEFAULT_WAVE_LINE_COLOR);
            mTimeTextColor = typedArray.getColor(R.styleable.WaveformView_time_text_color, DEFAULT_TIME_TEXT_COLOR);

            mTimeTextSize = typedArray.getDimension(R.styleable.WaveformView_time_text_size,
                    DimenUtil.sp2px(getContext(), DEFAULT_TIME_TEXT_SIZE));

            mMarginLeft = DimenUtil.dip2px(getContext(), 20);
            mLeft = mMarginLeft;
            mRectangleHeight = (int) DimenUtil.dip2px(getContext(), 51);

            mLinePaint.setStrokeWidth(mRectangleLineHeight);

            mDegreePaint.setColor(mDegreeColor);

            mWavePaint.setColor(mWaveColor);

            mTimeTextPaint.setColor(mTimeTextColor);
            mTimeTextPaint.setTextSize(mTimeTextSize);

            mIndicatorPaint.setHinting(mRectangleHeight);
            mProgressIndicator = BitmapFactory.decodeResource(getResources(), R.drawable.wave_form_indicator);

            mGestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

                    final float distanceTimeFactor = 0.4f;
                    final float totalDx = (distanceTimeFactor * velocityX / 2);

                    onAnimateMove(totalDx, (long) (1000 * distanceTimeFactor));

                    return super.onFling(e1, e2, velocityX, velocityY);
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    mLeft -= distanceX;
                    adjustViewLeftWhenScroll();
                    if (null != mListener) {
                        mListener.onWaveformScrolling(mCurrentTime);
                    }
                    reDraw();

                    return super.onScroll(e1, e2, distanceX, distanceY);
                }

            });
        } finally {
            typedArray.recycle();
        }


    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Get measured sizes
        mWidth = MeasureSpec.getSize(widthMeasureSpec);
        mHeight = MeasureSpec.getSize(heightMeasureSpec);
        calculateRectangleWidth(mDuration);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mLastDrawTime = System.currentTimeMillis();
        // draw lines
        mLinePaint.setColor(mRectangleColor);
        canvas.drawLine(0, 0, mWidth, 0, mLinePaint);
        canvas.drawLine(0, mRectangleHeight, mWidth, mRectangleHeight, mLinePaint);
        mLinePaint.setColor(DEFAULT_RECTANGLE_CENTER_LINE_COLOR);
        canvas.drawLine(0, mRectangleHeight / 2, mWidth, mRectangleHeight / 2, mLinePaint);

        // draw time & degree
        float left = mLeft;
        int i = 0;
        while (left < mWidth && (mDuration == 0 || i <= mDuration)) {
            final float degreeHeight = mRectangleHeight +
                    (i % 5 == 0 ? mDegreeOfFiveSecondsHeight : mDegreeOfSecondsHeight);
            final float degreeWidth = left + mDegreeOfSecondsWidth;
            canvas.drawRect(left, mRectangleHeight, degreeWidth, degreeHeight, mDegreePaint);
            if (i % 5 == 0) {
                canvas.drawText(generateTime(i), left, degreeHeight + DimenUtil.dip2px(getContext(), 7.5f), mTimeTextPaint);
            }
            left += mInterValOfSeconds;
            i++;
        }

        // draw wave 40 lines per seconds.
        if (mWaveHeights != null && mWaveHeights.length > 0) {
            final int halfHeight = mRectangleHeight / 2;
            for (int j = 0; j < mWaveHeights.length; j++) {
                canvas.drawLine(mLeft + j, halfHeight - calculateWaveHeight(mWaveHeights[j]),
                        mLeft + j, halfHeight + calculateWaveHeight(mWaveHeights[j]), mWavePaint);
            }
        }

        // draw indicator
        canvas.drawBitmap(mProgressIndicator, calculateIndicatorLeft(mWaveHeights/*mCurrentTime*/), 0, mIndicatorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
            default:
                if ((mValueAnimator == null || !mValueAnimator.isRunning()) && mListener != null) {
                    mListener.onWaveformScrolled(mCurrentTime);
                }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mValueAnimator != null && mValueAnimator.isRunning()) {
            mValueAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    public void reset() {
        mLeft = mMarginLeft;
        mWaveHeights = null;
        mCurrentTime = 0;
        reDraw();
    }

    public void reDraw() {
        if (System.currentTimeMillis() - mLastDrawTime > 25) {
            postInvalidate();
        }
    }

    public void onAnimateMove(float x, final long time) {
        if (null == mValueAnimator) {
            mValueAnimator = new ValueAnimator();
            mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mLeft = (float) animation.getAnimatedValue();
                    adjustViewLeftWhenScroll();
                    if (null != mListener) {
                        mListener.onWaveformScrolling(mCurrentTime);
                    }
                    reDraw();
                }
            });
            mValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mValueAnimator.cancel();
                    if (null != mListener) {
                        mListener.onWaveformScrolled(mCurrentTime);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                }
            });
        }
        mValueAnimator.setFloatValues(mLeft, mLeft + x);
        mValueAnimator.setDuration(time);
        mValueAnimator.start();
    }

    public void setWaveformListener(WaveformListener listener) {
        mListener = listener;
    }

    public void setCurrentTime(int time, int[] waveHeights) {
        mWaveHeights = waveHeights;
        mCurrentTime = time * 1000;
        invalidate();
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setWaveColor(int waveColor) {
        mWaveColor = waveColor;
    }

    public void refreshByFrame(int[] waveHeights) {
        mWaveHeights = waveHeights;
        mCurrentTime = waveHeights.length * 1000 / 40;// / (int) mInterValOfSeconds;
        mLeft = calculateLeft(mWaveHeights);//mMarginLeft;
        invalidate();
    }

    private int calculateIndicatorLeft(int currentTime/*second*/) {
        int left = mWidth / 2;
        final int halfWidthTimes = mWidth / 2 - (int) mMarginLeft;
        if (halfWidthTimes / mInterValOfSeconds > currentTime) {
            left = (int) mMarginLeft + (int) mInterValOfSeconds * currentTime;
        }
        return left - (int) DimenUtil.dip2px(getContext(), 1.5f);
    }

    private int calculateIndicatorLeft(int[] waveSet) {
        int left = mWidth / 2;
        int currentFrame = waveSet == null ? 0 : waveSet.length;
        if (currentFrame + mMarginLeft > left) {
            return left;
        } else {
            return currentFrame + (int) mMarginLeft - (int) DimenUtil.dip2px(getContext(), 1.5f);
        }
    }


    private int calculateWaveHeight(int audioHeight) {
        final int height = Math.abs(audioHeight);
        final int halfAreaHeight = mRectangleHeight / 2 - mRectanglePaddingTop;

        return Math.min(Math.max(height, 8), halfAreaHeight);
    }

    private int calculateRectangleWidth(int duration/*seconds*/) {
        final float degreeLength = duration * mInterValOfSeconds + mDegreeOfSecondsWidth;
        return (int) Math.max((mMarginLeft + mMarginRight + degreeLength), mWidth);
    }

    private float calculateLeft(int currentTime) {
        final int halfWidth = mWidth / 2;
        final float waveLength = currentTime * mInterValOfSeconds + mDegreeOfSecondsWidth;
        if (mMarginLeft + waveLength < halfWidth) {
            return mMarginLeft;
        }
        return halfWidth - mMarginLeft - waveLength;
    }

    private float calculateLeft(int[] waveSet) {
        final int halfWidth = mWidth / 2;
        final float waveLength = waveSet == null ? 0 : waveSet.length;
        if (mMarginLeft + waveLength < halfWidth) {
            return mMarginLeft;
        }
        return halfWidth - waveLength + (int) DimenUtil.dip2px(getContext(), 1.5f);
    }

    private void adjustViewLeftWhenScroll() {
        final float indicatorPos = calculateIndicatorLeft(mWaveHeights) + mProgressIndicator.getWidth() / 2;
        if (mLeft > indicatorPos) {
            mLeft = indicatorPos;
        }
        if (mLeft < calculateLeft(mWaveHeights)) {
            mLeft = calculateLeft(mWaveHeights);
        }
        mCurrentTime = (long) ((indicatorPos - mLeft) * 1000 / 40);
    }

    public long getCurrentTime() {
        return mCurrentTime;
    }

    public long getTotalFrames() {
        return (long) (mDuration * mInterValOfSeconds);
    }

    public String generateTime(int seconds) {
        int i = seconds % 60;
        int j = seconds / 60 % 60;
        seconds /= 3600;
        if (seconds > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", seconds, j, i);
        }
        return String.format(Locale.US, "%02d:%02d", j, i);
    }

    public interface WaveformListener {

        void onWaveformScrolled(long seek);

        void onWaveformScrolling(long seek);
    }


}
