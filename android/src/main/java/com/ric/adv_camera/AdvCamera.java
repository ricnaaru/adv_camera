package com.ric.adv_camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

public class AdvCamera implements MethodChannel.MethodCallHandler,
        PlatformView, PluginRegistry.RequestPermissionsResultListener {
    private static final String TAG = "ricric";
    private final MethodChannel methodChannel;
    private final Context context;
    private final FragmentActivity activity;
    private boolean disposed = false;
    private boolean bestPictureSize = true;
    private int maxSize;
    private View view;
    private String flashType;
    private File folder;
    private int cameraFacing = 0;
    private String savePath;
    private String fileNamePrefix = "adv_camera";
    private int mPhotoAngle = 90;
    private String previewRatio;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private int REQUEST_CODE_PERMISSIONS = 101;
    private TextureView textureView;
    private String cameraId;
    private Size imageDimension;
    private CameraDevice cameraDevice;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession cameraCaptureSessions;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private Size[] previewSizes;
    private static final String CAMERA_FRONT = "1";
    private static final String CAMERA_BACK = "0";

    /**
     * The current state of camera state for taking pictures.
     *
//     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//            if (cameraDevice == null) {
            openCamera();
//            } else {
//                createCameraPreview();
//            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged => " + width + ", " + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    AdvCamera(
            int id,
            final Context context,
            PluginRegistry.Registrar registrar, Object args) {
        this.context = context;
        this.activity = (FragmentActivity) registrar.activity();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (args instanceof HashMap) {
            Map<String, Object> params = (Map<String, Object>) args;
            Object initialCamera = params.get("initialCamera");
            Object flashType = params.get("flashType");
            Object savePath = params.get("savePath");
            Object previewRatio = params.get("previewRatio");
            Object fileNamePrefix = params.get("fileNamePrefix");
            Object maxSize = params.get("maxSize");
            Object bestPictureSize = params.get("bestPictureSize");

            if (initialCamera != null) {
                if (initialCamera.equals("front")) {
                    cameraFacing = 1;
                } else if (initialCamera.equals("rear")) {
                    cameraFacing = 0;
                }
            }

            if (flashType != null) {
                this.flashType = flashType.toString();
            }

            if (savePath != null) {
                this.savePath = savePath.toString();
            } else {
                this.savePath = Environment.getExternalStorageDirectory() + "/whatsappCamera";
            }

            if (previewRatio != null) {
                this.previewRatio = previewRatio.toString();
            } else {
                this.previewRatio = "16:9";
            }

            if (fileNamePrefix != null) {
                this.fileNamePrefix = fileNamePrefix.toString();
            }

            if (maxSize != null) {
                this.maxSize = (Integer) maxSize;
            }

            if (bestPictureSize != null) {
                this.bestPictureSize = Boolean.valueOf(bestPictureSize.toString());
            }
        }


        folder = new File(this.savePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        methodChannel =
                new MethodChannel(registrar.messenger(), "plugins.flutter.io/adv_camera/" + id);
        methodChannel.setMethodCallHandler(this);
        view = registrar.activity().getLayoutInflater().inflate(R.layout.activity_camera, null);
        CameraFragment cameraFragment = (CameraFragment) activity.getSupportFragmentManager().findFragmentById(R.id.cameraFragment);


        assert cameraFragment != null;
        cameraFragment.listener = new FragmentLifecycleListener() {
            @Override
            public void onPause() {
                AdvCamera.this.onPause();
            }

            @Override
            public void onResume() {
                AdvCamera.this.onResume();
            }
        };

        textureView = view.findViewById(R.id.imgSurface);
        textureView.setSurfaceTextureListener(textureListener);

        registrar.addRequestPermissionsResultListener(this);
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        identifyOrientationEvents();
    }

    private void identifyOrientationEvents() {
        OrientationEventListener myOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int iAngle) {
                if (iAngle != ORIENTATION_UNKNOWN) {
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

    private void configureFocus() {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            cameraId = manager.getCameraIdList()[cameraFacing];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            textureView.setOnTouchListener(new CameraFocusOnTouchHandler(context, characteristics, captureRequestBuilder, cameraCaptureSessions, mBackgroundHandler));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            // This method is called when the camera is opened. We start camera preview here.
            mCameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

            mCameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
        }
    };

    protected void onResume() {
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            captureRequestBuilder
                    = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            Log.d(TAG, "lalala");

                            // When the session is ready, we start displaying the preview.
                            AdvCamera.this.cameraCaptureSessions = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                                // Flash is automatically enabled when necessary.
//                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            configureFocus();
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(activity, "Configuration change", Toast.LENGTH_SHORT).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    /**
//     * Initiate a still image capture.
//     */
//    private void stakePicture() {
//        lockFocus();
//    }

//    /**
//     * Lock the focus as the first step for a still image capture.
//     */
//    private void lockFocus() {
//        try {
//            // This is how to tell the camera to lock focus.
////            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
////                    CameraMetadata.CONTROL_AF_TRIGGER_START);
//            // Tell #mCaptureCallback to wait for the lock.
//            mState = STATE_WAITING_LOCK;
//            cameraCaptureSessions.capture(captureRequestBuilder.build(), mCaptureCallback,
//                    mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }

    protected void onPause() {
        stopBackgroundThread();
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[cameraFacing];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            previewSizes = map.getOutputSizes(SurfaceTexture.class);
            for (Size size : previewSizes) {
                if (asFraction(size.getWidth(), size.getHeight()).equals(previewRatio)) {
                    imageDimension = size;
                    break;
                }
            }

            Log.d(TAG, "previewsize => " + previewSizes);

            if (imageDimension == null) {
                imageDimension = previewSizes[0];
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (mBackgroundThread == null) return;

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();

            assert texture != null;
            Log.e(TAG, "imageDimension: " + imageDimension.getWidth() + ", " + imageDimension.getHeight());

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            MeteringRectangle[] focusArea = null;
            Rect cropRegion = null;

            if (captureRequestBuilder != null) {
                focusArea = captureRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
                cropRegion = captureRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            }

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            if (focusArea != null && cropRegion != null) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, focusArea);
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
            }

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }

                    cameraCaptureSessions = cameraCaptureSession;
                    configureFocus();
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(activity, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMethodCall(MethodCall methodCall, final MethodChannel.Result result) {
        if (methodCall.method.equals("waitForCamera")) {
            result.success(null);
        } else if (methodCall.method.equals("setPreviewRatio")) {
            String previewRatio = "";

            if (cameraFacing == 1) return;

            if (methodCall.arguments instanceof HashMap) {
                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                previewRatio = params.get("previewRatio") == null ? null : params.get("previewRatio").toString();
            }

            for (Size size : previewSizes) {
                if (asFraction(size.getWidth(), size.getHeight()).equals(previewRatio)) {
                    imageDimension = size;
                    break;
                }
            }

            if (imageDimension == null) {
                result.success(false);
                return;
            }

            cameraDevice.close();
            cameraDevice = null;
            cameraId = null;

            result.success(true);
        } else if (methodCall.method.equals("captureImage")) {
            try {
                // This is how to tell the camera to lock focus.
//                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
//                        CameraMetadata.CONTROL_AF_TRIGGER_START);
                // Tell #mCaptureCallback to wait for the lock.
                Log.d(TAG, "before trigger 1! " + flashType);
                if (flashType.equals("off")) {
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                } else if (flashType.equals("on")) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                    } else if (flashType.equals("torch")) {
//                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                } else if (flashType.equals("auto")) {
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_/OFF);
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
                }
//                        session.capture(captureBuilder.build(), captureListener, null);
                boolean aeState = captureRequestBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
                Log.d(TAG, "before trigger! => " + aeState);
                // This is how to tell the camera to trigger.
//                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
//                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
                Log.d(TAG, "after trigger!");
                mState = STATE_WAITING_LOCK;
                final CameraCaptureSession[] ccs = new CameraCaptureSession[1];
                SurfaceTexture texture = textureView.getSurfaceTexture();

                // This is the output Surface we need to start preview.
                Surface surface = new Surface(texture);
                // Here, we create a CameraCaptureSession for camera preview.
                cameraDevice.createCaptureSession(Arrays.asList(surface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                // The camera is already closed
                                if (null == cameraDevice) {
                                    return;
                                }

                                Log.d(TAG, "lalala");

                                // When the session is ready, we start displaying the preview.
                                ccs[0] = cameraCaptureSession;

                                try {
                                    ccs[0].setRepeatingRequest(captureRequestBuilder.build(),
                                            new CameraCaptureSession.CaptureCallback() {
                                                @Override
                                                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                                    super.onCaptureProgressed(session, request, partialResult);
                                                    Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
    //                                Integer aeState = partialResult.get(CaptureResult.FLASH_MODE);
                                                    Log.d(TAG, "captureImage onCaptureProgressed CONTROL_AE_STATE aeState =? " + aeState);
                                                    Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + partialResult.get(CaptureResult.FLASH_MODE));
                                                    Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + partialResult.get(CaptureResult.CONTROL_AE_MODE));
                                                }

                                                @Override
                                                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                                    super.onCaptureCompleted(session, request, result);
                                                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                                    Log.d(TAG, "captureImage onCaptureCompleted CONTROL_AE_STATE aeState =? " + aeState);
                                                    Log.d(TAG, "captureImage onCaptureCompleted FLASH_MODE aeState =? " + result.get(CaptureResult.FLASH_MODE));
                                                    Log.d(TAG, "captureImage onCaptureCompleted FLASH_MODE aeState =? " + result.get(CaptureResult.CONTROL_AE_MODE));
    //                                try {
    //                                    cameraCaptureSessions.stopRepeating();
    //                                    cameraCaptureSessions.abortCaptures();
    //                                } catch (CameraAccessException e) {
    //                                    e.printStackTrace();
    //                                }
                                                    if (aeState == null ||
                                                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                                        takePicture();
                                                    } else {
                                                        try {
                                                            // This is how to tell the camera to trigger.
                                                            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                                                            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                                                            mState = STATE_WAITING_PRECAPTURE;

                                                            Log.d(TAG, "inner else =? " + session.isReprocessable());
                                                            session.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                                                        @Override
                                                                        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                                                            super.onCaptureProgressed(session, request, partialResult);
                                                                            Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
                                                                            Log.d(TAG, "inner captureImage onCaptureProgressed CONTROL_AE_STATE aeState =? " + aeState);
    //                                                        if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
    //                                                            mState = STATE_PICTURE_TAKEN;
    //                                                            try {
    //                                                                cameraCaptureSessions.stopRepeating();
    //                                                                cameraCaptureSessions.abortCaptures();
    //                                                            } catch (CameraAccessException e) {
    //                                                                e.printStackTrace();
    //                                                            }
    //                                                            takePicture();
    //                                                        }
                                                                        }

                                                                        @Override
                                                                        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                                                            super.onCaptureCompleted(session, request, result);
                                                                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                                                            Log.d(TAG, "inner captureImage onCaptureCompleted CONTROL_AE_STATE aeState =? " + aeState);
                                                                            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                                                                mState = STATE_PICTURE_TAKEN;
    //                                                            try {
    //                                                                cameraCaptureSessions.stopRepeating();
    //                                                                cameraCaptureSessions.abortCaptures();
    //                                                            } catch (CameraAccessException e) {
    //                                                                e.printStackTrace();
    //                                                            }
                                                                                takePicture();
                                                                            }
                                                                        }
                                                                    },
                                                                    mBackgroundHandler);
                                                        } catch (CameraAccessException e) {
                                                            e.printStackTrace();
                                                        }
                                                    }
                                                }
                                            }, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(
                                    @NonNull CameraCaptureSession cameraCaptureSession) {
                                Toast.makeText(activity, "Configuration change", Toast.LENGTH_SHORT).show();
                            }
                        }, null
                );
