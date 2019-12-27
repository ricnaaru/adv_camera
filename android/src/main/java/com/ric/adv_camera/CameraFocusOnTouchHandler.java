package com.ric.adv_camera;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

public class CameraFocusOnTouchHandler implements View.OnTouchListener {

    private static final String TAG = "FocusOnTouchHandler";

    private CameraCharacteristics mCameraCharacteristics;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private Handler mBackgroundHandler;

    private boolean mManualFocusEngaged = false;

    public CameraFocusOnTouchHandler(
            CameraCharacteristics cameraCharacteristics,
            CaptureRequest.Builder previewRequestBuilder,
            CameraCaptureSession captureSession,
            Handler backgroundHandler
    ) {
        mCameraCharacteristics = cameraCharacteristics;
        mPreviewRequestBuilder = previewRequestBuilder;
        mCaptureSession = captureSession;
        mBackgroundHandler = backgroundHandler;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        //Override in your touch-enabled view (this can be different than the view you use for displaying the cam preview)

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
        final int y = (int) ((motionEvent.getX() / (float) view.getWidth()) * (float) sensorArraySize.height());
        final int x = (int) ((motionEvent.getY() / (float) view.getHeight()) * (float) sensorArraySize.width());
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