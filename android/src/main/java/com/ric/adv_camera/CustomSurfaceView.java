package com.ric.adv_camera;

import android.content.Context;

import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;

public class CustomSurfaceView extends SurfaceView {
    public CustomSurfaceView(Context context) {
        super(context);
    }

    public CustomSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.d("tag", "onDetachedFromWindow");
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {//onResume called
            Log.d("tag", "onResume() called");
        } else {
            Log.d("tag", "onPause() called");
        }// onPause() called
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            Log.d("tag", "onresume() called");
        } else {
            Log.d("tag", "onPause() called");
        }// onPause() called
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // onCreate() called
        Log.d("tag", "onAttachedToWindow");
    }

    //    public CustomSurfaceView(Context context) {
//        super(context);
//    }
//
//    public CustomSurfaceView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//    }
//
//    public CustomSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
//        super(context, attrs, defStyleAttr);
//    }
//
//    @Override
//    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        super.onLayout(changed, left, top, right, bottom);
//        Log.d("ricric", "CustomSurfaceView onLayout");
////        try {
////            if (cameraFacing == 0) {
////                camera = Camera.open(0);
////            } else {
////                camera = Camera.open(1);
////            }
////        } catch (RuntimeException e) {
////            e.printStackTrace();
////            return;
////        }
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//        Log.d("ricric", "CustomSurfaceView onDraw");
//    }
//

}
