package com.wsy.mp4recorderdemo;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.wsy.mp4recorderdemo.camera.CameraHelper;
import com.wsy.mp4recorderdemo.camera.CameraListener;

import java.io.File;

public class CameraActivity extends BaseActivity implements ViewTreeObserver.OnGlobalLayoutListener, CameraListener {
    private static final String TAG = "CameraActivity";
    private static final int ACTION_REQUEST_PERMISSIONS = 1;
    private CameraHelper cameraHelper;
    private TextureView textureView;
    private Mp4Recorder mp4Recorder;
    //默认打开的CAMERA
    private static final int CAMERA_ID = Camera.CameraInfo.CAMERA_FACING_FRONT;
    //需要的权限
    private static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        initView();
    }

    private void initView() {
        textureView = findViewById(R.id.texture_preview);
        textureView.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }


    void initCamera() {
        cameraHelper = new CameraHelper.Builder()
                .cameraListener(CameraActivity.this)
                .specificCameraId(CAMERA_ID)
                .previewOn(textureView)
                .previewViewSize(new Point(textureView.getLayoutParams().width, textureView.getLayoutParams().height))
                .rotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        cameraHelper.start();
    }

    @Override
    protected void onRequestPermissionResult(int requestCode, boolean isAllGranted) {
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            if (isAllGranted) {
                initCamera();
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onGlobalLayout() {
        textureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
        int sideLength = Math.min(textureView.getWidth(), textureView.getHeight()) * 3 / 4;
        layoutParams.width = sideLength;
        layoutParams.height = sideLength;
        textureView.setLayoutParams(layoutParams);
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            initCamera();
        }
    }

    @Override
    protected void onPause() {
        if (cameraHelper != null) {
            cameraHelper.stop();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraHelper != null) {
            cameraHelper.start();
        }
    }


    private Camera.Size previewSize;

    @Override
    public void onCameraOpened(Camera camera, int cameraId, final int displayOrientation, boolean isMirror) {
        previewSize = camera.getParameters().getPreviewSize();
        Log.i(TAG, "onCameraOpened:  previewSize = " + previewSize.width + "x" + previewSize.height);
        //在相机打开时，添加右上角的view用于显示原始数据和预览数据
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //将预览控件和预览尺寸比例保持一致，避免拉伸
                {
                    ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
                    //横屏
                    if (displayOrientation % 180 == 0) {
                        layoutParams.height = layoutParams.width * previewSize.height / previewSize.width;
                    }
                    //竖屏
                    else {
                        layoutParams.height = layoutParams.width * previewSize.width / previewSize.height;
                    }
                    textureView.setLayoutParams(layoutParams);
                }
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

                FrameLayout.LayoutParams radiusSeekBarLayoutParams = new FrameLayout.LayoutParams(
                        displayMetrics.widthPixels, displayMetrics.heightPixels / 4
                );
            }
        });
    }

    @Override
    public void onPreview(final byte[] nv21, Camera camera) {
        Log.i(TAG, "onPreview: " + Thread.currentThread() + "  " + (Looper.myLooper() == Looper.getMainLooper()));
        if (mp4Recorder != null && mp4Recorder.isRecording()) {
            mp4Recorder.pushFrame(nv21);
        }
    }

    @Override
    public void onCameraClosed() {
        Log.i(TAG, "onCameraClosed: ");
    }

    @Override
    public void onCameraError(Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onCameraConfigurationChanged(int cameraID, int displayOrientation) {

    }

    @Override
    protected void onDestroy() {
        if (cameraHelper != null) {
            cameraHelper.release();
        }
        super.onDestroy();
    }

    public void switchRecord(View button) {
        if (mp4Recorder == null || !mp4Recorder.isRecording()) {
            File file = new File("sdcard/mp4recorderdemo/" + System.currentTimeMillis() + ".mp4");
            Log.i(TAG, "switchRecord: " + file);
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                Log.i(TAG, "switchRecord: failed");
                return;
            }
            mp4Recorder = new Mp4Recorder(previewSize.width, previewSize.height, file);
            mp4Recorder.startRecord();
            ((Button) button).setText("stop");
        }else {
            mp4Recorder.stopRecord();
            ((Button) button).setText("record");
        }
    }
}