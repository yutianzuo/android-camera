package com.github.yutianzuo.camerademo;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.yutianzuo.camerademo.help.CameraHelper;
import com.github.yutianzuo.camerademo.help.YuvToRGB;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * yutianzuo Demo for Camera API version1 (OS version below 5.1)
 */
public class CustomCamera1 extends AppCompatActivity implements TextureView.SurfaceTextureListener,
        Camera.PreviewCallback {
    public static final boolean bAutoFocus;

    static {
        if (((Build.MODEL.contains("GT-I9505")) || (Build.MODEL.contains("GT-I9506")) ||
                (Build.MODEL.contains("GT-I9500")) || (Build.MODEL.contains("SGH-I337")) ||
                (Build.MODEL.contains("SGH-M919")) || (Build.MODEL.contains("SCH-I545")) ||
                (Build.MODEL.contains("SPH-L720")) || (Build.MODEL.contains("GT-I9508")) ||
                (Build.MODEL.contains("SHV-E300")) || (Build.MODEL.contains("SCH-R970")) ||
                (Build.MODEL.contains("SM-N900")) || (Build.MODEL.contains("LG-D801"))) &&
                (!Build.MODEL.contains("SM-N9008"))) {
            bAutoFocus = true;
        } else {
            bAutoFocus = false;
        }
    }

    private DisplayMetrics mDm;
    private TextureView mTextureView;
    private CameraHelper mCameraHelper;
    private boolean mFirstAvailable = false;
    private boolean mFlashOn = false;
    private boolean mFacingCamera = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_preview);
        mDm = getResources().getDisplayMetrics();
        mTextureView = (TextureView) findViewById(R.id.camera_texture_view);
        mTextureView.setSurfaceTextureListener(this);
        mCameraHelper = new CameraHelper();


        ImageButton captureBtn = (ImageButton) findViewById(R.id.capture_btn);
        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        ImageButton btnFlash = (ImageButton) findViewById(R.id.flash_btn);
        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Camera.Parameters parameters = mCamera.getParameters();
                    if (!mFlashOn) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    } else {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    }
                    mCamera.setParameters(parameters);
                    mFlashOn = !mFlashOn;
                } catch (Throwable e) {

                }
            }
        });

        ImageButton btnSwitch = (ImageButton) findViewById(R.id.switch_btn);
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCameraHelper.hasFrontCamera()) {
                    return;
                }
                try {
                    UninitCamera();
                    if (mCameraId == mCameraHelper
                            .getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)) {
                        mCameraId = mCameraHelper
                                .getCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
                    } else {
                        mCameraId = mCameraHelper
                                .getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
                    }
                    mSizesForCamera = null;
                    startCameraPreview();

                } catch (Throwable e) {

                }
                mFacingCamera = !mFacingCamera;
                mFlashOn = false;
            }
        });

        initShotClickSound();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        UninitCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFirstAvailable) {
            startCameraPreview();
        }
    }


    private Camera mCamera = null;
    private int mCameraId = 0;
    private int mRotationDegree = 0;
    private boolean mSupportFrontFalsh = false;
    private List<Camera.Size> mSizesForCamera = null;
    private boolean mTakeOneShot = false;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private MediaActionSound mMediaActionSound;

    private void initShotClickSound() {
        mMediaActionSound = new MediaActionSound();
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
    }

    private void InitCamera() {
        if (mCamera == null) {
            if (mCameraId != -1) {
                mCamera = mCameraHelper.openCamera(mCameraId);
            } else {
                mCamera = mCameraHelper.openDefaultCamera();
            }
            if (mCamera == null) {
                return;
            }

            try {
                mRotationDegree = mCameraHelper.getCameraDisplayOrientation(
                        this, mCameraId);
                mCamera.setDisplayOrientation(mRotationDegree);
            } catch (Throwable e) {
                UninitCamera();
            }
            mCamera.setPreviewCallback(this);
        }

        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            try {
                List<String> list_ret = mCamera.getParameters().getSupportedFlashModes();
                for (int i = 0; i < list_ret.size(); ++i) {
                    if (list_ret.get(i).equalsIgnoreCase(Camera.Parameters.FLASH_MODE_AUTO) ||
                            list_ret.get(i).equalsIgnoreCase(Camera.Parameters.FLASH_MODE_TORCH)) {
                        mSupportFrontFalsh = true;
                        break;
                    }
                }
            } catch (Throwable e) {
                mSupportFrontFalsh = false;
            }
        }
    }

    private void UninitCamera() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            } catch (Throwable e) {
            }
            try {
                mCamera.release();
            } catch (Throwable e) {
            }
            mCamera = null;
        }
    }

    private void startCameraPreview() {
        InitCamera();
        if (mCamera == null) {
            Toast.makeText(this, "open camera failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Camera.Parameters parameters = mCamera.getParameters();// 获得相机参数
        if (mSizesForCamera == null || mSizesForCamera.size() == 0) {
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            if (sizes == null) {
                return;
            }
            mSizesForCamera = new ArrayList<>();
            for (Camera.Size size : sizes) {
                mSizesForCamera.add(size);
            }
            if (mSizesForCamera.size() == 0) { // 摄像头不支持
                return;
            }
        }

        Camera.Size cameraSize = initCameraSizeFitPreviewSize(mTextureView.getWidth(), mTextureView.getHeight(),
                0.5625f/*16：9*/);
        if (cameraSize == null) {
            return;
        }
        mPreviewWidth = cameraSize.width;
        mPreviewHeight = cameraSize.height;
        setCameraFocusMode(mCamera, parameters);
        parameters.setPreviewSize(cameraSize.width, cameraSize.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setParameters(parameters);
        try {
            mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());
            mCamera.startPreview();
        } catch (Throwable e) {
            mCamera.release();
            mCamera = null;
            e.printStackTrace();
            return;
        }

        //根据摄像头比利缩放控件，防止变形
        setPreviewSize(cameraSize);
    }

    private boolean setCameraFocusMode(Camera paramCamera, Camera.Parameters paramParameters) {
        List localList = paramParameters.getSupportedFocusModes();
        if (localList == null) {
            return false;
        }
        String focusMode = null;
        if (bAutoFocus && localList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
        } else if (localList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
        } else if (localList.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
            focusMode = Camera.Parameters.FOCUS_MODE_INFINITY;
        }
        try {
            if (!TextUtils.isEmpty(focusMode)) {
                paramParameters.setFocusMode(focusMode);
                paramCamera.setParameters(paramParameters);
                return true;
            }
        } catch (Exception localException) {
            paramParameters.setFocusMode(paramParameters.getFocusMode());
        }
        return false;
    }

    private Camera.Size initCameraSizeFitPreviewSize(int textureviewWidth, int textureHeight, float ratio) {
        Camera.Size sizeRet = null;
        if (mSizesForCamera == null || mSizesForCamera.size() == 0) {
            return sizeRet;
        }

        for (int i = 0; i < mSizesForCamera.size(); i++) {
            Camera.Size size = mSizesForCamera.get(i);
            if (Math.min(size.width, size.height) == 1080 &&
                    Float.compare((float) Math.min(size.width, size.height) / Math.max(size.width, size.height), ratio) == 0
                    ) { //first round find 1080p

                sizeRet = size;
                return sizeRet;
            }
        }

        for (int i = 0; i < mSizesForCamera.size(); i++) { //second roud find 16:9 && size > preview size
            Camera.Size size = mSizesForCamera.get(i);
            if (Math.min(size.width, size.height) >= textureviewWidth &&
                    Math.max(size.width, size.height) >= textureHeight &&
                    Float.compare((float) Math.min(size.width, size.height) / Math.max(size.width, size.height), ratio) == 0) {
                sizeRet = size;
                return sizeRet;
            }
        }

        return sizeRet;
    }

    private void setPreviewSize(Camera.Size cameraSize) {
        int textureWidth = mTextureView.getWidth();
        int textureHeight = mTextureView.getHeight();
        float ratioView = (float) textureWidth / textureHeight;
        float ratioCamera = (float) Math.min(cameraSize.width, cameraSize.height) / Math.max(cameraSize.width, cameraSize.height);

        int textureWidthfinal = textureWidth;
        int textureHeightfinal = textureHeight;

        if (Float.compare(ratioView, ratioCamera) <= 0) { //控件的比例小于选定的相机比例，即需要横向拉伸控件
            textureWidthfinal = (int) (textureHeightfinal * ratioCamera);
        } else {
            textureHeightfinal = (int) (textureWidthfinal / ratioCamera);
        }
        RelativeLayout.LayoutParams rlPreview = new RelativeLayout.LayoutParams(
                textureWidthfinal,
                textureHeightfinal);
        mTextureView.setLayoutParams(rlPreview);
    }

    private void takePicture() {
        mTakeOneShot = true;
        mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (!mFirstAvailable) {
            startCameraPreview();
            mFirstAvailable = true;
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mFirstAvailable = false;
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!mTakeOneShot) {
            return;
        }
        mTakeOneShot = false;
        final byte[] new_data = new byte[data.length];
        System.arraycopy(data, 0, new_data, 0, data.length);
        if (data != null) {
            //DebugYUV(new_data);
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                final int imageFormat = parameters.getPreviewFormat();
                final int w = mPreviewWidth;
                final int h = mPreviewHeight;
                final int rotation = mCameraHelper
                        .getVideoOrientation(mCameraId);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int rgbs[] = null;
                        if (imageFormat == ImageFormat.NV21) {
                            try {
                                rgbs = new int[w * h];
                                decodeYUV420SP(rgbs, new_data, w, h);
                            } catch (Throwable e) {
                            }
                        } else if (imageFormat == ImageFormat.YV12) {
                            try {
                                rgbs = YuvToRGB.YV12ToRGB(new_data, w, h);
                            } catch (Throwable e) {
                            }
                        } else {
                            return;
                        }

                        try {
                            Matrix matrix = new Matrix();
                            matrix.postRotate(rotation);
                            //镜面翻转，如需要打开即可
//                            if (isFront) {
//                                matrix.postScale(-1, 1);
//                            }
                            Bitmap bmp = Bitmap.createBitmap(rgbs, w, h,
                                    Bitmap.Config.RGB_565);
                            Bitmap nbmp = Bitmap.createBitmap(bmp, 0, 0,
                                    w, h, matrix, true);

                            savePic(nbmp, "/sdcard/" + System.currentTimeMillis() + ".jpg");
                        } catch (Throwable e) {

                        }
                    }
                }).start();
            } catch (Throwable e) {

            }
        }
    }

    private void DebugYUV(byte[] data) {
        String str_path = "/sdcard/" + System.currentTimeMillis() + "yuv";
        //save data to file...
    }


    static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width,
                                      int height) {
        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) {
                    y = 0;
                }
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) {
                    r = 0;
                } else if (r > 262143) {
                    r = 262143;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 262143) {
                    g = 262143;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 262143) {
                    b = 262143;
                }

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000)
                        | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }


    public static void savePic(Bitmap b, String strFileName) {
        if (b == null) {
            return;
        }
        FileOutputStream fos = null;

        int rate = 85;
        try {
            fos = new FileOutputStream(strFileName);
            if (null != fos) {
                b.compress(Bitmap.CompressFormat.JPEG, rate, fos);
                fos.flush();
                fos.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
