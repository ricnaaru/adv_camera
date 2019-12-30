package com.ric.adv_camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;

import androidx.annotation.NonNull;

public class CameraFocusOnTouchHandler implements View.OnTouchListener {

    private static final String TAG = "FocusOnTouchHandler";

    private CameraCharacteristics mCameraCharacteristics;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private Handler mBackgroundHandler;
    private int iOrientation = 0;
    private int mPhotoAngle = 90;
    private Context context;

    private boolean mManualFocusEngaged = false;

    public CameraFocusOnTouchHandler(
            Context context,
            CameraCharacteristics cameraCharacteristics,
            CaptureRequest.Builder previewRequestBuilder,
            CameraCaptureSession captureSession,
            Handler backgroundHandler
    ) {
        this.context = context;
        mCameraCharacteristics = cameraCharacteristics;
        mPreviewRequestBuilder = previewRequestBuilder;
        mCaptureSession = captureSession;
        mBackgroundHandler = backgroundHandler;
        identifyOrientationEvents();
    }

    private void identifyOrientationEvents() {
        OrientationEventListener myOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int iAngle) {

                final int iLookup[] = {0, 0, 0, 90, 90, 90, 90, 90, 90, 180, 180, 180, 180, 180, 180, 270, 270, 270, 270, 270, 270, 0, 0, 0}; // 15-degree increments
                if (iAngle != ORIENTATION_UNKNOWN) {

                    int iNewOrientation = iLookup[iAngle / 15];
                    if (iOrientation != iNewOrientation) {
                        iOrientation = iNewOrientation;

//                        Log.d(TAG, "[" + textureView.getRotation() + "] mPhotoAngle => " + mPhotoAngle + ", " + normalize(iAngle) + ", " + textureView.getWidth() + ", " + textureView.getHeight());

                        int newAngle = normalize(iAngle);
                        int str = Settings.System.getInt(context.getContentResolver(),
                                Settings.System.ACCELEROMETER_ROTATION, 0);
//
//                        if (str == 1) {
//                            if (newAngle != 180)
////                                setr(newAngle);
//                                textureView.setRotation(newAngle);
////                            createCameraPreview();
////                            configureR();
//                        }
                    }
                    mPhotoAngle = normalize(iAngle);
                }
            }
        };

        if (myOrientationEventListener.canDetectOrientation()) {
            myOrientationEventListener.enable();
        }
    }

    private int normalize(int degrees) {
        if (degrees > 315 || degrees <= 45) {
            return 0;
        }

        if (degrees <= 135) {
            return 90;
        }

        if (degrees <= 225) {
            return 180;
        }

        return 270;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int allowControl = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);

        if (allowControl == 0) return false;
        final Rect sensorArraySize2 = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        int pointerId = motionEvent.getPointerId(0);
        int pointerIndex = motionEvent.findPointerIndex(pointerId);
        int sensorWidth2 = sensorArraySize2.width();
        int sensorHeight2 = sensorArraySize2.height();

        final float viewWidth2 = (float) view.getWidth();
        final float viewHeight2 = (float) view.getHeight();
        final int y2 = (int) ((motionEvent.getX() / viewWidth2) * (float) sensorHeight2);
        final int x2 = (int) ((motionEvent.getY() / viewHeight2) * (float) sensorWidth2);
//        float screenX = motionEvent.getX();
//        float screenY = motionEvent.getY();
//        float viewX = screenX - view.getLeft();
//        float viewY = screenY - view.getTop();

        Rect rectf = new Rect();

//For coordinates location relative to the parent
//        anyView.getLocalVisibleRect(rectf);

//For coordinates location relative to the screen/display
        view.getGlobalVisibleRect(rectf);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        float screenX = motionEvent.getRawX();
        float screenY = motionEvent.getRawY();

        float viewX = screenX - rectf.left;
        float viewY = screenY - rectf.top;
//        viewX = motionEvent.getX(pointerIndex);
//        viewY = motionEvent.getY(pointerIndex);
        Log.d(TAG, "screen: " + rectf.left + " x " + rectf.top);
        Log.d(TAG, "sensor: " + sensorWidth2 + " x " + sensorHeight2);
        Log.d(TAG, "motionEvent: " + viewX + " x " + viewY + ", " + mPhotoAngle);
        Log.d(TAG, "view ize: " + viewWidth2 + " x " + viewHeight2);
        Log.d(TAG, "percentage: " + ( viewX / viewWidth2 * 100) + " x " +  (viewY / viewHeight2 * 100));
//
//
//        /// kiri y = viewWidth - x; x = y
//
////        6xx12xx1920=>x
////
////            1x10x1080=>y
//
//        float tempX = y;
//        float tempY = viewWidth - x;
//
//        if (mPhotoAngle == 270) {
//
//        }
//
//
////        Log.d(TAG, "Real: " + tempX + " x " + tempY);
//
////        mCameraCharacteristics.get(CaptureRequest.DISTORTION_CORRECTION_MODE);
//
////        //Override in your touch-enabled view (this can be different than the view you use for displaying the cam preview)
//
        final int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (mManualFocusEngaged) {
            Log.d(TAG, "Manual focus already engaged");
            return true;
        }

        final Rect sensorArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        //TODO: here I just flip x,y, but this needs to correspond with the sensor orientation (via SENSOR_ORIENTATION)
        final float viewWidth = (float) view.getWidth();
        final float viewHeight = (float) view.getHeight();

        // 0
//        final int y = (int) ((motionEvent.getX() / viewWidth) * (float) sensorArraySize.height());
//        final int x = (int) ((motionEvent.getY() / viewHeight) * (float) sensorArraySize.width());

        final int x = (int) ((motionEvent.getX() / viewWidth) * (float) sensorArraySize.width());
        final int y = (int) ((motionEvent.getY() / viewHeight) * (float) sensorArraySize.height());
        final int halfTouchWidth = 50; //(int)motionEvent.getTouchMajor(); //TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
        final int halfTouchHeight = 50; //(int)motionEvent.getTouchMinor();
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);

        CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                mManualFocusEngaged = false;

                if (request.getTag() == "FOCUS_TAG") {
                    //the focus trigger is complete - resume repeating (preview surface will get frames), clear AF trigger
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "Manual AF failure: " + failure);
                mManualFocusEngaged = false;
            }
        };

        //first stop the existing repeating request
        try {
            mCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        //cancel any existing AF trigger (repeated touches, etc.)
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        //Now add a new AF trigger with focus region
        if (isMeteringAreaAFSupported()) {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        mPreviewRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

        //then we ask for a single request (not repeating!)
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallbackHandler, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mManualFocusEngaged = true;

        return true;
    }

    private boolean isMeteringAreaAFSupported() {
        Integer value = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        if (value != null) {
            return value >= 1;
        } else {
            return false;
        }
    }

}