//                cameraCaptureSessions.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
//                            @Override
//                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
//                                super.onCaptureProgressed(session, request, partialResult);
//                                Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
////                                Integer aeState = partialResult.get(CaptureResult.FLASH_MODE);
//                                Log.d(TAG, "captureImage onCaptureProgressed CONTROL_AE_STATE aeState =? " + aeState);
//                                Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + partialResult.get(CaptureResult.FLASH_MODE));
//                                Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + partialResult.get(CaptureResult.CONTROL_AE_MODE));
//                            }
//
//                            @Override
//                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                                super.onCaptureCompleted(session, request, result);
//                                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                                Log.d(TAG, "captureImage onCaptureProgressed CONTROL_AE_STATE aeState =? " + aeState);
//                                Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + result.get(CaptureResult.FLASH_MODE));
//                                Log.d(TAG, "captureImage onCaptureProgressed FLASH_MODE aeState =? " + result.get(CaptureResult.CONTROL_AE_MODE));
//                                takePicture();
//                            }
//                        },
//                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            result.success(true);
        } else if (methodCall.method.equals("switchCamera")) {
            if (cameraId.equals(CAMERA_FRONT)) {
                cameraFacing = 0;

                cameraDevice.close();
                cameraDevice = null;
                cameraId = null;
                captureRequestBuilder = null;

                openCamera();
//                reopenCamera();
//                switchCameraButton.setImageResource(R.drawable.ic_camera_front);

            } else if (cameraId.equals(CAMERA_BACK)) {
                cameraFacing = 1;

                cameraDevice.close();
                cameraDevice = null;
                cameraId = null;
                captureRequestBuilder = null;
//                Matrix matrix = new Matrix();
//                matrix.setScale(-1, 1);
////                matrix.postTranslate(width, 0);
//                textureView.setTransform(matrix);

                openCamera();
            }
//            if (cameraFacing == 0) {
//                cameraFacing = 1;
//            } else {
//                cameraFacing = 0;
//            }
//
//            camera.stopPreview();
//            camera.release();
//            setupCamera();
        } else if (methodCall.method.equals("getPictureSizes")) {
//            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//            CameraCharacteristics characteristics = null;
//            try {
//                characteristics = manager.getCameraCharacteristics(cameraId);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            assert map != null;
//
//            List<String> pictureSizes = new ArrayList<>();
////
////            Camera.Parameters param = camera.getParameters();
////
////            List<Camera.Size> sizes = param.getSupportedPictureSizes();
//            for (Size size : map.getOutputSizes(SurfaceTexture.class)) {
//                pictureSizes.add(size.getWidth()     + ":" + size.getHeight());
//            }
////
//            result.success(pictureSizes);
        } else if (methodCall.method.equals("setPictureSize")) {
//            int pictureWidth = 0;
//            int pictureHeight = 0;
//            String error = "";
//
//            if (methodCall.arguments instanceof HashMap) {
//                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                pictureWidth = (int) params.get("pictureWidth");
//                pictureHeight = (int) params.get("pictureHeight");
//            }
//
//            Camera.Parameters param = camera.getParameters();
//
//            param.setPictureSize(pictureWidth, pictureHeight);
//
//            camera.stopPreview();
//
//            try {
//                camera.setParameters(param);
//                camera.setPreviewDisplay(surfaceHolder);
//                this.pictureSize = camera.new Size(pictureWidth, pictureHeight);
//            } catch (IOException e) {
//                error = e.getMessage();
//            } catch (RuntimeException e) {
//                error = e.getMessage();
//            }
//
//            camera.startPreview();
//
//            if (error.isEmpty()) {
//                result.success(true);
//            } else {
//                result.error("Camera Error", "setPictureSize", error);
//            }
        } else if (methodCall.method.equals("setSavePath")) {
            if (methodCall.arguments instanceof HashMap) {
                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                this.savePath = params.get("savePath") == null ? null : params.get("savePath").toString();
            }

            folder = new File(this.savePath);

            if (!folder.exists()) {
                folder.mkdirs();
            }

            result.success(true);
        } else if (methodCall.method.equals("setFlashType")) {
            String flashType = "auto";

            if (methodCall.arguments instanceof HashMap) {
                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                flashType = params.get("flashType") == null ? "auto" : params.get("flashType").toString();
            }
//            try {
            if (isFlashAvailable()) {
//                    FlashState.AUTO -> {
//                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
//                    }
//                    FlashState.ON -> {
//                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
//                        previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
//                    }
//                    FlashState.OFF -> {
//                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
//                        previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
//                    }
                Log.d(TAG, "flashType => " + flashType);
//                    if (flashType.equals("off")) {
//                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
//                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//                    } else if (flashType.equals("on")) {
//                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                    } else if (flashType.equals("torch")) {
////                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                    } else if (flashType.equals("auto")) {
//                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
//                        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                    }
            }
//                cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
//                    @Override
//                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                        super.onCaptureCompleted(session, request, result);
//                        Log.d(TAG, "onCaptureCompleted");
//                    }
//
//                    @Override
//                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
//                        super.onCaptureFailed(session, request, failure);
//                        Log.d(TAG, "onCaptureFailed");
//                    }
//                }, null);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//
//            Camera.Parameters param = camera.getParameters();
//
//            if (this.flashType.equals("torch") && flashType.equals("on")) {
//                param.setFlashMode("off");
//                camera.setParameters(param);
//            }
//
//            if (flashType.equals("torch")) {
//                List<String> supportedFlashModes = param.getSupportedFlashModes();
//
//                if (supportedFlashModes != null) {
//                    if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
//                        this.flashType = Camera.Parameters.FLASH_MODE_TORCH;
//                    } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
//                        this.flashType = Camera.Parameters.FLASH_MODE_ON;
//                    }
//                }
//            } else {
//                this.flashType = flashType;
//            }
//
//            param.setFlashMode(this.flashType);
//
//            camera.stopPreview();
//            camera.setParameters(param);
//            try {
//                camera.setPreviewDisplay(surfaceHolder);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            camera.startPreview();
//
            result.success(true);
        }
    }

    private boolean isFlashAvailable() {
        final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return available == null ? false : available;
    }

    private static long gcm(long a, long b) {
        return b == 0 ? a : gcm(b, a % b); // Not bad for one line of code :)
    }

    private static String asFraction(long a, long b) {
        long gcm = gcm(a, b);
        return (a / gcm) + ":" + (b / gcm);
    }

