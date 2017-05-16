package com.example.visualaudio;

import ca.uol.aig.fftpack.RealDoubleFFT;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.example.visualaudio.soundfile.CheapSoundFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements WaveformView.WaveformListener, AudioRecordHelper.OnAudioRecordListener {
    private static final int DEFAULT_DURATION = 48;
    private int mRecordBufferSize;
    int frequency = 44100;
    int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    //    RealDoubleFFT fftTrans;
    int blockSize = 300;
    Button startStopBtn;
    boolean started = false;
    RecordAudioTask recordAudioTask;
    ImageView imgView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;
    AudioRecordHelper helper;
    private long mPlayTime;
    private long mLastTime;

    WaveformView mWaveformView;
    int waveHeight;
    int[] mWaveHeights = new int[DEFAULT_DURATION * 40]; // one minutes
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                int index = msg.arg1;
                if (index > mWaveformView.getTotalFrames()) {
                    return;
                }
                if (index > mWaveHeights.length - 1) {
                    mWaveHeights = Arrays.copyOf(mWaveHeights, mWaveHeights.length * 2);
                }
                mWaveHeights[index] = waveHeight;

                mWaveformView.refreshByFrame(Arrays.copyOfRange(mWaveHeights, 0, index));
                Message message = obtainMessage(0, index + 1, 0);
                sendMessageDelayed(message, 16);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        helper = new AudioRecordHelper(new File(getExternalFilesDir(null), System.currentTimeMillis() + ".wav"));

        helper.setOnAudioRecordListener(this);
        startStopBtn = (Button) findViewById(R.id.startStopBtn);


//        fftTrans = new RealDoubleFFT(blockSize);
        mWaveformView = (WaveformView) findViewById(R.id.waveform_view);
        mWaveformView.setDuration(DEFAULT_DURATION);
        mWaveformView.setWaveformListener(this);

        imgView = (ImageView) findViewById(R.id.imgView);
        bitmap = Bitmap.createBitmap(800, 200, Bitmap.Config.ARGB_8888);

        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        imgView.setImageBitmap(bitmap);

        startStopBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (started) {
                    started = false;
                    startStopBtn.setText("Start");
//                    recordAudioTask.cancel(true);
//                    mHandler.removeMessages(0);
                    helper.stop();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
                    } else {
                        started = true;
                        startStopBtn.setText("Stop");
//                        recordAudioTask = new RecordAudioTask();
//                        recordAudioTask.execute();
                        record();
                    }
                }
            }
        });
    }


    private void record() {
        helper.start(mWaveformView.getCurrentTimeByIndicator());
        mWaveIndex = mWaveformView.getLeftWaveLengthByIndicator();//int) (mPlayTime / mWaveformView.getPeriodPerFrame());
    }

    private void playAudio() {
        mPlayTime = mWaveformView.getCurrentTimeByIndicator();
        if (mPlayTime >= mWaveformView.getCurrentTotalTime()){
            mPlayTime = 0;
        }
        helper.play(mPlayTime);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            started = true;
            recordAudioTask = new RecordAudioTask();
            startStopBtn.setText("Stop");
            recordAudioTask.execute();
        }
    }

    @Override
    public void onWaveformScrolled(long seek) {
        mPlayTime = seek;
        mWaveIndex =(int) (seek / mWaveformView.getPeriodPerFrame());
    }

    @Override
    public void onWaveformScrolling(long seek) {

    }

    @Override
    public void onWaveformOffset(long time) {
        mLastTime = time;
    }

    public void resetWaveView(View v) {
//        mWaveformView.reset();
        playAudio();
    }

    int mWaveIndex = 0;

    @Override
    public void onWaveSize(int size) {
        if (mWaveIndex > mWaveHeights.length - 1) {
            mWaveHeights = Arrays.copyOf(mWaveHeights, mWaveHeights.length * 2);
        }
        mWaveHeights[mWaveIndex] = size;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWaveformView.refreshByFrame(Arrays.copyOfRange(mWaveHeights, 0, mWaveIndex));
            }
        });
        mWaveIndex++;
    }

    @Override
    public void onUpdateWaveFramePos() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWaveformView.refreshByPos(mPlayTime);
            }
        });

        mPlayTime += mWaveformView.getPeriodPerFrame();
    }

    @Override
    public void onMediaPlayerStart() {

    }

    @Override
    public void onMediaPlayerStop() {
        mPlayTime = mWaveformView.getCurrentTime();
    }

    @Override
    public void onMediaPlayerComplete() {
        mWaveformView.refreshToEndPos();
        mPlayTime = mWaveformView.getCurrentTotalTime();
    }

    private class RecordAudioTask extends AsyncTask<Void, double[], Void> {

        @Override
        protected Void doInBackground(Void... params) {
            try {
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfig, audioFormat);
                Log.v("bufSize", String.valueOf(bufferSize));
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfig, audioFormat, bufferSize);

                blockSize = bufferSize / 2;
                short[] audioBuffer = new short[blockSize];
                double[] toTrans = new double[blockSize];
                RealDoubleFFT fftTrans = new RealDoubleFFT(blockSize);

                audioRecord.startRecording();

                mHandler.sendMessage(mHandler.obtainMessage(0, 0, 0, 0));

                while (started) {
                    int result = audioRecord.read(audioBuffer, 0, blockSize);

                    for (int i = 0; i < blockSize && i < result; i++) {
                        toTrans[i] = (double) audioBuffer[i] / Short.MAX_VALUE;
                    }
                    fftTrans.ft(toTrans);
                    publishProgress(toTrans);
                }
                audioRecord.stop();
            } catch (Throwable t) {
                t.printStackTrace();
                Log.e("AudioRecord", "Recording failed");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(double[]... values) {
            long cur = System.currentTimeMillis();
            canvas.drawColor(Color.BLACK);
            float j = 0;
            for (int i = 0; i < values[0].length; i++) {
                int x = i;
                int downy = (int) (100 - (values[0][i] * 10));
                int upy = 100;
                if (i < blockSize / 6) {
                    j += Math.abs(values[0][i] * 10);
                }
                canvas.drawLine(x, downy, x, upy, paint);
            }
            waveHeight = (int) (j / (blockSize / 6));
            imgView.invalidate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
