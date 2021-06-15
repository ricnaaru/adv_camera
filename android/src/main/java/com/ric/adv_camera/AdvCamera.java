package com.ric.adv_camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

@SuppressWarnings("ALL")
public class AdvCamera implements MethodChannel.MethodCallHandler,
        PlatformView {
    private final MethodChannel methodChannel;
    private final Context context;
    private final Activity activity;
    private boolean disposed = false;
    private final View view;
    private androidx.camera.core.Camera camera;
    private int cameraFacing = 0;
    private SavePicTask savePicTask;
    private File folder;
    private Integer maxSize;
    private String savePath;
    private String fileNamePrefix = "adv_camera";
    private int iOrientation = 0;
    private int mPhotoAngle = 90;
    private String previewRatio;
    private float mDist;
    private Camera.Size pictureSize;
    private String flashType = Camera.Parameters.FLASH_MODE_AUTO;
    private boolean bestPictureSize;
    //    private View focusRect;
    private WaitForCameraObject waitForCameraObject;
    private int focusRectColor = Color.GREEN;
    private float focusRectSize = 100f;
    PreviewView previewView;
    float lastScale = 0f;

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    AdvCamera(
            int id,
            final Context context,
            PluginRegistry.Registrar registrar, Object args) {
        this.context = context;
        this.activity = registrar.activity();
        Log.d("ricric", "id => " + id);

        methodChannel =
                new MethodChannel(registrar.messenger(), "plugins.flutter.io/adv_camera/" + id);
        methodChannel.setMethodCallHandler(this);
        view = registrar.activity().getLayoutInflater().inflate(com.ric.adv_camera.R.layout.activity_camera, null);


        previewView = view.findViewById(R.id.previewView);

        CameraX.initialize(context, Camera2Config.defaultConfig());
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindImageAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(context));
        final ListenableFuture[] xxx = new ListenableFuture[1];
        final long[] lastTime = {0};
        ScaleGestureDetector.SimpleOnScaleGestureListener listener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = camera.getCameraInfo().getZoomRatio().getValue() * detector.getScaleFactor();

                camera.getCameraControl().setZoomRatio(scale);
//                if (detector.getEventTime() - lastTime[0] < 100) return true;

//                lastTime[0] = detector.getEventTime();
//                float minScale = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
//                float maxScale = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
//
//                float x = detector.getCurrentSpan() - detector.getPreviousSpan();
//                float scale = camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * x / 400;
//                lastScale = clamp(lastScale + scale, minScale, maxScale);
//                float finalScale = lastScale;
//                Log.d("ricric", "camera.getCameraInfo().getZoomState().getValue().getZoomRatio() => " + camera.getCameraInfo().getZoomState().getValue().getZoomRatio());
//                Log.d("ricric", "detector.getScaleFactor() => " + detector.getScaleFactor() + " + " + detector.getCurrentSpan() + " + " + detector.getPreviousSpan());
//
//                Log.d("ricric", "lastScale + scale => " + lastScale + " + " + scale);
//                Log.d("ricric", "detector.getTimeDelta() xxx.length => (" + xxx.length + " > 0 && " + xxx[0] + ".isDone()) || " + xxx[0] + " == null");
////                if ((xxx[0] != null && xxx[0].isDone()) || xxx[0] == null) {
////                    Log.d("ricric", "detector.getTimeDelta() => " + detector.getTimeDelta() + ", " + detector.getEventTime());
////                    xxx[0] = camera.getCameraControl().setZoomRatio(finalScale);
////                }
////                return true;
//                if (xxx[0] == null || (xxx[0] != null && xxx[0].isDone())) {
////                    xxx[0].cancel(false);
//                    xxx[0] = camera.getCameraControl().setZoomRatio(finalScale);
//                    xxx[0].addListener(new Runnable() {
//                        @Override
//                        public void run() {
//                            Log.d("ricric", "run");
//                            lastScale = finalScale;
//                        }
//                    }, new Executor() {
//                        @Override
//                        public void execute(Runnable runnable) {
//                            Log.d("ricric", "execute --> " + camera.getCameraInfo().getZoomState().getValue().getZoomRatio());
//                        }
//                    });
//                }
                return true;
            }
        };

        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(context, listener);

        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d("ricric", "touched " + motionEvent.getPointerCount() + ", " + motionEvent.actionToString(motionEvent.getAction()));

                if (motionEvent.getPointerCount() > 1) {
                    scaleGestureDetector.onTouchEvent(motionEvent);
                } else if (motionEvent.getPointerCount() == 1 && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    SurfaceOrientedMeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(previewView.getWidth(), previewView.getHeight());
                    MeteringPoint point = factory.createPoint(motionEvent.getX(), motionEvent.getY());
//                    MeteringPoint point2 = new MeteringPoint(motionEvent.getX(), motionEvent.getY(), 100, null);
                    Log.d("ricric", "focusing " + point.getX() + ", " + point.getY());
                    FocusMeteringAction action = FocusMeteringAction.Builder.from(point).build();
                    CameraControl cameraControl = camera.getCameraControl();
                    cameraControl.startFocusAndMetering(action);
                }