//    private void runPrecapture() {
//        try {
//            // This is how to tell the camera to trigger.
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
//            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
//            mState = STATE_WAITING_PRECAPTURE;
//            cameraCaptureSessions.capture(captureRequestBuilder.build(), mCaptureCallback,
//                    mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }

//    private CameraCaptureSession.CaptureCallback mCaptureCallback
//            = new CameraCaptureSession.CaptureCallback() {
//
//        private void process(CaptureResult result) {
//            switch (mState) {
//                case STATE_PREVIEW: {
//                    // We have nothing to do when the camera preview is working normally.
//                    break;
//                }
//                case STATE_WAITING_LOCK: {
////                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
////                    Log.d(TAG, "afState =? " + afState);
////                    if (afState == null) {
////                        takePicture();
////                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
////                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
////                        // CONTROL_AE_STATE can be null on some devices
////                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
////                        if (aeState == null ||
////                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
////                            mState = STATE_PICTURE_TAKEN;
////                            takePicture();
////                        } else {
////                    runPrecapture();
////                        }
////                    }
//                    break;
//                }
//                case STATE_WAITING_PRECAPTURE: {
//                    // CONTROL_AE_STATE can be null on some devices
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                    Log.d(TAG, "STATE_WAITING_PRECAPTURE aeState =? " + aeState);
//                    if (aeState == null ||
//                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
//                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
//                        mState = STATE_WAITING_NON_PRECAPTURE;
//                        mState = STATE_PICTURE_TAKEN;
//                        takePicture();
//                    }
//                    break;
//                }
//                case STATE_WAITING_NON_PRECAPTURE: {
//                    // CONTROL_AE_STATE can be null on some devices
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                    Log.d(TAG, "STATE_WAITING_NON_PRECAPTURE aeState =? " + aeState);
//                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
//                        mState = STATE_PICTURE_TAKEN;
//                        takePicture();
//                    }
//                    break;
//                }
//            }
//        }
//
//        @Override
//        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
//                                        @NonNull CaptureRequest request,
//                                        @NonNull CaptureResult partialResult) {
//            process(partialResult);
//        }
//
//        @Override
//        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
//                                       @NonNull CaptureRequest request,
//                                       @NonNull TotalCaptureResult result) {
//            process(result);
//        }
//
//    };
//
//    /**
//     * Unlock the focus. This method should be called when still image capture sequence is
//     * finished.
//     */
//    private void unlockFocus() {
//        try {
////            // Reset the auto-focus trigger
////            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
////                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
//////            setAutoFlash(mPreviewRequestBuilder);
////            cameraCaptureSessions.capture(captureRequestBuilder.build(), mCaptureCallback,
////                    mBackgroundHandler);
//            // After this, the camera will go back to the normal state of preview.
//            mState = STATE_PREVIEW;
//            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallback,
//                    mBackgroundHandler);
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }

    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;

            if (jpegSizes != null && 0 < jpegSizes.length) {

                for (Size s : jpegSizes) {
                    Log.e(TAG, "s => " + s.getHeight() + " - " + s.getWidth());
                }

                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            Date currentTime = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            MeteringRectangle[] focusArea = captureRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS);
            Rect cropRegion = captureRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            Integer aeMode = captureRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE);
            Integer flashMode = captureRequestBuilder.get(CaptureRequest.FLASH_MODE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, focusArea);
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
//            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
//            captureBuilder.set(CaptureRequest.FLASH_MODE, flashMode);

            if (cameraFacing == 0) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mPhotoAngle + 90);
            }

            final File file = new File(folder.getAbsolutePath(), fileNamePrefix + "_" + dateFormat.format(currentTime) + ".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);

                        if (cameraFacing == 0) {
                            save(bytes);
                        } else {
                            Integer x = manager.getCameraCharacteristics(cameraId).get(
                                    CameraCharacteristics.SENSOR_ORIENTATION);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            Matrix matrix = new Matrix();
                            if (x != null)
                                matrix.postRotate(x - mPhotoAngle);
                            matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);

                            Bitmap bb = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            save(bb);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }

                private void save(Bitmap bitmap) throws IOException {
                    OutputStream fOut = new FileOutputStream(file);

//                    Bitmap pictureBitmap = getImageBitmap(myurl); // obtaining the Bitmap
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
                    fOut.flush(); // Not really required
                    fOut.close(); // do not forget to close the stream
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Map<String, Object> params = new HashMap<>();
                            params.put("path", file.getAbsolutePath());
                            methodChannel.invokeMethod("onImageCaptured", params);
//                            createCameraPreview();
//                            unlockFocus();
                            cameraCaptureSessions.close();
                            createCameraPreviewSession();
                        }
                    });
                }
