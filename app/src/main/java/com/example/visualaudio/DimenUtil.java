package com.example.visualaudio;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Created by dsq on 2017/4/24.
 */

public class DimenUtil {
    public static float dip2px(Context c, float dp) {
        return TypedValue.applyDimension(1, dp, c.getResources().getDisplayMetrics());
    }

    public static int getScreenWidth(Context c) {
        DisplayMetrics localDisplayMetrics = new DisplayMetrics();
        ((Activity) c).getWindow().getWindowManager().getDefaultDisplay().getMetrics(localDisplayMetrics);
        return localDisplayMetrics.widthPixels;
    }

    public static float px2dip(Context c, float paramFloat) {
        return TypedValue.applyDimension(0, paramFloat, c.getResources().getDisplayMetrics());
    }

    public static float sp2px(Context c, float paramFloat) {
        return TypedValue.applyDimension(2, paramFloat, c.getResources().getDisplayMetrics());
    }
}
