package com.example.mediacodec;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class H264TouPingActivity extends AppCompatActivity {

    private static final String TAG = H264TouPingActivity.class.getCanonicalName();
    // 录屏工具类
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private H264SocketLiveService socketLiveService;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void start(View view) {
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 请求用户同意录屏
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();

        startActivityForResult(captureIntent, 1);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "requestCode = " + requestCode +" resultCode = " + resultCode);

        if ( requestCode != 1) {
            // 不是录屏请求
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.d(TAG, "user reject");
            // 用户拒绝录屏权限
            return;
        }

        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);

        // 发送编码数据到另外一部设备代码
        socketLiveService = new H264SocketLiveService();
        socketLiveService.start(mMediaProjection);

        // 手机录制功能代码
//        H264Encoder h264Encoder = new H264Encoder(socketLiveService, mMediaProjection);
//        h264Encoder.start();
//        Log.d(TAG, "h264Encoder.start()");

    }

    public void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            }, 1);

        }
    }
}