//                                    @Override
//                                    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
//                                        super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
//                                        Log.d(TAG, "onCaptureSequenceCompleted");
//                                    }
//
//                                    @Override
//                                    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
//                                        super.onCaptureSequenceAborted(session, sequenceId);
//                                        Log.d(TAG, "onCaptureSequenceAborted");
//                                    }
//
//                                    @Override
//                                    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
//                                        super.onCaptureBufferLost(session, request, target, frameNumber);
//                                        Log.d(TAG, "onCaptureBufferLost => " + request);
//                                    }
//
//                                    @Override
//                                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
//                                        super.onCaptureStarted(session, request, timestamp, frameNumber);
//                                        Log.d(TAG, "onCaptureStarted => " + request);
//                                    }
//
//                                    @Override
//                                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
//                                        super.onCaptureFailed(session, request, failure);
//                                        Log.d(TAG, "onCaptureFailed => " + request);
//                                    }
//
//                                    @Override
//                                    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
//                                        super.onCaptureProgressed(session, request, partialResult);
//                                        Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
//                                        Log.d(TAG, "onCaptureProgressed => " + aeState);
////                                        session.capture(captureBuilder.build(), captureListener, null);
//                                    }

//                                    @Override
//                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                                        super.onCaptureCompleted(session, request, result);
//                                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                                        Log.d(TAG, "onCaptureCompleted => " + aeState);
//                                    }
            };
