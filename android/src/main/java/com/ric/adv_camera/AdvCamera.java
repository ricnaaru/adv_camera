package com.ric.adv_camera;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        PlatformView, SurfaceHolder.Callback {
    private final MethodChannel methodChannel;
    private PluginRegistry.Registrar registrar;
    private final Context context;
    private final Activity activity;
    private boolean disposed = false;
    private View view;
    private SurfaceView imgSurface;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private int cameraFacing = 0;
    private int cameraFlashType = 1;
    private SavePicTask savePicTask;
    private Camera.PictureCallback jpegCallback;
    private File folder = null;
    private String savePath;
    private String fileNamePrefix = "adv_camera";
    private OrientationEventListener myOrientationEventListener;
    int iOrientation = 0;
    int mOrientation = 90;
    private int mPhotoAngle = 90;
    private String previewRatio;

    private int focusAreaSize = 100;
    private Matrix matrix = new Matrix();
    float mDist;
    private FooFragment fooFragment;

    AdvCamera(
            int id,
            final Context context,
            PluginRegistry.Registrar registrar, Object args) {
        this.context = context;
        this.activity = registrar.activity();
        this.registrar = registrar;

        methodChannel =
                new MethodChannel(registrar.messenger(), "plugins.flutter.io/adv_camera/" + id);
        methodChannel.setMethodCallHandler(this);
        view = registrar.activity().getLayoutInflater().inflate(R.layout.whatsapp_activity_camera, null);
        imgSurface = view.findViewById(R.id.imgSurface);
//        fooFragment = view.(R.id.fooFragment);
        fooFragment = (FooFragment) activity.getFragmentManager().findFragmentById(R.id.fooFragment);
        imgSurface.setFocusable(true);
        imgSurface.setFocusableInTouchMode(true);

        fooFragment.listener = new FragmentLifecycleListener() {
            @Override
            public void onPause() {
//                camera.unlock();
//                camera.stopPreview();
//                camera.release();
            }

            @Override
            public void onResume() {
                setupCamera();
            }
        };

        if (args instanceof HashMap) {
            Map<String, Object> params = (Map<String, Object>) args;
            Object initialCamera = params.get("initialCamera");
            Object flashType = params.get("flashType");
            Object savePath = params.get("savePath");
            Object previewRatio = params.get("previewRatio");
            Object fileNamePrefix = params.get("fileNamePrefix");

            if (initialCamera != null) {
                if (initialCamera.equals("front")) {
                    cameraFacing = 1;
                } else if (initialCamera.equals("rear")) {
                    cameraFacing = 0;
                }
            }

            if (flashType != null) {
                if (flashType.equals("auto")) {
                    cameraFlashType = 1;
                } else if (flashType.equals("on")) {
                    cameraFlashType = 2;
                } else if (flashType.equals("off")) {
                    cameraFlashType = 3;
                }
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
        }

        imgSurface.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                Log.d("tag", "setOnTouchListener " + (camera == null));
                // Get the pointer ID
                Camera.Parameters params = camera.getParameters();
                Log.d("tag", "setOnTouchListener param " + (camera.getParameters()));
                int action = event.getAction();
                Log.d("tag", "getPointerCount => " + event.getPointerCount());
                if (event.getPointerCount() > 1) {
                    // handle multi-touch events
                    if (action == MotionEvent.ACTION_POINTER_DOWN) {
                        mDist = getFingerSpacing(event);
                    } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                        camera.cancelAutoFocus();
                        handleZoom(event, params);
                    }
                } else {
                    // handle single touch events
                    if (action == MotionEvent.ACTION_UP) {
                        handleFocus(event, params);
                    }
                }
                return true;
            }
        });


        folder = new File(this.savePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        surfaceHolder = imgSurface.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        jpegCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera camera) {

                Log.d("tag", "onPictureTaken!");
                refreshCamera();

                cancelSavePicTaskIfNeed();
                savePicTask = new SavePicTask(data, getPhotoRotation());
                savePicTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

            }
        };

        identifyOrientationEvents();

