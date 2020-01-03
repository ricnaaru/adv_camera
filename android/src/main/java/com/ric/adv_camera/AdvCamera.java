/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ric.adv_camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

public class AdvCamera implements MethodChannel.MethodCallHandler,
        PlatformView, PluginRegistry.RequestPermissionsResultListener {
    private final Context context;
    private final FragmentActivity activity;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

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

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: " + width + " x " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
//            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private TextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened. We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Date currentTime = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            mFile = new File(folder.getAbsolutePath(), fileNamePrefix + "_" + dateFormat.format(currentTime) + ".jpg");
//            Log.d(TAG, "onImageAvailable => " + (mBackgroundHandler == null));
//            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(),file));
//            mFile.mkdirs();

            Log.d(TAG, mFile.canRead() + " pre onImageAvailable => " + mFile.getAbsolutePath());



            Image image = null;
            if (mState != STATE_PICTURE_TAKEN) return;
            Log.d(TAG, mFile.canRead() + " onImageAvailable => " + mFile.getAbsolutePath());
            try {
                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.d(TAG, "LENS_FACING_BACK");
                    save(bytes, mFile);
                } else {
                    Log.d(TAG, "not LENS_FACING_BACK");
                    Integer x = manager.getCameraCharacteristics(mCameraId).get(
                            CameraCharacteristics.SENSOR_ORIENTATION);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Matrix matrix = new Matrix();
                    if (x != null)
                        matrix.postRotate(x - mPhotoAngle);
                    matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);

                    Bitmap bb = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    save(bb, mFile);
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

        private void save(byte[] bytes, File file) throws IOException {
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

        private void save(Bitmap bitmap, File file) throws IOException {
            Log.d(TAG, "saving bitmap => " + bitmap.getWidth() + " x " + bitmap.getHeight());
            OutputStream fOut = new FileOutputStream(file);

//                    Bitmap pictureBitmap = getImageBitmap(myurl); // obtaining the Bitmap
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
            fOut.flush(); // Not really required
            fOut.close(); // do not forget to close the stream
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            Log.d(TAG, "process (" + mState + ")");
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    Log.d(TAG, "STATE_WAITING_LOCK (" + mState + ") => " + afState);
//                    if (afState == null || afState == CaptureResult.CONTROL_AF_STATE_INACTIVE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
//                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
//                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
//                        // CONTROL_AE_STATE can be null on some devices
//                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                        if (aeState == null ||
//                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
//                            mState = STATE_PICTURE_TAKEN;
//                            captureStillPicture();
//                        } else {
//                            runPrecaptureSequence();
//                        }
//                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d(TAG, "STATE_WAITING_PRECAPTURE (" + mState + ") => " + aeState);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    Log.d(TAG, "STATE_WAITING_NON_PRECAPTURE (" + mState + ") => " + aeState);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private String flashType;
    private String savePath;
    private String previewRatio;
    private String fileNamePrefix = "adv_camera";
    private int maxSize;
    private File folder;
    private final MethodChannel methodChannel;
    private View view;
    private int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};

    AdvCamera(
            int id,
            final Context context,
            PluginRegistry.Registrar registrar, Object args) {
        this.context = context;
        this.activity = (FragmentActivity) registrar.activity();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (args instanceof HashMap) {
            Map<String, Object> params = (Map<String, Object>) args;
            Object initialCamera = params.get("initialCamera");
            Object flashType = params.get("flashType");
            Object savePath = params.get("savePath");
            Object previewRatio = params.get("previewRatio");
            Object fileNamePrefix = params.get("fileNamePrefix");
            Object maxSize = params.get("maxSize");

            if (initialCamera != null) {
                if (initialCamera.equals("front")) {
                    cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
                } else if (initialCamera.equals("rear")) {
                    cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
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

        mTextureView = view.findViewById(R.id.imgSurface);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        registrar.addRequestPermissionsResultListener(this);
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        identifyOrientationEvents();
    }

    private int mPhotoAngle = 90;

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

    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void onResume() {
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    Size[] mPreviewSizes;

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {


            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing != cameraFacing) {
                    Log.d(TAG, "setUpCameraOutputs => 1");
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    Log.d(TAG, "setUpCameraOutputs => 2");
                    continue;
                }

                mPreviewSizes = map.getOutputSizes(SurfaceTexture.class);

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                for (Size size : mPreviewSizes) {
                    if (asFraction(size.getWidth(), size.getHeight()).equals(previewRatio)) {
                        mPreviewSize = size;
                        break;
                    }
                }

                Log.d(TAG, "previewsize => " + mPreviewSize);

                if (mPreviewSize == null) {
                    mPreviewSize = mPreviewSizes[0];
                }

                // We fit the aspect ratio of TextureView to the size of preview we picked.
//                int orientation = context.getResources().getConfiguration().orientation;
//                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                    mTextureView.setAspectRatio(
//                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
//                } else {
//                    mTextureView.setAspectRatio(
//                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
//                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//            ErrorDialog.newInstance(getString(R.string.camera_error))
//                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private static long gcm(long a, long b) {
        return b == 0 ? a : gcm(b, a % b); // Not bad for one line of code :)
    }

    private static String asFraction(long a, long b) {
        long gcm = gcm(a, b);
        return (a / gcm) + ":" + (b / gcm);
    }

    /**
     * Opens the camera specified by {link mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        setUpCameraOutputs(width, height);
//        configureTransform(width, height);
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
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

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
//        runPrecaptureSequence();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        Log.d(TAG, "runPrecaptureSequence");
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        Log.d(TAG, "captureStillPicture 0!");
        try {
            if (null == activity || null == mCameraDevice) {
                return;
            }
            Log.d(TAG, "captureStillPicture 1!");
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            if (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mPhotoAngle + 90);
            }

            Log.d(TAG, "captureStillPicture 2!");
            final CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                    Log.d(TAG, "onCaptureStarted!");
                }


                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                    super.onCaptureProgressed(session, request, partialResult);
                    Log.d(TAG, "onCaptureProgressed!");
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.d(TAG, "onCaptureFailed!");
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "onCaptureCompleted");
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Map<String, Object> params = new HashMap<>();
                            params.put("path", mFile.getAbsolutePath());
                            methodChannel.invokeMethod("onImageCaptured", params);
                            unlockFocus();
                        }
                    });
                }
            };
            Log.d(TAG, "captureStillPicture 3!");
            Log.d(TAG, "runPrecaptureSequence");
            try {
                // This is how to tell the camera to trigger.
                captureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                // Tell #mCaptureCallback to wait for the precapture sequence to be set.
//                final int xxx = STATE_WAITING_PRECAPTURE;
                mCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                    int xxxx = STATE_WAITING_PRECAPTURE;
                            private void process(CaptureResult result) {
                                switch (xxxx) {
                                    case STATE_WAITING_PRECAPTURE: {
                                        // CONTROL_AE_STATE can be null on some devices
                                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                        Log.d(TAG, "STATE_WAITING_PRECAPTURE (" + xxxx + ") => " + aeState);
                                        if (aeState == null ||
                                                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                                            xxxx = STATE_WAITING_NON_PRECAPTURE;
                                        }
                                        break;
                                    }
                                    case STATE_WAITING_NON_PRECAPTURE: {
                                        // CONTROL_AE_STATE can be null on some devices
                                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                        Log.d(TAG, "STATE_WAITING_NON_PRECAPTURE (" + xxxx + ") => " + aeState);
                                        if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
//                                            mState = STATE_PICTURE_TAKEN;

                                            try {
                                                mCaptureSession.stopRepeating();
                                                mCaptureSession.abortCaptures();
                                                mCaptureSession.capture(captureBuilder.build(), captureCallback, null);
                                                Log.d(TAG, "captureStillPicture 4!");
                                            } catch (CameraAccessException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        break;
                                    }
                                }
                            }

                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                            @NonNull CaptureRequest request,
                                                            @NonNull CaptureResult partialResult) {
                                process(partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                           @NonNull CaptureRequest request,
                                                           @NonNull TotalCaptureResult result) {
                                process(result);
                            }
                        },
                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        Log.d(TAG, "ulockFocus");
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
//            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
//                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        if (mFlashSupported) {
            if (flashType.equals("off")) {
                Log.d(TAG, "flashType => off");
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            } else if (flashType.equals("on")) {
                Log.d(TAG, "flashType => on");
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                    } else if (flashType.equals("torch")) {
//                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else if (flashType.equals("auto")) {
                Log.d(TAG, "flashType => auto");
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_/OFF);
//                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
//                    captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
            }
        }
    }

    @Override
    public void onMethodCall(MethodCall methodCall, MethodChannel.Result result) {
        if (methodCall.method.equals("waitForCamera")) {
            result.success(null);
        } else if (methodCall.method.equals("setPreviewRatio")) {
//            String previewRatio = "";
//
//            if (cameraFacing == 1) return;
//
//            if (methodCall.arguments instanceof HashMap) {
//                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                previewRatio = params.get("previewRatio") == null ? null : params.get("previewRatio").toString();
//            }
//
//            for (Size size : previewSizes) {
//                if (asFraction(size.getWidth(), size.getHeight()).equals(previewRatio)) {
//                    imageDimension = size;
//                    break;
//                }
//            }
//
//            if (imageDimension == null) {
//                result.success(false);
//                return;
//            }
//
//            cameraDevice.close();
//            cameraDevice = null;
//            cameraId = null;

            result.success(true);
        } else if (methodCall.method.equals("captureImage")) {
            takePicture();
//            // This is how to tell the camera to lock focus.
////                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
////                        CameraMetadata.CONTROL_AF_TRIGGER_START);
//            // Tell #mCaptureCallback to wait for the lock.
//            Log.d(TAG, "before trigger 1! " + flashType);
//            if (flashType.equals("off")) {
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
//                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
//            } else if (flashType.equals("on")) {
//                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                    } else if (flashType.equals("torch")) {
////                        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//            } else if (flashType.equals("auto")) {
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
////                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_/OFF);
//                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
////                    captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT);
//            }
////                        session.capture(captureBuilder.build(), captureListener, null);
//            boolean aeState = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_LOCK);
//            Log.d(TAG, "before trigger! => " + aeState);
//            // This is how to tell the camera to trigger.
////                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
////                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
//            Log.d(TAG, "after trigger!");
//            mState = STATE_WAITING_LOCK;
//            setUpOutput();

            result.success(true);
        } else if (methodCall.method.equals("switchCamera")) {
            if (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
            } else if (cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
            }

            mCameraDevice.close();

            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
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
            if (mFlashSupported) {
                this.flashType = flashType;
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
            } else {
                this.flashType = "auto";
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
            setAutoFlash(mPreviewRequestBuilder);

            // Finally, we start displaying the camera preview.
//            mPreviewRequest = mPreviewRequestBuilder.build();
//            try {
//                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
//                        mBackgroundHandler);
////                mCaptureSession.setRepeatingRequest(mPreviewRequest,
////                        mCaptureCallback, mBackgroundHandler);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
            result.success(true);
        }
    }

    @Override
    public View getView() {
        return view;
    }

    private boolean disposed = false;
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

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(context, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
            }
        }

        return true;
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage("R.string.request_permission")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

}
