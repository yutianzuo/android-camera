package com.github.yutianzuo.camerademo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
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
import android.media.MediaActionSound;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * yutianzuo
 * Camera2 APIDemo, preview and take a picture(jpeg)
 * some key points commented in Chinese
 *
 * 本文件展示了camera2系列api的使用，功能包括preview和自定义拍照，可以旋转摄像头和操作闪关灯等最基本操作
 * 注释标注了一些可以扩展的地方，如果想实现更复杂的功能，可以参考注释
 */
public class CustomCamera2 extends AppCompatActivity {
    private final int REQUEST_CAMERA_PERMISSION = 0;

    private TextureView mTextureView;
    private ImageButton mCaptureBtn;

    private ImageReader mImageReader;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private Size mPreviewSize;
    private int mWidth;
    private int mHeight;
    private MediaActionSound mMediaActionSound;

    private void initShotClickSound() {
        mMediaActionSound = new MediaActionSound();
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }
    };

    //性能如果不好，用后台线程关联的handler去传入各种session调用所需要的handler参数，目前都传入的null
//    private HandlerThread mBackgroundThread;
//    private Handler mBackgroundHandler;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mBuilder;
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

            //如果需要对每一帧数据处理可以在这里触发
//            mSession = session;
//            takePicture();
        }
    };

    boolean mFirstAvailable = false;
    boolean mFlashOn = false;
    boolean mFacingCamera = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_camera2_preview);
        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        mTextureView = (TextureView) findViewById(R.id.camera_texture_view);
        mCaptureBtn = (ImageButton) findViewById(R.id.capture_btn);
        mCaptureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        ImageButton btnFlash = (ImageButton) findViewById(R.id.flash_btn);
        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mFlashOn) {
                    mBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                } else {
                    mBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                }
                try {
                    mSession.setRepeatingRequest(mBuilder.build(), mSessionCaptureCallback, null);//mBackgroundHandler);
                } catch (Throwable e) {

                }
                mFlashOn = !mFlashOn;
            }
        });

        ImageButton btnSwitch = (ImageButton) findViewById(R.id.switch_btn);
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
                mFacingCamera = !mFacingCamera;
                getCameraId(mFacingCamera);
                openCamera();
            }
        });

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                if (!mFirstAvailable) {
                    mWidth = width;
                    mHeight = height;
                    getCameraId(mFacingCamera);
                    openCamera();
                    mFirstAvailable = true;
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        });

        initShotClickSound();
    }

    private void initImageReader(int width, int height, int format) {
        mImageReader = ImageReader.newInstance(width, height, format, 2);
        if (format == ImageFormat.JPEG) {
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    //should run in workthread 正式工程需要在工作线程中处理
                    Log.e("ytz", "");
                    Image image = reader.acquireNextImage();

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(new File("/sdcard/" + System.currentTimeMillis() + ".jpeg"));
                        output.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        image.close();
                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }, null);
        } else if (format == ImageFormat.NV21) { //处理原生YUV420SP数据，如需要可以在这里加

        }
    }

    private void takePicture() {
        if (mCameraDevice == null) return;
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 闪光灯
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            // 获取手机方向
            //int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            int deviceOrientation = getWindowManager().getDefaultDisplay().getOrientation();
            int totalRotation = sensorToDeviceRotation(characteristics, deviceOrientation);

            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, totalRotation);
            //拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mSession.capture(mCaptureRequest, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                    super.onCaptureProgressed(session, request, partialResult);
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                }

                @Override
                public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
                    super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                }

                @Override
                public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
                    super.onCaptureSequenceAborted(session, sequenceId);
                }

                @Override
                public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
                    super.onCaptureBufferLost(session, request, target, frameNumber);
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }


        mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);

    }

    private void getCameraId(boolean bFaceOrBack) {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (bFaceOrBack) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        mCameraId = cameraId;
                        return;
                    }
                } else {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        mCameraId = cameraId;
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private Size getPreferredPreviewSize(Size[] sizes, int width, int height, float ratio) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : sizes) {
            if (width > height) {
                if (option.getWidth() >= width && option.getHeight() >= height
                        && Float.compare(((float)height / (float)width), ratio) == 0) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getHeight() >= width && option.getWidth() >= height
                        && Float.compare(((float)width / (float)height), ratio) == 0) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size s1, Size s2) {
                    return Long.signum(s1.getWidth() * s1.getHeight() - s2.getWidth() * s2.getHeight());
                }
            });
        }
        return sizes[0];
    }

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("ytz", "Lacking privileges to access camera service, please request permission first.");
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CAMERA_PERMISSION);
        }

        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, null);
//            startBackgroundThread();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    public void startBackgroundThread() {
//        mBackgroundThread = new HandlerThread("Camera Background");
//        mBackgroundThread.start();
//        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
//    }
//
//    public void stopBackgroundThread() {
//        if (mBackgroundThread != null) {
//            mBackgroundThread.quitSafely();
//            try {
//                mBackgroundThread.join();
//                mBackgroundThread = null;
//                mBackgroundHandler = null;
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    private void createCameraPreview() {
        try {

            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int deviceOrientation = getWindowManager().getDefaultDisplay().getOrientation();
            int totalRotation = sensorToDeviceRotation(characteristics, deviceOrientation);
            boolean swapRotation = totalRotation == 90 || totalRotation == 270;
            int rotatedWidth = mWidth;
            int rotatedHeight = mHeight;
            if (swapRotation) {
                rotatedWidth = mHeight;
                rotatedHeight = mWidth;
            }
            //选取一个合适的相机分辨率（根据显示控件的尺寸）
            mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedWidth, rotatedHeight, /*16:9*/0.5625f);

//            Size[] jpgSize = map.getOutputSizes(ImageFormat.JPEG);

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            //这里texture的width和height设置应该和相机尺寸相同，即是跟控件方向相差90度的。
            texture.setDefaultBufferSize(Math.max(mPreviewSize.getHeight(), mPreviewSize.getWidth()),
                    Math.min(mPreviewSize.getHeight(), mPreviewSize.getWidth()));
//            int previewWidth = mTextureView.getWidth();
//            int previewHeight = mTextureView.getHeight();
//            texture.setDefaultBufferSize(1920, 1080);

            //preview 和 输出到imageReader的尺寸是可以不同的;
            //这里的尺寸设置跟texture一样，跟相机一样
            initImageReader(Math.max(mPreviewSize.getHeight(), mPreviewSize.getWidth()),
                    Math.min(mPreviewSize.getHeight(), mPreviewSize.getWidth()), ImageFormat.JPEG);

            Log.e("ytz", "OptimalSize width: " + mPreviewSize.getWidth() + " height: " + mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            mBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mBuilder.addTarget(surface);
//            mBuilder.addTarget(mImageReader.getSurface()); //这里也可以作为触发摄像头每一帧数据回调开关
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    //Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        return;
                    }
                    mSession = cameraCaptureSession;
                    mBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        mSession.setRepeatingRequest(mBuilder.build(), mSessionCaptureCallback, null);//mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(CustomCamera2.this, "Camera configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics characteristics, int deviceOrientation) {
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length <= 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission denied
                Toast.makeText(this, "User denied permission, can't use take photo feature.",
                        Toast.LENGTH_SHORT).show();
            } else {

            }
        }
    }

    private void closeCamera() {
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mFirstAvailable) {
            getCameraId(mFacingCamera);
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
//        stopBackgroundThread();
    }


}
