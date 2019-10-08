//package com.ric.adv_camera;
//
//import android.Manifest;
//import android.animation.AnimatorSet;
//import android.animation.ObjectAnimator;
//import android.animation.ValueAnimator;
//import android.annotation.SuppressLint;
//import android.app.ProgressDialog;
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.Canvas;
//import android.graphics.Matrix;
//import android.graphics.Rect;
//import android.graphics.RectF;
//import android.hardware.Camera;
//import android.hardware.SensorManager;
//import android.media.CamcorderProfile;
//import android.media.MediaRecorder;
//import android.media.ThumbnailUtils;
//import android.os.AsyncTask;
//import android.os.Build;
//import android.os.Bundle;
//import android.os.Environment;
//import android.os.Handler;
//import android.os.StatFs;
//import android.os.SystemClock;
//import android.support.annotation.NonNull;
//import android.support.v7.app.AppCompatActivity;
//import android.util.DisplayMetrics;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.OrientationEventListener;
//import android.view.Surface;
//import android.view.SurfaceHolder;
//import android.view.SurfaceView;
//import android.view.View;
//import android.view.WindowManager;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.OutputStream;
//import java.util.ArrayList;
//import java.util.List;
//
//public class WhatsappCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {
//
//    private SurfaceHolder surfaceHolder;
//    private Camera camera;
//    private Handler customHandler = new Handler();
//    int flag = 0;
//    private File tempFile = null;
//    private Camera.PictureCallback jpegCallback;
//    int MAX_VIDEO_SIZE_UPLOAD = 25; //MB
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        try {
//            if (myOrientationEventListener != null)
//                myOrientationEventListener.enable();
//        } catch (Exception e1) {
//            e1.printStackTrace();
//        }
//    }
//
//    private File folder = null;
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        if (runTimePermission != null) {
//            runTimePermission.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        }
//
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//    }
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.whatsapp_activity_camera);
//
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//
//        runTimePermission = new RunTimePermission(this);
//        runTimePermission.requestPermission(new String[]{Manifest.permission.CAMERA,
//                Manifest.permission.RECORD_AUDIO,
//                Manifest.permission.READ_EXTERNAL_STORAGE,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE
//        }, new RunTimePermission.RunTimePermissionListener() {
//            @Override
//            public void permissionGranted() {
//                // First we need to check availability of play services
//                initControls();
//
//                identifyOrientationEvents();
//
//                //create a folder to get image
//                folder = new File(Environment.getExternalStorageDirectory() + "/whatsappCamera");
//                if (!folder.exists()) {
//                    folder.mkdirs();
//                }
//
//                //capture image on callback
//                captureImageCallback();
//
//                if (camera != null) {
//                    Camera.CameraInfo info = new Camera.CameraInfo();
//                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//                        imgFlashOnOff.setVisibility(View.GONE);
//                    }
//                }
//            }
//
//            @Override
//            public void permissionDenied() {
//            }
//        });
//
//
//    }
//
//    private void cancelSavePicTaskIfNeed() {
//        if (savePicTask != null && savePicTask.getStatus() == AsyncTask.Status.RUNNING) {
//            savePicTask.cancel(true);
//        }
//    }
//
//    private void cancelSaveVideoTaskIfNeed() {
//        if (saveVideoTask != null && saveVideoTask.getStatus() == AsyncTask.Status.RUNNING) {
//            saveVideoTask.cancel(true);
//        }
//    }
//
//    private SavePicTask savePicTask;
//
//    private class SavePicTask extends AsyncTask<Void, Void, String> {
//        private byte[] data;
//        private int rotation = 0;
//
//        public SavePicTask(byte[] data, int rotation) {
//            this.data = data;
//            this.rotation = rotation;
//        }
//
//        protected void onPreExecute() {
//
//        }
//
//        @Override
//        protected String doInBackground(Void... params) {
//            try {
//                return saveToSDCard(data, rotation);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return null;
//            }
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//
//            activeCameraCapture();
//
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
//
//
//        }
//    }
//
//    public String saveToSDCard(byte[] data, int rotation) throws IOException {
//        String imagePath = "";
//        try {
//            final BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inJustDecodeBounds = true;
//            BitmapFactory.decodeByteArray(data, 0, data.length, options);
//
//            DisplayMetrics metrics = new DisplayMetrics();
//            getWindowManager().getDefaultDisplay().getMetrics(metrics);
//
//            int reqHeight = metrics.heightPixels;
//            int reqWidth = metrics.widthPixels;
//
//            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
//
//            options.inJustDecodeBounds = false;
//            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
//            if (rotation != 0) {
//                Matrix mat = new Matrix();
//                mat.postRotate(rotation);
//                Bitmap bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
//                if (bitmap != bitmap1) {
//                    bitmap.recycle();
//                }
//                imagePath = getSavePhotoLocal(bitmap1);
//                if (bitmap1 != null) {
//                    bitmap1.recycle();
//                }
//            } else {
//                imagePath = getSavePhotoLocal(bitmap);
//                if (bitmap != null) {
//                    bitmap.recycle();
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return imagePath;
//    }
//
//    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
//        final int height = options.outHeight;
//        final int width = options.outWidth;
//        int inSampleSize = 1;
//
//        if (height > reqHeight || width > reqWidth) {
//            if (width > height) {
//                inSampleSize = Math.round((float) height / (float) reqHeight);
//            } else {
//                inSampleSize = Math.round((float) width / (float) reqWidth);
//            }
//        }
//        return inSampleSize;
//    }
//
//    private String getSavePhotoLocal(Bitmap bitmap) {
//        String path = "";
//        try {
//            OutputStream output;
//            File file = new File(folder.getAbsolutePath(), "wc" + System.currentTimeMillis() + ".jpg");
//            try {
//                output = new FileOutputStream(file);
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
//                output.flush();
//                output.close();
//                path = file.getAbsolutePath();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return path;
//    }
//
//    private void captureImageCallback() {
//        surfaceHolder = imgSurface.getHolder();
//        surfaceHolder.addCallback(this);
//        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//        jpegCallback = new Camera.PictureCallback() {
//            public void onPictureTaken(byte[] data, Camera camera) {
//
//                refreshCamera();
//
//                cancelSavePicTaskIfNeed();
//                savePicTask = new SavePicTask(data, getPhotoRotation());
//                savePicTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
//
//            }
//        };
//    }
//
//    private int focusAreaSize = 100;
//    private Matrix matrix = new Matrix();
//
//    private Rect calculateTapArea(float x, float y, float coefficient) {
//        // Get the pointer's current position
//        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
//
//        int left = clamp((int) x - areaSize / 2, 0, imgSurface.getWidth() - areaSize);
//        int top = clamp((int) y - areaSize / 2, 0, imgSurface.getHeight() - areaSize);
//
//        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
//        matrix.mapRect(rectF);
//
//        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
//    }
//
//    private int clamp(int x, int min, int max) {
//        if (x > max) {
//            return max;
//        }
//        if (x < min) {
//            return min;
//        }
//        return x;
//    }
//
//    float mDist;
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//
//        // Get the pointer ID
//        Camera.Parameters params = camera.getParameters();
//        int action = event.getAction();
//        Log.d("tag", "getPointerCount => " + event.getPointerCount());
//        if (event.getPointerCount() > 1) {
//            // handle multi-touch events
//            if (action == MotionEvent.ACTION_POINTER_DOWN) {
//                mDist = getFingerSpacing(event);
//            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
//                camera.cancelAutoFocus();
//                handleZoom(event, params);
//            }
//        } else {
//            // handle single touch events
//            if (action == MotionEvent.ACTION_UP) {
//                handleFocus(event, params);
//            }
//        }
//        return true;
//    }
//
//
//    private void handleZoom(MotionEvent event, Camera.Parameters params) {
//        int maxZoom = params.getMaxZoom();
//        int zoom = params.getZoom();
//        float newDist = getFingerSpacing(event);
//        Log.d("tag", "maxZoom => " + maxZoom);
//        Log.d("tag", "newDist => " + newDist);
//        Log.d("tag", "mDist => " + mDist);
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
//        mDist = newDist;
//        params.setZoom(zoom);
//        camera.setParameters(params);
//    }
//
//    public void handleFocus(MotionEvent event, Camera.Parameters params) {
//        int pointerId = event.getPointerId(0);
//        int pointerIndex = event.findPointerIndex(pointerId);
//        // Get the pointer's current position
//        float x = event.getX(pointerIndex);
//        float y = event.getY(pointerIndex);
//
//
//            //cancel previous actions
//            camera.cancelAutoFocus();
////            Rect focusRect = calculateTapArea(event.getX(), event.getY(), 1f);
//            Rect meteringRect = calculateTapArea(event.getX(), event.getY(), 1.5f);
////            int pointerId = event.getPointerId(0);
////            int pointerIndex = event.findPointerIndex(pointerId);
////            float x = event.getX(pointerIndex);
////            float y = event.getY(pointerIndex);
//
//            Rect touchRect = new Rect(
//                    (int) (x - 100),
//                    (int) (y - 100),
//                    (int) (x + 100),
//                    (int) (y + 100));
//            Log.d("touchRect", "lalala => " + touchRect.flattenToString());
//
//            int aboutToBeLeft = touchRect.left;
//            int aboutToBeTop = touchRect.top;
//            int aboutToBeRight = touchRect.right;
//            int aboutToBeBottom = touchRect.bottom;
//
//            if (aboutToBeLeft < 0) {
//                aboutToBeLeft = 0;
//                aboutToBeRight = 200;
//            }
//            if (aboutToBeTop < 0) {
//                aboutToBeTop = 0;
//                aboutToBeBottom = 200;
//            }
//            if (aboutToBeRight > imgSurface.getWidth()) {
//                aboutToBeRight = imgSurface.getWidth();
//                aboutToBeLeft = imgSurface.getWidth() - 200;
//            }
//            if (aboutToBeBottom > imgSurface.getHeight()) {
//                aboutToBeBottom = imgSurface.getHeight();
//                aboutToBeTop = imgSurface.getHeight() - 200;
//            }
//            Log.d("tag", "aboutToBeLeft => " + aboutToBeLeft);
//            Log.d("tag", "aboutToBeTop => " + aboutToBeTop);
//            Log.d("tag", "aboutToBeRight => " + aboutToBeRight);
//            Log.d("tag", "aboutToBeBottom => " + aboutToBeBottom);
//            Log.d("tag", "imgSurface.getWidth() => " + imgSurface.getWidth());
//            Log.d("tag", "imgSurface.getHeight() => " + imgSurface.getHeight());
//            aboutToBeLeft = aboutToBeLeft * 2000 / imgSurface.getWidth() - 1000;
//            aboutToBeTop = aboutToBeTop * 2000 / imgSurface.getHeight() - 1000;
//            aboutToBeRight = aboutToBeRight * 2000 / imgSurface.getWidth() - 1000;
//            aboutToBeBottom = aboutToBeBottom * 2000 / imgSurface.getHeight() - 1000;
//
//            final Rect focusRect = new Rect(
//                    aboutToBeLeft,
//                    aboutToBeTop,
//                    aboutToBeRight,
//                    aboutToBeBottom);
//
//            Camera.Parameters parameters = null;
//            try {
//                parameters = camera.getParameters();
//            } catch (Exception e) {
//                Log.e("error", "lalalalalala=> " + e);
//            }
//            Log.d("focusRect", "lalala => " + focusRect.flattenToString());
//            Log.d("focusRect", "imgSurface.getWidth() => " + imgSurface.getWidth());
//            Log.d("focusRect", "imgSurface.getHeight() => " + imgSurface.getHeight());
//            Log.d("focusRect", "getPreviewSize width => " + parameters.getPreviewSize().width);
//            Log.d("focusRect", "getPreviewSize height => " + parameters.getPreviewSize().height);
//
//            // check if parameters are set (handle RuntimeException: getParameters failed (empty parameters))
//            if (parameters != null) {
//                List<Camera.Area> mylist2 = new ArrayList<Camera.Area>();
//
////                focusRect = new Rect(-1000, -1000, 1000, 1000);
//
//                mylist2.add(new Camera.Area(focusRect, 1000));
//                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//                parameters.setFocusAreas(mylist2);
//
////                if (meteringAreaSupported) {
////                List<Camera.Area> mylist = new ArrayList<Camera.Area>();
////                mylist.add(new Camera.Area(meteringRect, 1000));
////                    parameters.setMeteringAreas(mylist);
////                }
//
//                try {
//                    camera.setParameters(parameters);
//                    camera.autoFocus(new Camera.AutoFocusCallback() {
//                        @Override
//                        public void onAutoFocus(boolean success, Camera camera) {
//                            Log.d("dede", "onAutoFocus success => " + success);
//                        }
//                    });
//                } catch (Exception e) {
//                    Log.e("error", "lalalalalala=> " + e);
//                }
//            }
//
////        List<String> supportedFocusModes = params.getSupportedFocusModes();
////        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
////            camera.autoFocus(new Camera.AutoFocusCallback() {
////                @Override
////                public void onAutoFocus(boolean b, Camera camera) {
////                    // currently set to auto-focus on single touch
////                }
////            });
////        }
//    }
//
//    /**
//     * Determine the space between the first two fingers
//     */
//    private float getFingerSpacing(MotionEvent event) {
//        // ...
//        float x = event.getX(0) - event.getX(1);
//        float y = event.getY(0) - event.getY(1);
//        return (float) Math.sqrt(x * x + y * y);
//    }
//
//    private class SaveVideoTask extends AsyncTask<Void, Void, Void> {
//
//        File thumbFilename;
//
//        ProgressDialog progressDialog = null;
//
//        @Override
//        protected void onPreExecute() {
//            progressDialog = new ProgressDialog(WhatsappCameraActivity.this);
//            progressDialog.setMessage("Processing a video...");
//            progressDialog.show();
//            super.onPreExecute();
//            imgCapture.setOnTouchListener(null);
//            textCounter.setVisibility(View.GONE);
//            imgSwipeCamera.setVisibility(View.VISIBLE);
//            imgFlashOnOff.setVisibility(View.VISIBLE);
//
//        }
//
//        @Override
//        protected Void doInBackground(Void... params) {
//            try {
//                try {
//                    myOrientationEventListener.enable();
//
//                    customHandler.removeCallbacksAndMessages(null);
//
//                    mediaRecorder.stop();
//                    releaseMediaRecorder();
//
//                    tempFile = new File(folder.getAbsolutePath() + "/" + mediaFileName + ".mp4");
//                    thumbFilename = new File(folder.getAbsolutePath(), "t_" + mediaFileName + ".jpeg");
//                    generateVideoThmb(tempFile.getPath(), thumbFilename);
//
//
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//            } catch (Exception e) {
//                e.printStackTrace();
//
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(Void aVoid) {
//            super.onPostExecute(aVoid);
//
//            if (progressDialog != null) {
//                if (progressDialog.isShowing()) {
//                    progressDialog.dismiss();
//                }
//            }
//            if (tempFile != null && thumbFilename != null)
//                onVideoSendDialog(tempFile.getAbsolutePath(), thumbFilename.getAbsolutePath());
//        }
//    }
//
//    private int mPhotoAngle = 90;
//
//    private void identifyOrientationEvents() {
//
//        myOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
//            @Override
//            public void onOrientationChanged(int iAngle) {
//
//                final int iLookup[] = {0, 0, 0, 90, 90, 90, 90, 90, 90, 180, 180, 180, 180, 180, 180, 270, 270, 270, 270, 270, 270, 0, 0, 0}; // 15-degree increments
//                if (iAngle != ORIENTATION_UNKNOWN) {
//
//                    int iNewOrientation = iLookup[iAngle / 15];
//                    if (iOrientation != iNewOrientation) {
//                        iOrientation = iNewOrientation;
//                        if (iOrientation == 0) {
//                            mOrientation = 90;
//                        } else if (iOrientation == 270) {
//                            mOrientation = 0;
//                        } else if (iOrientation == 90) {
//                            mOrientation = 180;
//                        }
//
//                    }
//                    mPhotoAngle = normalize(iAngle);
//                }
//            }
//        };
//
//        if (myOrientationEventListener.canDetectOrientation()) {
//            myOrientationEventListener.enable();
//        }
//
//    }
//
//    private MediaRecorder mediaRecorder;
//    private SurfaceView imgSurface;
//    private ImageView imgCapture;
//    private ImageView imgFlashOnOff;
//    private ImageView imgSwipeCamera;
//    private RunTimePermission runTimePermission;
//    private TextView textCounter;
//    private TextView hintTextView;
//
//    private void initControls() {
//        mediaRecorder = new MediaRecorder();
//
//        imgSurface = findViewById(R.id.imgSurface);
//        textCounter = findViewById(R.id.textCounter);
//        imgCapture = findViewById(R.id.imgCapture);
//        imgFlashOnOff = findViewById(R.id.imgFlashOnOff);
//        imgSwipeCamera = findViewById(R.id.imgChangeCamera);
//        textCounter.setVisibility(View.GONE);
//        imgSurface.setFocusable(true);
//        imgSurface.setFocusableInTouchMode(true);
//
//        hintTextView = findViewById(R.id.hintTextView);
//
////        imgSwipeCamera.setOnClickListener(this);
//        activeCameraCapture();
//
//        imgFlashOnOff.setOnClickListener(this);
//
////        imgFlashOnOff.setVisibility(View.GONE);
////        imgSwipeCamera.setVisibility(View.GONE);
////        imgCapture.setVisibility(View.GONE);
////        textCounter.setVisibility(View.GONE);
//
//
//    }
//
//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        cancelSavePicTaskIfNeed();
//    }
//
//    @Override
//    public void onClick(View v) {
//        if (v.getId() == R.id.imgFlashOnOff) {
//            flashToggle();
//        } else if (v.getId() == R.id.imgChangeCamera) {
//            camera.stopPreview();
//            camera.release();
//            if (flag == 0) {
//                imgFlashOnOff.setVisibility(View.GONE);
//                flag = 1;
//            } else {
//                imgFlashOnOff.setVisibility(View.VISIBLE);
//                flag = 0;
//            }
//            surfaceCreated(surfaceHolder);
//        }
//    }
//
//    private void flashToggle() {
//
//        if (flashType == 1) {
//
//            flashType = 2;
//        } else if (flashType == 2) {
//
//            flashType = 3;
//        } else if (flashType == 3) {
//
//            flashType = 1;
//        }
//        refreshCamera();
//    }
//
//    private void captureImage() {
//        camera.takePicture(null, null, jpegCallback);
//        inActiveCameraCapture();
//    }
//
//    private void releaseMediaRecorder() {
//        if (mediaRecorder != null) {
//            mediaRecorder.reset();   // clear recorder configuration
//            mediaRecorder.release(); // release the recorder object
//            mediaRecorder = new MediaRecorder();
//        }
//    }
//
//
//    public void refreshCamera() {
//        if (surfaceHolder.getSurface() == null) {
//            return;
//        }
//        try {
//            camera.stopPreview();
//            Camera.Parameters param = camera.getParameters();
//
//            if (flag == 0) {
//                if (flashType == 1) {
//                    param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
//                    imgFlashOnOff.setImageResource(R.drawable.ic_flash_auto);
//                } else if (flashType == 2) {
//                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//                    if (camera != null) {
//                        Camera.Parameters params = camera.getParameters();
//
//                        if (params != null) {
//                            List<String> supportedFlashModes = params.getSupportedFlashModes();
//
//                            if (supportedFlashModes != null) {
//                                if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
//                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//                                } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
//                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//                                }
//                            }
//                        }
//                    }
//                    imgFlashOnOff.setImageResource(R.drawable.ic_flash_on);
//                } else if (flashType == 3) {
//                    param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
//                    imgFlashOnOff.setImageResource(R.drawable.ic_flash_off);
//                }
//            }
//
//
//            refrechCameraPriview(param);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void refrechCameraPriview(Camera.Parameters param) {
//        try {
//            int orientation = setCameraDisplayOrientation(0);
//            param.setRotation(orientation);
//            camera.setParameters(param);
//
//            camera.setPreviewDisplay(surfaceHolder);
//            camera.startPreview();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public int setCameraDisplayOrientation(int cameraId) {
//
//        Camera.CameraInfo info = new Camera.CameraInfo();
//        Camera.getCameraInfo(cameraId, info);
//
//        int rotation = getWindowManager().getDefaultDisplay().getRotation();
//
//        if (Build.MODEL.equalsIgnoreCase("Nexus 6") && flag == 1) {
//            rotation = Surface.ROTATION_180;
//        }
//        int degrees = 0;
//        switch (rotation) {
//
//            case Surface.ROTATION_0:
//
//                degrees = 0;
//                break;
//
//            case Surface.ROTATION_90:
//
//                degrees = 90;
//                break;
//
//            case Surface.ROTATION_180:
//
//                degrees = 180;
//                break;
//
//            case Surface.ROTATION_270:
//
//                degrees = 270;
//                break;
//
//        }
//
//        int result;
//
//        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//
//            result = (info.orientation + degrees) % 360;
//            result = (360 - result) % 360; // compensate the mirror
//
//        } else {
//            result = (info.orientation - degrees + 360) % 360;
//
//        }
//
//        camera.setDisplayOrientation(result);
//
//        return result;
//
//    }
//
//    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
//        final double ASPECT_TOLERANCE = 0.1;
//        double targetRatio=(double)h / w;
//
//        if (sizes == null) return null;
//
//        Camera.Size optimalSize = null;
//        double minDiff = Double.MAX_VALUE;
//
//        for (Camera.Size size : sizes) {
//            double ratio = (double) size.width / size.height;
//            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
//            if (Math.abs(size.height - h) < minDiff) {
//                optimalSize = size;
//                minDiff = Math.abs(size.height - h);
//            }
//        }
//
//        if (optimalSize == null) {
//            minDiff = Double.MAX_VALUE;
//            for (Camera.Size size : sizes) {
//                if (Math.abs(size.height - h) < minDiff) {
//                    optimalSize = size;
//                    minDiff = Math.abs(size.height - h);
//                }
//            }
//        }
//        return optimalSize;
//    }
//
//    //------------------SURFACE CREATED FIRST TIME--------------------//
//
//    int flashType = 1;
//
//    @Override
//    public void surfaceCreated(SurfaceHolder arg0) {
//        try {
//            if (flag == 0) {
//                camera = Camera.open(0);
//            } else {
//                camera = Camera.open(1);
//            }
//        } catch (RuntimeException e) {
//            e.printStackTrace();
//            return;
//        }
//
//        try {
//            Camera.Parameters param;
//            param = camera.getParameters();
//            List<Camera.Size> sizes = param.getSupportedPreviewSizes();
//            //get diff to get perfact preview sizes
//            DisplayMetrics displaymetrics = new DisplayMetrics();
//            getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
//            int height = displaymetrics.heightPixels;
//            int width = displaymetrics.widthPixels;
//
//            Camera.Size cs = getOptimalPreviewSize(sizes, width, height); //sizes.get(idx);
//            param.setPreviewSize(cs.width, cs.height);
//            param.setPictureSize(cs.width, cs.height);
//
//            int orientation = setCameraDisplayOrientation(0);
//
//            param.setRotation(orientation);
//
//            camera.setParameters(param);
//
//            camera.setPreviewDisplay(surfaceHolder);
//            camera.startPreview();
//
//            if (flashType == 1) {
//                param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
//                imgFlashOnOff.setImageResource(R.drawable.ic_flash_auto);
//            } else if (flashType == 2) {
//                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//
//                if (camera != null) {
//                    Camera.Parameters params =  camera.getParameters();
//
//                    if (params != null) {
//                        List<String> supportedFlashModes = params.getSupportedFlashModes();
//
//                        if (supportedFlashModes != null) {
//                            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
//                                param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
//                            } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
//                                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
//                            }
//                        }
//                    }
//                }
//
//                imgFlashOnOff.setImageResource(R.drawable.ic_flash_on);
//            } else if (flashType == 3) {
//                param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
//                imgFlashOnOff.setImageResource(R.drawable.ic_flash_off);
//            }
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void surfaceDestroyed(SurfaceHolder arg0) {
//        try {
//            camera.stopPreview();
//            camera.release();
//            camera = null;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void surfaceChanged(SurfaceHolder surfaceHolder, int i22, int i1, int i2) {
//        refreshCamera();
//    }
//
//    //------------------SURFACE OVERRIDE METHIDS END--------------------//
//
//    private long startTime = SystemClock.uptimeMillis();
//    private Runnable updateTimerThread = new Runnable() {
//
//        public void run() {
//
//            long timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
//            long timeSwapBuff = 0L;
//            long updatedTime = timeSwapBuff + timeInMilliseconds;
//
//            int secs = (int) (updatedTime / 1000);
//            int mins = secs / 60;
//
//            secs = secs % 60;
//            textCounter.setText(String.format("%02d", mins) + ":" + String.format("%02d", secs));
//            customHandler.postDelayed(this, 0);
//
//        }
//
//    };
//
//    private void scaleUpAnimation() {
//        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(imgCapture, "scaleX", 2f);
//        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(imgCapture, "scaleY", 2f);
//        scaleDownX.setDuration(100);
//        scaleDownY.setDuration(100);
//        AnimatorSet scaleDown = new AnimatorSet();
//        scaleDown.play(scaleDownX).with(scaleDownY);
//
//        scaleDownX.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//            @Override
//            public void onAnimationUpdate(ValueAnimator valueAnimator) {
//                View p = (View) imgCapture.getParent();
//                p.invalidate();
//            }
//        });
//        scaleDown.start();
//    }
//
//    private void scaleDownAnimation() {
//        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(imgCapture, "scaleX", 1f);
//        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(imgCapture, "scaleY", 1f);
//        scaleDownX.setDuration(100);
//        scaleDownY.setDuration(100);
//        AnimatorSet scaleDown = new AnimatorSet();
//        scaleDown.play(scaleDownX).with(scaleDownY);
//
//        scaleDownX.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//            @Override
//            public void onAnimationUpdate(ValueAnimator valueAnimator) {
//
//                View p = (View) imgCapture.getParent();
//                p.invalidate();
//            }
//        });
//        scaleDown.start();
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//
//        try {
//            if (customHandler != null)
//                customHandler.removeCallbacksAndMessages(null);
//
//            releaseMediaRecorder();       // if you are using MediaRecorder, release it first
//
//            if (myOrientationEventListener != null)
//                myOrientationEventListener.enable();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private SaveVideoTask saveVideoTask = null;
//
//    private void activeCameraCapture() {
//        if (imgCapture != null) {
//            imgCapture.setAlpha(1.0f);
//            imgCapture.setOnLongClickListener(new View.OnLongClickListener() {
//                @Override
//                public boolean onLongClick(View v) {
//                    hintTextView.setVisibility(View.INVISIBLE);
//                    try {
//                        if (prepareMediaRecorder()) {
//                            myOrientationEventListener.disable();
//                            Thread.sleep(1000);
//                            mediaRecorder.start();
//                            startTime = SystemClock.uptimeMillis();
//                            customHandler.postDelayed(updateTimerThread, 0);
//                        } else {
//                            return false;
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    textCounter.setVisibility(View.VISIBLE);
//                    imgSwipeCamera.setVisibility(View.GONE);
//                    imgFlashOnOff.setVisibility(View.GONE);
//                    scaleUpAnimation();
//                    imgCapture.setOnTouchListener(new View.OnTouchListener() {
//                        @Override
//                        public boolean onTouch(View v, MotionEvent event) {
//                            if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS) {
//                                return true;
//                            }
//                            if (event.getAction() == MotionEvent.ACTION_UP) {
//
//                                scaleDownAnimation();
//                                hintTextView.setVisibility(View.VISIBLE);
//
//                                cancelSaveVideoTaskIfNeed();
//                                saveVideoTask = new SaveVideoTask();
//                                saveVideoTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
//
//                                return true;
//                            }
//                            return true;
//
//                        }
//                    });
//                    return true;
//                }
//
//            });
//            imgCapture.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//
//                    if (isSpaceAvailable()) {
//                        captureImage();
//                    } else {
//                        Toast.makeText(WhatsappCameraActivity.this, "Memory is not available", Toast.LENGTH_SHORT).show();
//                    }
//                }
//            });
//        }
//
//    }
//
//    public void onVideoSendDialog(final String videopath, final String thumbPath) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                if (videopath != null) {
//                    File fileVideo = new File(videopath);
//                    long fileSizeInBytes = fileVideo.length();
//                    long fileSizeInKB = fileSizeInBytes / 1024;
//                    long fileSizeInMB = fileSizeInKB / 1024;
//                    if (fileSizeInMB > MAX_VIDEO_SIZE_UPLOAD) {
////                        new android.support.v7.app.AlertDialog.Builder(WhatsappCameraActivity.this)
////                                .setMessage(getString(R.string.file_limit_size_upload_format, MAX_VIDEO_SIZE_UPLOAD))
////                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
////                                    @Override
////                                    public void onClick(DialogInterface dialog, int which) {
////                                        dialog.dismiss();
////                                    }
////                                })
////                                .show();
//                    } else {
//
//                        Intent mIntent = new Intent(WhatsappCameraActivity.this, PhotoVideoRedirectActivity.class);
//                        mIntent.putExtra("PATH", videopath.toString());
//                        mIntent.putExtra("THUMB", thumbPath.toString());
//                        mIntent.putExtra("WHO", "Video");
//                        startActivity(mIntent);
//
//                        //SendVideoDialog sendVideoDialog = SendVideoDialog.newInstance(videopath, thumbPath, name, phoneNuber);
//                        // sendVideoDialog.show(getSupportFragmentManager(), "SendVideoDialog");
//                    }
//                }
//            }
//        });
//    }
//
//    private void inActiveCameraCapture() {
//        if (imgCapture != null) {
//            imgCapture.setAlpha(0.5f);
//            imgCapture.setOnClickListener(null);
//        }
//    }
//
//    //--------------------------CHECK FOR MEMORY -----------------------------//
//
//    public int getFreeSpacePercantage() {
//        int percantage = (int) (freeMemory() * 100 / totalMemory());
//        int modValue = percantage % 5;
//        return percantage - modValue;
//    }
//
//    public double totalMemory() {
//        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
//        double sdAvailSize = (double) stat.getBlockCount() * (double) stat.getBlockSize();
//        return sdAvailSize / 1073741824;
//    }
//
//    public double freeMemory() {
//        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
//        double sdAvailSize = (double) stat.getAvailableBlocks() * (double) stat.getBlockSize();
//        return sdAvailSize / 1073741824;
//    }
//
//    public boolean isSpaceAvailable() {
//        if (getFreeSpacePercantage() >= 1) {
//            return true;
//        } else {
//            return false;
//        }
//    }
//    //-------------------END METHODS OF CHECK MEMORY--------------------------//
//
//
//    private String mediaFileName = null;
//
//    @SuppressLint("SimpleDateFormat")
//    protected boolean prepareMediaRecorder() throws IOException {
//
//        mediaRecorder = new MediaRecorder(); // Works well
//
//        mediaRecorder.setCamera(camera);
//        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        if (flag == 1) {
//            mediaRecorder.setProfile(CamcorderProfile.get(1, CamcorderProfile.QUALITY_HIGH));
//        } else {
//            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
//        }
//        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
//
//        mediaRecorder.setOrientationHint(mOrientation);
//
//        if (Build.MODEL.equalsIgnoreCase("Nexus 6") && flag == 1) {
//
//            if (mOrientation == 90) {
//                mediaRecorder.setOrientationHint(mOrientation);
//            } else if (mOrientation == 180) {
//                mediaRecorder.setOrientationHint(0);
//            } else {
//                mediaRecorder.setOrientationHint(180);
//            }
//
//        } else if (mOrientation == 90 && flag == 1) {
//            mediaRecorder.setOrientationHint(270);
//        } else if (flag == 1) {
//            mediaRecorder.setOrientationHint(mOrientation);
//        }
//        mediaFileName = "wc_vid_" + System.currentTimeMillis();
//        mediaRecorder.setOutputFile(folder.getAbsolutePath() + "/" + mediaFileName + ".mp4"); // Environment.getExternalStorageDirectory()
//
//        mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
//
//            public void onInfo(MediaRecorder mr, int what, int extra) {
//                // TODO Auto-generated method stub
//
//                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
//
//                    long downTime = 0;
//                    long eventTime = 0;
//                    float x = 0.0f;
//                    float y = 0.0f;
//                    int metaState = 0;
//                    MotionEvent motionEvent = MotionEvent.obtain(
//                            downTime,
//                            eventTime,
//                            MotionEvent.ACTION_UP,
//                            0,
//                            0,
//                            metaState
//                    );
//
//                    imgCapture.dispatchTouchEvent(motionEvent);
//
//                    Toast.makeText(WhatsappCameraActivity.this, "You reached to Maximum(25MB) video size.", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//
//        mediaRecorder.setMaxFileSize(1000 * 25 * 1000);
//
//        try {
//            mediaRecorder.prepare();
//        } catch (Exception e) {
//            releaseMediaRecorder();
//            e.printStackTrace();
//            return false;
//        }
//        return true;
//
//    }
//
//    OrientationEventListener myOrientationEventListener;
//    int iOrientation = 0;
//    int mOrientation = 90;
//
//    public void generateVideoThmb(String srcFilePath, File destFile) {
//        try {
//            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(srcFilePath, 120);
//            FileOutputStream out = new FileOutputStream(destFile);
//            ThumbnailUtils.extractThumbnail(bitmap, 200, 200).compress(Bitmap.CompressFormat.JPEG, 100, out);
//            out.flush();
//            out.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//    }
//
//    private int normalize(int degrees) {
//        if (degrees > 315 || degrees <= 45) {
//            return 0;
//        }
//
//        if (degrees > 45 && degrees <= 135) {
//            return 90;
//        }
//
//        if (degrees > 135 && degrees <= 225) {
//            return 180;
//        }
//
//        if (degrees > 225 && degrees <= 315) {
//            return 270;
//        }
//
//        throw new RuntimeException("Error....");
//    }
//
//    private int getPhotoRotation() {
//        int rotation;
//        int orientation = mPhotoAngle;
//
//        Camera.CameraInfo info = new Camera.CameraInfo();
//        if (flag == 0) {
//            Camera.getCameraInfo(0, info);
//        } else {
//            Camera.getCameraInfo(1, info);
//        }
//
//        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//            rotation = (info.orientation - orientation + 360) % 360;
//        } else {
//            rotation = (info.orientation + orientation) % 360;
//        }
//        return rotation;
//    }
//}