//        if (checkPermission()) {
//            new DisplayImage(context, this, bucketId, true).execute();
//        }
//
//        registrar.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
//            @Override
//            public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//                if (requestCode == 28) {
//                    if (grantResults.length > 0) {
//                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                            // permission was granted, yay!
//                            getAlbums();
//                            new DisplayImage(context, AdvCamera.this, bucketId, true).execute();
//                        } else {
//                            new PermissionCheck(context).showPermissionDialog();
//                        }
//                    }
//                }
//                return false;
//            }
//        });
    }

    //    private boolean checkPermission() {
//        PermissionCheck permissionCheck = new PermissionCheck(registrar.activity());
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (permissionCheck.CheckStoragePermission()) {
//                return true;
//            }
//        } else {
//            return true;
//        }
//
//        return false;
//    }
//
    private void captureImage() {
        Log.d("tag", "start capturing!");
        camera.takePicture(null, null, jpegCallback);
//        inActiveCameraCapture();
    }

    @Override
    public void onMethodCall(MethodCall methodCall, MethodChannel.Result result) {
        if (methodCall.method.equals("waitForCamera")) {
            result.success(null);
        } else if (methodCall.method.equals("setPreviewRatio")) {
            if (methodCall.arguments instanceof HashMap) {
                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                this.previewRatio = params.get("previewRatio") == null ? null : params.get("previewRatio").toString();
            }

            Camera.Parameters param = camera.getParameters();

            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
            Camera.Size selectedSize = sizes.get(0);
            for (Camera.Size size : sizes) {
                Log.d("ricric", "setPreviewRatio Camera.Size => " + size.width + " - " + size.height);
                Log.d("ricric", "setPreviewRatio this.previewRatio => " + this.previewRatio);
                Log.d("ricric", "setPreviewRatio asFraction => " + asFraction(size.width, size.height));
                if (asFraction(size.width, size.height).equals(this.previewRatio)) {
                    selectedSize = size;
                    break;
                }
            }
            Log.d("ricric", "setPreviewRatio selectedSize => " + selectedSize.width + " - " + selectedSize.height);

            Log.d("tag", "setPreviewRatio imgSurface.getWidth() => " + imgSurface.getWidth());
            Log.d("tag", "setPreviewRatio imgSurface.getHeight() => " + imgSurface.getHeight());
            param.setPreviewSize(selectedSize.width, selectedSize.height);

            camera.stopPreview();
            camera.setParameters(param);
            try {
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("ricric", "eee => " + e.getMessage());
            }
            camera.startPreview();

            result.success(true);
        } else if (methodCall.method.equals("captureImage")) {
            captureImage();
        } else if (methodCall.method.equals("switchCamera")) {
            if (cameraFacing == 0) {
                cameraFacing = 1;
            } else {
                cameraFacing = 0;
            }

            camera.stopPreview();
            camera.release();
            setupCamera();
        } else if (methodCall.method.equals("getPictureSizes")) {
            List<String> pictureSizes = new ArrayList<>();

            Camera.Parameters param = camera.getParameters();

            List<Camera.Size> sizes2 = param.getSupportedPictureSizes();
            for (Camera.Size size : sizes2) {
                pictureSizes.add(size.width + ":" + size.height);
            }

            result.success(pictureSizes);
        } else if (methodCall.method.equals("setPictureSize")) {
            int pictureWidth = 0;
            int pictureHeight = 0;
            String error = "";

            if (methodCall.arguments instanceof HashMap) {
                Map<String, Object> params = (Map<String, Object>) methodCall.arguments;
                pictureWidth = (int) params.get("pictureWidth");
                pictureHeight = (int) params.get("pictureHeight");
            }

            Camera.Parameters param = camera.getParameters();

            param.setPictureSize(pictureWidth, pictureHeight);

            camera.stopPreview();
            try {
                camera.setParameters(param);
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = e.getMessage();
            }
            camera.startPreview();

            if (error.isEmpty()) {
                result.success(true);
            } else {
                result.error("Camera Error", "setPictureSize", error);
            }
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

            Camera.Parameters param = camera.getParameters();

            if (flashType.equals("auto")) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            } else if (flashType.equals("on")) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            } else if (flashType.equals("torch")) {
                List<String> supportedFlashModes = param.getSupportedFlashModes();

                if (supportedFlashModes != null) {
                    if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                        param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    }
                }
            } else if (flashType.equals("off")) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }

            camera.stopPreview();
            camera.setParameters(param);
            try {
                camera.setPreviewDisplay(surfaceHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            camera.startPreview();

            result.success(true);
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
        Log.d("ricric", "dispose");
        FooFragment f = (FooFragment) activity.getFragmentManager()
                .findFragmentById(R.id.fooFragment);
        if (f != null) {
            Log.d("ricric", "before commit");
            activity.getFragmentManager().beginTransaction().remove(f).commit();
            Log.d("ricric", "after commit");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setupCamera();
    }

    public void setupCamera() {
        Log.d("tag", "setupCamera started! => " + cameraFacing);


        try {
            if (cameraFacing == 0) {
                camera = Camera.open(0);
            } else {
                camera = Camera.open(1);
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            return;
        }

        try {
            Camera.Parameters param;
            param = camera.getParameters();
            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
            Camera.Size selectedSize = sizes.get(0);
            for (Camera.Size size : sizes) {
                Log.d("ricric", "selectedSize Camera.Size => " + size.width + " - " + size.height);
                Log.d("ricric", "selectedSize this.previewRatio => " + this.previewRatio);
                if (asFraction(size.width, size.height).equals(this.previewRatio)) {
                    selectedSize = size;
                    break;
                }
            }
            //get diff to get perfact preview sizes
            DisplayMetrics displaymetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
            int height = displaymetrics.heightPixels;
            int width = displaymetrics.widthPixels;
            Log.d("tag", "surfaceCreated displaymetrics.heightPixels => " + displaymetrics.heightPixels);
            Log.d("tag", "surfaceCreated displaymetrics.widthPixels => " + displaymetrics.widthPixels);
            Log.d("tag", "surfaceCreated getPreviewSize 0 => " + param.getPreviewSize().width);
            Log.d("tag", "surfaceCreated getPreviewSize 1 => " + param.getPreviewSize().height);

            Camera.Size cs = getOptimalPreviewSize(sizes, width, height); //sizes.get(idx);
//            param.setPreviewSize(imgSurface.getHeight(), imgSurface.getWidth());
            Log.d("tag", "surfaceCreated selectedSize.getWidth() => " + selectedSize.width);
            Log.d("tag", "surfaceCreated selectedSize.getHeight() => " + selectedSize.height);
            Log.d("tag", "surfaceCreated cs.getWidth() => " + cs.width);
            Log.d("tag", "surfaceCreated cs.getHeight() => " + cs.height);
            Log.d("tag", "surfaceCreated imgSurface.getWidth() => " + imgSurface.getWidth());
            Log.d("tag", "surfaceCreated imgSurface.getHeight() => " + imgSurface.getHeight());
            Log.d("tag", "surfaceCreated height => " + imgSurface.getWidth());
            Log.d("tag", "surfaceCreated width => " + imgSurface.getHeight());
//            param.setPictureSize(imgSurface.getHeight(), imgSurface.getWidth());
//            param.setPreviewSize(1710, 962);
//            param.setPictureSize(1710, 962);
            param.setPreviewSize(selectedSize.width, selectedSize.height);
            param.setPictureSize(cs.width, cs.height);
//            param.setPreviewSize(imgSurface.getWidth(), imgSurface.getHeight());
//            param.setPictureSize(imgSurface.getWidth(), imgSurface.getHeight());
//            param.setPreviewSize(1920, imgSurface.getWidth());

//            param.setPictureSize(imgSurface.getHeight(), imgSurface.getWidth());

//            bebas provisi adminstrasi dan pelunasan penalti, 9,45%
//            provisi dan admin, 1 tahun 7,99%, 2 tahun 8,15%, 3 tahun 8,45%
//            50% dari gaji

            int orientation = setCameraDisplayOrientation(0);
            Log.d("tag", "surfaceCreated orientation => " + orientation);
//            orientation = 0;
            param.setRotation(orientation);

            camera.setParameters(param);

            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

            if (cameraFlashType == 1) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            } else if (cameraFlashType == 2) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);

                if (camera != null) {
                    Camera.Parameters params = camera.getParameters();

                    if (params != null) {
                        List<String> supportedFlashModes = params.getSupportedFlashModes();

                        if (supportedFlashModes != null) {
                            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                            } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                            }
                        }
                    }
                }
            } else if (cameraFlashType == 3) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        refreshCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        try {
            camera.stopPreview();
            camera.release();
            camera = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

    public int setCameraDisplayOrientation(int cameraId) {

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

        camera.setDisplayOrientation(result);

        return result;

    }

    public void refreshCamera() {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            camera.stopPreview();
            Camera.Parameters param = camera.getParameters();

            if (cameraFacing == 0) {
                if (cameraFlashType == 1) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                } else if (cameraFlashType == 2) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    if (camera != null) {
                        Camera.Parameters params = camera.getParameters();
//                        param.setPreviewSize(imgSurface.getWidth(), imgSurface.getHeight());
//                        param.setPictureSize(imgSurface.getWidth(), imgSurface.getHeight());

                        if (params != null) {
                            List<String> supportedFlashModes = params.getSupportedFlashModes();

                            if (supportedFlashModes != null) {
                                if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                                } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                                }
                            }
                        }
                    }
                } else if (cameraFlashType == 3) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
            }


            refrechCameraPriview(param);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refrechCameraPriview(Camera.Parameters param) {
        try {
            int orientation = setCameraDisplayOrientation(0);
            param.setRotation(orientation);
            camera.setParameters(param);

            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancelSavePicTaskIfNeed() {
        if (savePicTask != null && savePicTask.getStatus() == AsyncTask.Status.RUNNING) {
            savePicTask.cancel(true);
        }
    }

    private class SavePicTask extends AsyncTask<Void, Void, String> {
        private byte[] data;
        private int rotation = 0;

        public SavePicTask(byte[] data, int rotation) {
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
            Log.d("tag", "result  = " + result);

//            activeCameraCapture();

//            tempFile = new File(result);
//
//            new Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    Intent mIntent = new Intent(WhatsappCameraActivity.this, PhotoVideoRedirectActivity.class);
//                    mIntent.putExtra("PATH", tempFile.toString());
//                    mIntent.putExtra("THUMB", tempFile.toString());
//                    mIntent.putExtra("WHO", "Image");
//                    startActivity(mIntent);
//                }
//            }, 100);
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("path", result);
            methodChannel.invokeMethod("onImageCaptured", params);


        }
    }

    public String saveToSDCard(byte[] data, int rotation) throws IOException {
        String imagePath = "";
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);

            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int reqHeight = metrics.heightPixels;
            int reqWidth = metrics.widthPixels;

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
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

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
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
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
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

        myOrientationEventListener = new OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int iAngle) {

                final int iLookup[] = {0, 0, 0, 90, 90, 90, 90, 90, 90, 180, 180, 180, 180, 180, 180, 270, 270, 270, 270, 270, 270, 0, 0, 0}; // 15-degree increments
                if (iAngle != ORIENTATION_UNKNOWN) {

                    int iNewOrientation = iLookup[iAngle / 15];
                    if (iOrientation != iNewOrientation) {
                        iOrientation = iNewOrientation;
                        if (iOrientation == 0) {
                            mOrientation = 90;
                        } else if (iOrientation == 270) {
                            mOrientation = 0;
                        } else if (iOrientation == 90) {
                            mOrientation = 180;
                        }

                    }
                    mPhotoAngle = normalize(iAngle);
                }
//                Camera.Parameters param = camera.getParameters();
//                List<Camera.Size> sizes = param.getSupportedPreviewSizes();
//                //get diff to get perfact preview sizes
//                DisplayMetrics displaymetrics = new DisplayMetrics();
//                activity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
//                int height = displaymetrics.heightPixels;
//                int width = displaymetrics.widthPixels;
//                Log.d("tag", "onOrientationChanged getPreviewSize 0 => " + param.getPreviewSize().width);
//                Log.d("tag", "onOrientationChanged getPreviewSize 1 => " + param.getPreviewSize().height);
//
//                Camera.Size cs = getOptimalPreviewSize(sizes, width, height); //sizes.get(idx);
////            param.setPreviewSize(imgSurface.getHeight(), imgSurface.getWidth());
//                Log.d("tag", "onOrientationChanged cs.getWidth() => " + cs.width);
//                Log.d("tag", "onOrientationChanged cs.getHeight() => " + cs.height);
//                Log.d("tag", "onOrientationChanged imgSurface.getWidth() => " + imgSurface.getWidth());
//                Log.d("tag", "onOrientationChanged imgSurface.getHeight() => " + imgSurface.getHeight());
//                Log.d("tag", "onOrientationChanged mPhotoAngle => " + mPhotoAngle);
////            param.setPictureSize(imgSurface.getHeight(), imgSurface.getWidth());
////            param.setPreviewSize(cs.width, cs.height);
//                if (mPhotoAngle == 90 || mPhotoAngle == 270) {
//                    param.setPictureSize(cs.width, cs.height);
////            param.setPreviewSize(imgSurface.getWidth(), imgSurface.getHeight());
////            param.setPictureSize(imgSurface.getWidth(), imgSurface.getHeight());
//                    param.setPreviewSize(cs.width, imgSurface.getWidth());
//                } else {
//                    param.setPictureSize(cs.height, cs.width);
////            param.setPreviewSize(imgSurface.getWidth(), imgSurface.getHeight());
////            param.setPictureSize(imgSurface.getWidth(), imgSurface.getHeight());
//                    param.setPreviewSize(cs.height, cs.width);
//                }
//                camera.setParameters(param);
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

        if (degrees > 45 && degrees <= 135) {
            return 90;
        }

        if (degrees > 135 && degrees <= 225) {
            return 180;
        }

        if (degrees > 225 && degrees <= 315) {
            return 270;
        }

        throw new RuntimeException("Error....");
    }


    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        Log.d("tag", "maxZoom => " + maxZoom);
        Log.d("tag", "newDist => " + newDist);
        Log.d("tag", "mDist => " + mDist);

        if (Math.abs(newDist - mDist) < 2) return;

        if (newDist > mDist) {
            //zoom in
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < mDist) {
            //zoom out
            if (zoom > 0)
                zoom--;
        }
        mDist = newDist;
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position


        int xxw = imgSurface.getHeight();
        int xxh = imgSurface.getWidth();
        float x = event.getY(pointerIndex);
        float y = xxh - event.getX(pointerIndex);
        /* else {
            x = xxw - event.getY(pointerIndex);
            y = event.getX(pointerIndex) ;
//            x = xxw - event.getY(pointerIndex);
//            y = event.getX(pointerIndex) ;
        } *//*else if (mPhotoAngle == 180) {
            x = xxw - event.getY(pointerIndex);
            y = xxh - event.getX(pointerIndex) ;
        }*/


        //cancel previous actions
        camera.cancelAutoFocus();
//            Rect focusRect = calculateTapArea(event.getX(), event.getY(), 1f);
        Rect meteringRect = calculateTapArea(event.getX(), event.getY(), 1.5f);
//            int pointerId = event.getPointerId(0);
//            int pointerIndex = event.findPointerIndex(pointerId);
//            float x = event.getX(pointerIndex);
//            float y = event.getY(pointerIndex);
//        Log.d("touchRect", "permisi x => " + x);
//        Log.d("touchRect", "permisi y => " + y);

        Rect touchRect = new Rect(
                (int) (x - 100),
                (int) (y - 100),
                (int) (x + 100),
                (int) (y + 100));
        Log.d("touchRect", "lalala => " + touchRect.flattenToString());

        int aboutToBeLeft = touchRect.left;
        int aboutToBeTop = touchRect.top;
        int aboutToBeRight = touchRect.right;
        int aboutToBeBottom = touchRect.bottom;

        if (aboutToBeLeft < 0) {
            aboutToBeLeft = 0;
            aboutToBeRight = 200;
        }
        if (aboutToBeTop < 0) {
            aboutToBeTop = 0;
            aboutToBeBottom = 200;
        }
        if (aboutToBeRight > xxw) {
            aboutToBeRight = xxw;
            aboutToBeLeft = xxw - 200;
        }
        if (aboutToBeBottom > xxh) {
            aboutToBeBottom = xxh;
            aboutToBeTop = xxh - 200;
        }
        Log.d("focusRect", "lalala focusRect before aboutToBeLeft => " + aboutToBeLeft);
        Log.d("focusRect", "lalala focusRect before aboutToBeTop => " + aboutToBeTop);
        Log.d("focusRect", "lalala focusRect before aboutToBeRight => " + aboutToBeRight);
        Log.d("focusRect", "lalala focusRect before aboutToBeBottom => " + aboutToBeBottom);
        Log.d("tag", "lalala focusRect before imgSurface.getWidth() => " + imgSurface.getWidth());
        Log.d("tag", "lalala focusRect before imgSurface.getHeight() => " + imgSurface.getHeight());

        aboutToBeLeft = aboutToBeLeft * 2000 / xxw - 1000;
        aboutToBeTop = aboutToBeTop * 2000 / xxh - 1000;
        aboutToBeRight = aboutToBeRight * 2000 / xxw - 1000;
        aboutToBeBottom = aboutToBeBottom * 2000 / xxh - 1000;
        Log.d("focusRect", "lalala focusRect x => " + x);
        Log.d("focusRect", "lalala focusRect y => " + y);
        Log.d("focusRect", "lalala focusRect aboutToBeLeft => " + aboutToBeLeft);
        Log.d("focusRect", "lalala focusRect aboutToBeTop => " + aboutToBeTop);
        Log.d("focusRect", "lalala focusRect aboutToBeRight => " + aboutToBeRight);
        Log.d("focusRect", "lalala focusRect aboutToBeBottom => " + aboutToBeBottom);
        Log.d("focusRect", "lalala focusRect mPhotoAngle => " + mPhotoAngle);

        Rect focusRect = null;

//        if (mPhotoAngle == 0) {
        focusRect = new Rect(
                aboutToBeLeft,
                aboutToBeTop,
                aboutToBeRight,
                aboutToBeBottom);
//        } else if (mPhotoAngle == 90) {
//            focusRect = new Rect(
//                    aboutToBeBottom,
//                    aboutToBeLeft,
//                    aboutToBeTop,
//                    aboutToBeRight);
//        } else if (mPhotoAngle == 180) {
//            focusRect = new Rect(
//                    aboutToBeRight,
//                    aboutToBeBottom,
//                    aboutToBeLeft,
//                    aboutToBeTop);
//        } else if (mPhotoAngle == 270) {
//            focusRect = new Rect(
//                    aboutToBeTop,
//                    aboutToBeRight,
//                    aboutToBeBottom,
//                    aboutToBeLeft);
//        }

        Camera.Parameters parameters = null;
        try {
            parameters = camera.getParameters();
        } catch (Exception e) {
            Log.e("error", "lalalalalala=> " + e);
        }
        Log.d("focusRect", "lalala focusRect => " + focusRect.flattenToString());

        // check if parameters are set (handle RuntimeException: getParameters failed (empty parameters))
        if (parameters != null) {
            List<Camera.Area> mylist2 = new ArrayList<Camera.Area>();

//                focusRect = new Rect(-1000, -1000, 1000, 1000);

            mylist2.add(new Camera.Area(focusRect, 1000));
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            parameters.setFocusAreas(mylist2);

//                if (meteringAreaSupported) {
//                List<Camera.Area> mylist = new ArrayList<Camera.Area>();
//                mylist.add(new Camera.Area(meteringRect, 1000));
//                    parameters.setMeteringAreas(mylist);
//                }

            try {
                camera.setParameters(parameters);
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Log.d("dede", "onAutoFocus success => " + success);
                    }
                });
            } catch (Exception e) {
                Log.e("error", "lalalalalala=> " + e);
            }
        }

//        List<String> supportedFocusModes = params.getSupportedFocusModes();
//        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
//            camera.autoFocus(new Camera.AutoFocusCallback() {
//                @Override
//                public void onAutoFocus(boolean b, Camera camera) {
//                    // currently set to auto-focus on single touch
//                }
//            });
//        }
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        // Get the pointer's current position
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int left = clamp((int) x - areaSize / 2, 0, imgSurface.getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, imgSurface.getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        matrix.mapRect(rectF);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
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
    public static long gcm(long a, long b) {
        return b == 0 ? a : gcm(b, a % b); // Not bad for one line of code :)
    }

    public static String asFraction(long a, long b) {
        long gcm = gcm(a, b);
        return (a / gcm) + ":" + (b / gcm);
    }
}