//            captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
//            try {
//                cameraCaptureSessions.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
//                            @Override
//                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                                super.onCaptureCompleted(session, request, result);
//                            }
//                        },
//                        null);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }


            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured! " + isFlashAvailable());
                    try {
//                        Log.d(TAG, "before trigger 1! " + captureBuilder.get(CaptureRequest.CONTROL_AE_MODE));
//                        if (flashType.equals("off")) {
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
//                            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//                        } else if (flashType.equals("on")) {
//                            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                    } else if (flashType.equals("torch")) {
////                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                        } else if (flashType.equals("auto")) {
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_/OFF);
//                            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                            captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
//                            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
//                        }
////                        session.capture(captureBuilder.build(), captureListener, null);
//                        boolean aeState = captureBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
//                        Log.d(TAG, "before trigger! => " + aeState);
//                        // This is how to tell the camera to trigger.
//                        captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
//                        captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
//                        captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
//                        Log.d(TAG, "after trigger!");
//                        // Tell #mCaptureCallback to wait for the precapture sequence to be set.
//                        mState = STATE_WAITING_PRECAPTURE;

                        // Finally, we start displaying the camera preview.
//                        session.setRepeatingRequest(captureBuilder.build(),
//                                new CameraCaptureSession.CaptureCallback() {
//                                    @Override
//                                    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
//                                        super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
//                                        Log.d(TAG, "1 onCaptureSequenceCompleted");
//                                    }
//
//                                    @Override
//                                    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
//                                        super.onCaptureSequenceAborted(session, sequenceId);
//                                        Log.d(TAG, "1 onCaptureSequenceAborted");
//                                    }
//
//                                    @Override
//                                    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
//                                        super.onCaptureBufferLost(session, request, target, frameNumber);
//                                        Log.d(TAG, "1 onCaptureBufferLost => " + request);
//                                    }
//
//                                    @Override
//                                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
//                                        super.onCaptureStarted(session, request, timestamp, frameNumber);
//                                        Log.d(TAG, "1 onCaptureStarted => " + request);
//                                    }
//
//                                    @Override
//                                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
//                                        super.onCaptureFailed(session, request, failure);
//                                        Log.d(TAG, "1 onCaptureFailed => " + failure.getReason());
//                                    }
//
//                                    @Override
//                                    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
//                                        super.onCaptureProgressed(session, request, partialResult);
//                                        Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
//                                        Log.d(TAG, "1 onCaptureProgressed => " + aeState);
////                                        session.capture(captureBuilder.build(), captureListener, null);
//                                    }
//
//                                    @Override
//                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                                        super.onCaptureCompleted(session, request, result);
//                                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                                        Log.d(TAG, "1 onCaptureCompleted => " + aeState);
//                                    }
//                                }, mBackgroundHandler);
                        session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                                    @Override
                                    public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                                        super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                                        Log.d(TAG, "onCaptureSequenceCompleted");
                                    }

                                    @Override
                                    public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                                        super.onCaptureSequenceAborted(session, sequenceId);
                                        Log.d(TAG, "onCaptureSequenceAborted");
                                    }

                                    @Override
                                    public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                                        super.onCaptureBufferLost(session, request, target, frameNumber);
                                        Log.d(TAG, "onCaptureBufferLost => " + request);
                                    }

                                    @Override
                                    public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                                        Log.d(TAG, "onCaptureStarted => " + request);
                                    }

                                    @Override
                                    public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                                        super.onCaptureFailed(session, request, failure);
                                        Log.d(TAG, "onCaptureFailed => " + request);
                                    }

                                    @Override
                                    public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                        super.onCaptureProgressed(session, request, partialResult);
                                        Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
                                        Log.d(TAG, "onCaptureProgressed => " + aeState);
//                                        session.capture(captureBuilder.build(), captureListener, null);
                                    }

                                    @Override
                                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                        super.onCaptureCompleted(session, request, result);
                                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                        Log.d(TAG, "onCaptureCompleted => " + aeState);
                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Map<String, Object> params = new HashMap<>();
                                                params.put("path", file.getAbsolutePath());
                                                methodChannel.invokeMethod("onImageCaptured", params);
//                            createCameraPreview();
//                            unlockFocus();
                                                cameraCaptureSessions.close();
                                                createCameraPreviewSession();
                                            }
                                        });
                                    }
                                },
                                mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        methodChannel.setMethodCallHandler(null);

        CameraFragment f = (CameraFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.cameraFragment);
        if (f != null) {
            activity.getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
    }

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(context, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }

        return true;
    }
}