//                if (motionEvent.getPointerCount() > 1) {
//                    int action2 = motionEvent.getAction();
//                    // handle multi-touch events
//                    if (action2 == MotionEvent.ACTION_POINTER_DOWN) {
//                        mDist = getFingerSpacing(motionEvent);
//                    } else if (action2 == MotionEvent.ACTION_MOVE) {
//                        handleZoom(motionEvent);
//                    }
//                }

                return true;
            }
        });
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder().setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                image.close();
            }
        });
        OrientationEventListener orientationEventListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int orientation) {
                Log.d("ricric", "orientation - " + orientation);
            }
        };
        orientationEventListener.enable();
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        preview.setPreviewSurfaceProvider(previewView.getPreviewSurfaceProvider());
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector,
                imageAnalysis, preview);


    }

    private void recordVideo() {
    }

    float ulala = 1.0f;

    @Override
    public void onMethodCall(MethodCall methodCall, @NonNull MethodChannel.Result result) {
        Log.d("ricric", "methodCall.method => " + methodCall.method);
        switch (methodCall.method) {
            case "waitForCamera":
//                if (camera == null)
//                    waitForCameraObject = new WaitForCameraObject(result);
//                else
                result.success(true);
                break;
            case "turnOff":
//                if (camera != null) {
//                    camera.stopPreview();
//                    camera.release();
//                    camera = null;
//                }
//                result.success(null);
                break;
            case "turnOn":
                setupCamera();
                result.success(null);
                break;
            case "setPreviewRatio": {
//                String previewRatio = "";
//
//                if (methodCall.arguments instanceof HashMap) {
//                    @SuppressWarnings({"unchecked"})
//                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                    Object previewRatioRaw = params.get("previewRatio");
//                    previewRatio = previewRatioRaw == null ? null : previewRatioRaw.toString();
//                }
//
//                Camera.Parameters param = camera.getParameters();
//
//                List<Camera.Size> sizes = param.getSupportedPreviewSizes();
//                Camera.Size selectedSize = null;
//                for (Camera.Size size : sizes) {
//                    if (asFraction(size.width, size.height).equals(previewRatio)) {
//                        selectedSize = size;
//                        break;
//                    }
//                }
//
//                if (selectedSize == null) {
//                    result.success(false);
//                    return;
//                }
//
//                this.previewRatio = previewRatio;
//
//                param.setPreviewSize(selectedSize.width, selectedSize.height);
//
//                camera.stopPreview();
//                camera.setParameters(param);
//                try {
//                    camera.setPreviewDisplay(surfaceHolder);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                camera.startPreview();
//
//                result.success(true);
                break;
            }
            case "captureImage":
//                Log.d("ricric2", "ulala1 " + ulala);
//                if (ulala == 1f)
//                    ulala = 4f;
//                else
//                    ulala = 1f;
//                ListenableFuture xxxx = camera.getCameraControl().setZoomRatio(ulala);
//                xxxx.addListener(new Runnable() {
//                    @Override
//                    public void run() {
//                        Log.d("ricric2", "run " + ulala + ", " + camera.getCameraInfo().getZoomState().getValue().getZoomRatio());
//                    }
//                }, new Executor() {
//                    @Override
//                    public void execute(Runnable runnable) {
//                        Log.d("ricric2", "execute " + ulala + ", " + camera.getCameraInfo().getZoomState().getValue().getZoomRatio());
//                    }
//                });
//                Log.d("ricric2", "ulala2 " + ulala);
//                Integer maxSize = null;
//
//                if (methodCall.arguments instanceof HashMap) {
//                    @SuppressWarnings({"unchecked"})
//                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                    maxSize = params.get("maxSize") == null ? null : (Integer) params.get("maxSize");
//                }
//
//                if (maxSize != null) {
//                    this.maxSize = maxSize;
//                }
//
//                captureImage();
//
                result.success(true);
                break;
            case "recordVideo":
                recordVideo();

                result.success(true);
                break;
            case "switchCamera":
//                if (cameraFacing == 0) {
//                    cameraFacing = 1;
//                } else {
//                    cameraFacing = 0;
//                }
//
//                camera.stopPreview();
//                camera.release();
//                setupCamera();
                result.success(true);
                break;
            case "getPictureSizes": {
//                List<String> pictureSizes = new ArrayList<>();
//
//                Camera.Parameters param = camera.getParameters();
//
//                List<Camera.Size> sizes = param.getSupportedPictureSizes();
//                for (Camera.Size size : sizes) {
//                    pictureSizes.add(size.width + ":" + size.height);
//                }
//
//                result.success(pictureSizes);
                break;
            }
            case "setPictureSize": {
//                int pictureWidth = 0;
//                int pictureHeight = 0;
//                String error = "";
//
//                if (methodCall.arguments instanceof HashMap) {
//                    @SuppressWarnings({"unchecked"})
//                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                    pictureWidth = (int) params.get("pictureWidth");
//                    pictureHeight = (int) params.get("pictureHeight");
//                }
//
//                Camera.Parameters param = camera.getParameters();
//
//                param.setPictureSize(pictureWidth, pictureHeight);
//
//                camera.stopPreview();
//
//                try {
//                    camera.setParameters(param);
//                    camera.setPreviewDisplay(surfaceHolder);
//                    this.pictureSize = camera.new Size(pictureWidth, pictureHeight);
//                } catch (IOException e) {
//                    error = e.getMessage();
//                } catch (RuntimeException e) {
//                    error = e.getMessage();
//                }
//
//                camera.startPreview();
//
//                if (error.isEmpty()) {
//                    result.success(true);
//                } else {
//                    result.error("Camera Error", "setPictureSize", error);
//                }
                break;
            }
            case "setSavePath":
//                if (methodCall.arguments instanceof HashMap) {
//                    @SuppressWarnings({"unchecked"})
//                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                    this.savePath = params.get("savePath") == null ? null : params.get("savePath").toString();
//                }
//
//                folder = new File(this.savePath);
//
//                if (!folder.exists()) {
//                    folder.mkdirs();
//                }
//
//                result.success(true);
                break;
            case "getFlashType": {
//                Camera.Parameters param = camera.getParameters();
//                result.success(param.getSupportedFlashModes());
                break;
            }
            case "setFlashType": {
//                String flashType = "auto";
//
//                if (methodCall.arguments instanceof HashMap) {
//                    @SuppressWarnings({"unchecked"})
//                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
//                    flashType = params.get("flashType") == null ? "auto" : params.get("flashType").toString();
//                }
//
//                Camera.Parameters param = camera.getParameters();
//
//                if (this.flashType.equals("torch") && flashType.equals("on")) {
//                    param.setFlashMode("off");
//                    camera.setParameters(param);
//                }
//
//                if (flashType.equals("torch")) {
//                    List<String> supportedFlashModes = param.getSupportedFlashModes();
//
//                    if (supportedFlashModes != null) {
//                        if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
//                            this.flashType = Camera.Parameters.FLASH_MODE_TORCH;
//                        } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
//                            this.flashType = Camera.Parameters.FLASH_MODE_ON;
//                        }
//                    }
//                } else {
//                    this.flashType = flashType;
//                }
//
//                param.setFlashMode(translateFlashType(param.getSupportedFlashModes()));
//
//                camera.stopPreview();
//                camera.setParameters(param);
//                try {
//                    camera.setPreviewDisplay(surfaceHolder);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                camera.startPreview();
//
//                result.success(true);
                break;
            }
            case "setFocus": {
                float x = 0f;
                float y = 0f;

                if (methodCall.arguments instanceof HashMap) {
                    Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                    x = (float) Float.parseFloat(params.get("x").toString());
                    y = (float) Float.parseFloat(params.get("y").toString());
                }

                handleFocus(x, y);
                break;
            }
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
//        disposed = true;
//        methodChannel.setMethodCallHandler(null);
//
//        CameraFragment f = (CameraFragment) activity.getFragmentManager()
//                .findFragmentById(com.ric.adv_camera.R.id.cameraFragment);
//        if (f != null) {
//            activity.getFragmentManager().beginTransaction().remove(f).commit();
//        }
    }

//    @Override
//    public void surfaceCreated(SurfaceHolder holder) {
//        setupCamera();
//    }

    private void setupCamera() {
//        try {
//            if (cameraFacing == 0) {
//                camera = Camera.open(0);
//            } else {
//                camera = Camera.open(1);
//            }
//        } catch (RuntimeException e) {
//            e.printStackTrace();
//            return;
//        }
//
//        if (waitForCameraObject != null) {
//            waitForCameraObject.notifyCameraSet();
//            waitForCameraObject = null;
//        }

        try {
//            Camera.Parameters param = camera.getParameters();
//
//            if (this.bestPictureSize) {
//                List<Camera.Size> sizes2 = param.getSupportedPictureSizes();
//                Collections.sort(sizes2, new Comparator<Camera.Size>() {
//                    @Override
//                    public int compare(Camera.Size o1, Camera.Size o2) {
//                        return (o2.width - o1.width) + (o2.height - o1.height);
//                    }
//                });
//
//                pictureSize = sizes2.get(0);
//            } else {
//                pictureSize = param.getPictureSize();
//            }
//
//            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
//            Camera.Size selectedSize = sizes.get(0);
//            for (Camera.Size size : sizes) {
//                if (asFraction(size.width, size.height).equals(this.previewRatio)) {
//                    selectedSize = size;
//                    break;
//                }
//            }
//
//            //get diff to get perfact preview sizes
//            DisplayMetrics displaymetrics = new DisplayMetrics();
//            activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
//
//            param.setPreviewSize(selectedSize.width, selectedSize.height);
//            param.setPictureSize(pictureSize.width, pictureSize.height);
//            param.setFlashMode(translateFlashType(param.getSupportedFlashModes()));
//
//            List<String> supportedFocusMode = param.getSupportedFocusModes();
//            String focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
//            if (!supportedFocusMode.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//                if (supportedFocusMode.size() > 0) {
//                    focusMode = supportedFocusMode.get(0);
//                }
//            }
//            param.setFocusMode(focusMode);
//
//            /// I block this script because Xiaomi 4a and Huawei gets rotated because of this
//            int orientation = setCameraDisplayOrientation(0);
////            param.setRotation(orientation);
//
//            try {
//                camera.setParameters(param);
//            } catch (RuntimeException e) {
//                Log.d("AdvCamera", "set Parameters Failed\n" + pictureSize.width + ", " + pictureSize.height);
//            }
//
//            camera.setPreviewDisplay(surfaceHolder);
//            camera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String translateFlashType(List<String> supportedModes) {
        String result = this.flashType;

        if (cameraFacing == 1) {
            if (!this.flashType.equals("off")) {
                result = "on";
            }
        }

        if (supportedModes != null && !supportedModes.contains(result)) {
            if (supportedModes.size() > 0) {
                result = supportedModes.get(0);
            } else {
                result = "";
            }
        } else {
            result = "off";
        }

        return result;
    }

//    @Override
//    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//        refreshCamera();
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder holder) {
//        try {
//            if (camera != null) {
//                camera.stopPreview();
//                camera.release();
//                camera = null;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private int setCameraDisplayOrientation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        if (Build.MODEL.equalsIgnoreCase("Nexus 6") && cameraFacing == 1) {
            rotation = Surface.ROTATION_180;
        }

        int degrees = 0;
        switch (rotation) {

            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {

            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror

        } else {
            result = (info.orientation - degrees + 360) % 360;

        }

//        camera.setDisplayOrientation(result);

        return result;

    }

    private void refreshCamera() {
//        if (surfaceHolder.getSurface() == null) {
//            return;
//        }
//        try {
//            camera.stopPreview();
//            Camera.Parameters param = camera.getParameters();
//            param.setFlashMode(translateFlashType(param.getSupportedFlashModes()));
//            refreshCameraPreview(param);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private void refreshCameraPreview(Camera.Parameters param) {
//        try {
//            //this is unnecessary because on certain device (Xiaomi 4A / Huawei) it is rotated
//            int orientation = setCameraDisplayOrientation(0);
////            param.setRotation(orientation);
//            camera.setParameters(param);
//
//            camera.setPreviewDisplay(surfaceHolder);
//            camera.startPreview();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private void cancelSavePicTaskIfNeed() {
        if (savePicTask != null && savePicTask.getStatus() == AsyncTask.Status.RUNNING) {
            savePicTask.cancel(true);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SavePicTask extends AsyncTask<Void, Void, String> {
        private final byte[] data;
        private final int rotation;

        SavePicTask(byte[] data, int rotation) {
            this.data = data;
            this.rotation = rotation;
        }

        protected void onPreExecute() {

        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return saveToSDCard(data, rotation);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Map<String, Object> params = new HashMap<>();
            params.put("path", result);
            methodChannel.invokeMethod("onImageCaptured", params);

            refreshCamera();

        }
    }

    private String saveToSDCard(byte[] data, int rotation) {
        String imagePath = "";

        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);

            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int reqHeight = metrics.heightPixels;
            int reqWidth = metrics.widthPixels;
            // Fix for exporting image with correct resolution in landscape mode
            if (reqWidth > reqHeight) {
                reqHeight = metrics.widthPixels;
                reqWidth = metrics.heightPixels;
            }

//            // Fix for exporting image with correct resolution in landscape mode
//            if(reqWidth > reqHeight){
//                reqHeight = metrics.widthPixels;
//                reqWidth = metrics.heightPixels;
//            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

            if (Build.MODEL.equalsIgnoreCase("LGM-G600L")) {
                //for now there's a case for LGM-G600L phone that its rotation degree exceeded by 90
                rotation -= 90;
            }

            if (maxSize != null) {
                double initialWidth = bitmap.getWidth();
                double initialHeight = bitmap.getHeight();
                int width = initialHeight < initialWidth ? maxSize : (int) (initialWidth / initialHeight * maxSize);
                int height = initialWidth <= initialHeight ? maxSize : (int) (initialHeight / initialWidth * maxSize);

                bitmap = Bitmap.createScaledBitmap(bitmap, width,
                        height, true);
            }

            if (rotation != 0) {
                Matrix mat = new Matrix();
                mat.postRotate(rotation);

                Bitmap bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
                if (bitmap != bitmap1) {
                    bitmap.recycle();
                }

                if (cameraFacing == 1) {
                    Matrix matrixMirror = new Matrix();
                    matrixMirror.preScale(-1.0f, 1.0f);
                    Bitmap mirroredBitmap = Bitmap.createBitmap(bitmap1, 0, 0, bitmap1.getWidth(), bitmap1.getHeight(), matrixMirror, true);

                    if (mirroredBitmap != bitmap1) {
                        bitmap1.recycle();
                    }

                    imagePath = getSavePhotoLocal(mirroredBitmap);

                    if (mirroredBitmap != null) {
                        mirroredBitmap.recycle();
                    }
                } else {
                    imagePath = getSavePhotoLocal(bitmap1);
                    if (bitmap1 != null) {
                        bitmap1.recycle();
                    }
                }
            } else {
                if (cameraFacing == 1) {
                    Matrix matrixMirror = new Matrix();
                    matrixMirror.preScale(-1.0f, 1.0f);
                    Bitmap mirroredBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrixMirror, true);

                    if (mirroredBitmap != bitmap) {
                        bitmap.recycle();
                    }

                    imagePath = getSavePhotoLocal(mirroredBitmap);

                    if (mirroredBitmap != null) {
                        mirroredBitmap.recycle();
                    }
                } else {
                    imagePath = getSavePhotoLocal(bitmap);
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imagePath;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    private String getSavePhotoLocal(Bitmap bitmap) {
        String path = "";
        Date currentTime = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        try {
            OutputStream output;
            File file = new File(folder.getAbsolutePath(), fileNamePrefix + "_" + dateFormat.format(currentTime) + ".jpg");
            try {
                output = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                output.flush();
                output.close();
                path = file.getAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    private int getPhotoRotation() {
        int rotation;
        int orientation = mPhotoAngle;

        Camera.CameraInfo info = new Camera.CameraInfo();
        if (cameraFacing == 0) {
            Camera.getCameraInfo(0, info);
        } else {
            Camera.getCameraInfo(1, info);
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {
            rotation = (info.orientation + orientation) % 360;
        }

        return rotation;
    }

    private void identifyOrientationEvents() {
        OrientationEventListener myOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int iAngle) {
                final int[] iLookup = {0, 0, 0, 90, 90, 90, 90, 90, 90, 180, 180, 180, 180, 180, 180, 270, 270, 270, 270, 270, 270, 0, 0, 0}; // 15-degree increments
                if (iAngle != ORIENTATION_UNKNOWN) {
                    int iNewOrientation = iLookup[iAngle / 15];
                    if (iOrientation != iNewOrientation) {
                        iOrientation = iNewOrientation;
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

    ListenableFuture lf;

    private void handleZoom(MotionEvent event) {
//        float minZoom = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
//        float maxZoom = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
//        float zoom = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
//        float newDist = getFingerSpacing(event);
//
//        if (Math.abs(newDist - mDist) < 2) return;
//
//        if (newDist > mDist) {
//            //zoom in
//            if (zoom < maxZoom)
//                zoom++;
//        } else if (newDist < mDist) {
//            //zoom out
//            if (zoom > 0)
//                zoom--;
//        }
//        zoom = clamp(zoom, minZoom, maxZoom);
//        mDist = newDist;
//        Log.d("ricric2", "zoom " + zoom);
////        if (lf == null || (lf != null && lf.isDone())) {
//        lf = camera.getCameraControl().setZoomRatio(zoom);
//        }
//        camera.setParameters(params);
    }

    private void handleFocus(float initialX, float initialY) {
//        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//        int surfaceHeight = imgSurface.getHeight();
//        int surfaceWidth = imgSurface.getWidth();
//
//        /// normal
//        float x = initialY;
//        float y = surfaceWidth - initialX;
//
//        if (rotation == Surface.ROTATION_90) {
//            /// for this rotation, we have to swap upper right corner to bottom left corner
//            /// the rest, bottom right and upper left, will still be the same
//            /// rotate left
//            final float xPercentage = initialY / surfaceHeight;
//            final float yPercentage = initialX / surfaceWidth;
//            final boolean condition = (xPercentage > .5) == (yPercentage > .5);
//            x = !condition ? (1 - xPercentage) * surfaceHeight : initialY;
//            y = !condition ? (1 - yPercentage) * surfaceWidth : initialX;
//        } else if (rotation == Surface.ROTATION_270) {
//            /// for this rotation, we have to swap upper left corner to bottom right corner
//            /// the rest, bottom left and upper right, will still be the same
//            /// rotate right
//            final float xPercentage = initialY / surfaceHeight;
//            final float yPercentage = initialX / surfaceWidth;
//            final boolean condition = (xPercentage > .5) == (yPercentage > .5);
//            x = condition ? (1 - xPercentage) * surfaceHeight : initialY;
//            y = condition ? (1 - yPercentage) * surfaceWidth : initialX;
//        }
//
//        //cancel previous actions
//        camera.cancelAutoFocus();
//
//        Rect touchRect = new Rect(
//                (int) (x - focusRectSize),
//                (int) (y - focusRectSize),
//                (int) (x + focusRectSize),
//                (int) (y + focusRectSize));
//
//        int aboutToBeLeft = touchRect.left;
//        int aboutToBeTop = touchRect.top;
//        int aboutToBeRight = touchRect.right;
//        int aboutToBeBottom = touchRect.bottom;
//
//        if (aboutToBeLeft < 0) {
//            aboutToBeLeft = 0;
//            aboutToBeRight = 200;
//        }
//        if (aboutToBeTop < 0) {
//            aboutToBeTop = 0;
//            aboutToBeBottom = 200;
//        }
//        if (aboutToBeRight > surfaceHeight) {
//            aboutToBeRight = surfaceHeight;
//            aboutToBeLeft = surfaceHeight - 200;
//        }
//        if (aboutToBeBottom > surfaceWidth) {
//            aboutToBeBottom = surfaceWidth;
//            aboutToBeTop = surfaceWidth - 200;
//        }
//
//        aboutToBeLeft = aboutToBeLeft * 2000 / surfaceHeight - 1000;
//        aboutToBeTop = aboutToBeTop * 2000 / surfaceWidth - 1000;
//        aboutToBeRight = aboutToBeRight * 2000 / surfaceHeight - 1000;
//        aboutToBeBottom = aboutToBeBottom * 2000 / surfaceWidth - 1000;
//
//        Rect focusRect = new Rect(
//                aboutToBeLeft,
//                aboutToBeTop,
//                aboutToBeRight,
//                aboutToBeBottom);
//
////        this.focusRect.setLeft(touchRect.left);
////        this.focusRect.setTop(touchRect.top);
////        this.focusRect.setRight(touchRect.right);
////        this.focusRect.setBottom(touchRect.bottom);
//
//        final float RectLeft = initialX - focusRectSize;
//        final float RectTop = initialY - focusRectSize;
//        final float RectRight = initialX + focusRectSize;
//        final float RectBottom = initialY + focusRectSize;
//
//        setFocus(RectLeft, RectTop, RectRight, RectBottom, focusRectColor);
//
//        Camera.Parameters parameters = null;
//
//        try {
//            parameters = camera.getParameters();
//        } catch (Exception e) {
//            Log.e("Error", "Error getting parameter:" + e);
//        }
//
//        // check if parameters are set (handle RuntimeException: getParameters failed (empty parameters))
//        if (parameters != null) {
//            List<Camera.Area> mylist2 = new ArrayList<>();
//
//            mylist2.add(new Camera.Area(focusRect, 1000));
//
//            List<String> supportedFocusMode = parameters.getSupportedFocusModes();
//            String focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
//            if (!supportedFocusMode.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
//                if (supportedFocusMode.size() > 0) {
//                    focusMode = supportedFocusMode.get(0);
//                }
//            }
//            parameters.setFocusMode(focusMode);
//            if (focusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO))
//                parameters.setFocusAreas(mylist2);
//
//            try {
//                camera.setParameters(parameters);
//                camera.autoFocus(new Camera.AutoFocusCallback() {
//                    @Override
//                    public void onAutoFocus(boolean success, Camera camera) {
//                    }
//                });
//            } catch (Exception e) {
//                Log.e("error", "error => " + e);
//            }
//        }
    }

    private float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * @return the greatest common denominator
     */
    private static long gcm(long a, long b) {
        return b == 0 ? a : gcm(b, a % b); // Not bad for one line of code :)
    }

    private static String asFraction(long a, long b) {
        long gcm = gcm(a, b);
        return (a / gcm) + ":" + (b / gcm);
    }


    Canvas canvas;
    Paint paint;
    Canvas dismissCanvas;
    long lastId;

    private void setFocus(float RectLeft, float RectTop, float RectRight, float RectBottom, int color) {

//        canvas = holderTransparent.lockCanvas();
//        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
//        //border's properties
//        paint = new Paint();
//        paint.setStyle(Paint.Style.STROKE);
//        paint.setColor(color);
//        paint.setStrokeWidth(3);
//        canvas.drawRect(RectLeft, RectTop, RectRight, RectBottom, paint);
//
//        holderTransparent.unlockCanvasAndPost(canvas);
//
//        final long id = System.currentTimeMillis();
//        final DismissHandler handler = new DismissHandler(id);
//        lastId = id;
//
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if (handler.id == lastId) {
//                    dismissCanvas = holderTransparent.lockCanvas();
//                    if (dismissCanvas != null) {
//                        dismissCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
//                        holderTransparent.unlockCanvasAndPost(dismissCanvas);
//                    }
//                }
//            }
//        }, 2000);
    }
}

//class DismissHandler extends Handler {
//    long id;
//
//    public DismissHandler(long id) {
//        this.id = id;
//    }
//}z