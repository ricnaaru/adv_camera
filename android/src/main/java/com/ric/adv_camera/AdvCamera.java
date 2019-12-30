package com.ric.adv_camera;

import android.Manifest;
import android.content.Context;
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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
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

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (cameraDevice == null) {
                openCamera();
            } else {
                createCameraPreview();
            }
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

        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            cameraId = manager.getCameraIdList()[cameraFacing];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            previewSizes = map.getOutputSizes(SurfaceTexture.class);
            if (imageDimension == null)
                for (Size size : previewSizes) {
                    if (asFraction(size.getWidth(), size.getHeight()).equals(previewRatio)) {
                        imageDimension = size;
                        break;
                    }
                }

            if (imageDimension == null) {
                imageDimension = previewSizes[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

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
            cameraDevice = camera;
            if (textureView.isAvailable()) {
                createCameraPreview();
            } else {
                textureView.setSurfaceTextureListener(textureListener);
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
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

    protected void onPause() {
        stopBackgroundThread();
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[cameraFacing];

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

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

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
            takePicture();
            result.success(true);
        } else if (methodCall.method.equals("switchCamera")) {
            if (cameraId.equals(CAMERA_FRONT)) {
                cameraFacing = 0;

                cameraDevice.close();
                cameraDevice = null;
                cameraId = null;

                openCamera();
//                reopenCamera();
//                switchCameraButton.setImageResource(R.drawable.ic_camera_front);

            } else if (cameraId.equals(CAMERA_BACK)) {
                cameraFacing = 1;

                cameraDevice.close();
                cameraDevice = null;
                cameraId = null;
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
//            String flashType = "auto";
//
//            if (methodCall.arguments instanceof HashMap) {
//                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                flashType = params.get("flashType") == null ? "auto" : params.get("flashType").toString();
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
//            result.success(true);
        }
    }

    private static long gcm(long a, long b) {
        return b == 0 ? a : gcm(b, a % b); // Not bad for one line of code :)
    }

    private static String asFraction(long a, long b) {
        long gcm = gcm(a, b);
        return (a / gcm) + ":" + (b / gcm);
    }

    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            if (cameraFacing == 0) {
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mPhotoAngle + 90);
            } else {
                int x = manager.getCameraCharacteristics(cameraId).get(
                        CameraCharacteristics.SENSOR_ORIENTATION);

                Log.d(TAG, "x => " + x);
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, x);
//                captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE,  x);
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

//                        if (cameraFacing == 0) {
                            save(bytes);
//                        } else {
//                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
//                            Matrix matrix = new Matrix();
//                            matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
//
//                            Bitmap bb = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                            bb.compress(Bitmap.CompressFormat.PNG, 100, stream);
//                            byte[] byteArray = stream.toByteArray();
//                            bitmap.recycle();
//
//                            bb.recycle();
//
//                            save(byteArray);
//                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
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
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, "Saved:" + file, Toast.LENGTH_SHORT).show();
                            Map<String, Object> params = new HashMap<>();
                            params.put("path", file.getAbsolutePath());
                            methodChannel.invokeMethod("onImageCaptured", params);
                            createCameraPreview();
                        }
                    });
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
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