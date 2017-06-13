package com.opensource.android_cameravideo;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.dinuscxj.progressbar.CircleProgressBar;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
        , SurfaceHolder.Callback, MediaRecorder.OnErrorListener{


    private SurfaceView mSurfaceView;
    private ImageView startBtn;
    private ImageView lightBtn;
    private ImageView tag_start;
    private AnimationDrawable anim;
    private LinearLayout lay_tool;
    private MediaRecorder mMediaRecorder;// 录制视频的类
    private SurfaceHolder mSurfaceHolder;
    private CircleProgressBar mProgressBar;
    private Camera mCamera;
    private Timer mTimer;// 计时器
    TimerTask timerTask;
    private boolean isOpenCamera = true;// 是否一开始就打开摄像头
    private int mRecordMaxTime = 100;// 一次拍摄最长时间 10秒
    private OnRecordFinishListener mOnRecordFinishListener;// 录制完成回调接口
    private int mTimeCount;// 时间计数
    private File mVecordFile = null;// 文件

    private boolean isStarting = false;
    List<int[]> mFpsRange;
    private Camera.Size optimalSize;
    private Camera.Parameters parameters;
    private boolean isFlashLightOn = false;
    //摄像头默认是后置， 0：前置， 1：后置
    private int cameraPosition = 1;
    //视频存储的目录
    private String dirname;

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);// 去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);// 设置全屏
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        initView();
    }

    private void initView() {
        mProgressBar = (CircleProgressBar)findViewById(R.id.progress);

        lightBtn = (ImageView) findViewById(R.id.lightBtn);
        tag_start = (ImageView) findViewById(R.id.tag_start);
        anim = (AnimationDrawable)tag_start.getDrawable();
        anim.setOneShot(false); // 设置是否重复播放
        lay_tool = (LinearLayout) findViewById(R.id.lay_tool);
        lightBtn.setOnClickListener(this);
        findViewById(R.id.exitBtn).setOnClickListener(this);
        findViewById(R.id.switchCamera).setOnClickListener(this);

        mSurfaceView = (SurfaceView)findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurfaceView.getHolder();// 取得holder
        mSurfaceHolder.addCallback(this); // holder加入回调接口
        mSurfaceHolder.setKeepScreenOn(true);

        startBtn = (ImageView) findViewById(R.id.startBtn);
        startBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN :
                        if(isStarting){
                            stopRecord();
                        }else {
                            startRecord(recordFinishListener);
                        }
//                        Log.i("ACTION", "DOWN");
                        break;
                    case MotionEvent.ACTION_UP:
                        if(mTimeCount < 30){
//                            Utils.toast("不能少于3秒！");
                            Toast.makeText(MainActivity.this, "不能少于3秒！", Toast.LENGTH_SHORT).show();
                            stopRecord();
                        } else {
                            stopRecord();
                            if (mOnRecordFinishListener != null){
                                mOnRecordFinishListener.onRecordFinish();
                            }
                        }
//                        Log.i("ACTION", "UP");
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 释放摄像头资源
     */
    private void freeCameraResource() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 录制前，初始化
     */
    private void initRecord() {
        try {

            if(mMediaRecorder == null){
                mMediaRecorder = new MediaRecorder();

            }
            if(mCamera != null){
                mCamera.unlock();
                mMediaRecorder.setCamera(mCamera);
            }

            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);// 视频源

            // Use the same size for recording profile.
            CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            mProfile.videoFrameWidth = optimalSize.width;
            mProfile.videoFrameHeight = optimalSize.height;

            mMediaRecorder.setProfile(mProfile);
            //该设置是为了抽取视频的某些帧，真正录视频的时候，不要设置该参数
//            mMediaRecorder.setCaptureRate(mFpsRange.get(0)[0]);//获取最小的每一秒录制的帧数

            mMediaRecorder.setOutputFile(mVecordFile.getAbsolutePath());

            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (Exception e) {
            e.printStackTrace();
            releaseRecord();
        }
    }

    private void switchCamera(){
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras();//得到摄像头的个数

        for(int i = 0; i < cameraCount; i++ ) {
            Camera.getCameraInfo(i, cameraInfo);//得到每一个摄像头的信息
            if(cameraPosition == 1) {
                //现在是后置，变更为前置
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_FRONT) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    mCamera.stopPreview();//停掉原来摄像头的预览
                    mCamera.release();//释放资源
                    mCamera = null;//取消原来摄像头
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    try {
                        mCamera.setDisplayOrientation(90);
                        mCamera.setPreviewDisplay(mSurfaceHolder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    mCamera.setParameters(parameters);// 设置相机参数
                    mCamera.startPreview();//开始预览
                    cameraPosition = 0;
                    break;
                }
            } else {
                //现在是前置， 变更为后置
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_BACK) {//代表摄像头的方位，CAMERA_FACING_FRONT前置      CAMERA_FACING_BACK后置
                    mCamera.stopPreview();//停掉原来摄像头的预览
                    mCamera.release();//释放资源
                    mCamera = null;//取消原来摄像头
                    mCamera = Camera.open(i);//打开当前选中的摄像头
                    try {
                        mCamera.setDisplayOrientation(90);
                        mCamera.setPreviewDisplay(mSurfaceHolder);//通过surfaceview显示取景画面
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    mCamera.setParameters(parameters);// 设置相机参数
                    mCamera.startPreview();//开始预览
                    cameraPosition = 1;
                    break;
                }
            }

        }
    }

    /**
     * 开始录制视频
     */
    public void startRecord(final OnRecordFinishListener onRecordFinishListener) {
        this.mOnRecordFinishListener = onRecordFinishListener;
        isStarting = true;
        lay_tool.setVisibility(View.INVISIBLE);
        tag_start.setVisibility(View.VISIBLE);
        anim.start();
        createRecordDir();
        try {
            initRecord();
            mTimeCount = 0;// 时间计数器重新赋值
            mTimer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    mTimeCount++;
                    mProgressBar.setProgress(mTimeCount);
                    if (mTimeCount == mRecordMaxTime) {// 达到指定时间，停止拍摄
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                stop();
                            }
                        });

                        if (mOnRecordFinishListener != null){
                            mOnRecordFinishListener.onRecordFinish();
                        }

                    }
                }
            };
            mTimer.schedule(timerTask, 0, 100);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
    }

    /**
     * 停止拍摄
     */
    public void stop() {
        stopRecord();
        releaseRecord();
        freeCameraResource();

    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        mProgressBar.setProgress(0);
        isStarting = false;
        tag_start.setVisibility(View.GONE);
        anim.stop();
        lay_tool.setVisibility(View.VISIBLE);
        if(timerTask != null)
            timerTask.cancel();
        if (mTimer != null)
            mTimer.cancel();
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (RuntimeException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 释放资源
     */
    private void releaseRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.setOnErrorListener(null);
            try {
                mMediaRecorder.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mMediaRecorder = null;
    }

    /**
     * 创建目录与文件
     */
    private void createRecordDir() {
        dirname = String.valueOf(System.currentTimeMillis()) +  String.valueOf( new Random().nextInt(1000));
        File FileDir = new File(Constants.BASE_PATH + dirname);
        if (!FileDir.exists()) {
            FileDir.mkdirs();
        }
        // 创建文件
        try {
            mVecordFile = new File(FileDir.getAbsolutePath() + "/" + Constants.VEDIO_DEFAULT_NAME);
            Log.d("Path:", mVecordFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    OnRecordFinishListener recordFinishListener = new OnRecordFinishListener() {
        @Override
        public void onRecordFinish() {
//            Intent intent = new Intent(RecordVideoActivity.this, VedioPlayActivity.class);
//            intent.putExtra("filePath", mVecordFile.getAbsolutePath());
//            intent.putExtra("dirname", dirname);
//            startActivity(intent);
//            finish();
        }
    };


    @Override
    public void onBackPressed() {
        stop();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){

            case R.id.exitBtn:
                stop();
                finish();
                break;
            case R.id.lightBtn:
                flashLightToggle();
                break;
            case R.id.switchCamera:
                switchCamera();
                break;
        }
    }


    private void flashLightToggle(){
        try {
            if(isFlashLightOn){
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(parameters);
//                lightBtn.setImageResource(R.mipmap.camera_light_0);
                isFlashLightOn = false;
            }else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                mCamera.setParameters(parameters);
//                lightBtn.setImageResource(R.mipmap.camera_light_1);
                isFlashLightOn = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            freeCameraResource();
        }

        try {
            mCamera = Camera.open();
            if (mCamera == null)
                return;
            mCamera.setDisplayOrientation(90);
            mCamera.setPreviewDisplay(mSurfaceHolder);
            parameters = mCamera.getParameters();// 获得相机参数

            List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
            List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
            optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                    mSupportedPreviewSizes, height, width);

            parameters.setPreviewSize(optimalSize.width, optimalSize.height); // 设置预览图像大小

            parameters.set("orientation", "portrait");
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains("continuous-video")) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mFpsRange =  parameters.getSupportedPreviewFpsRange();

            mCamera.setParameters(parameters);// 设置相机参数
            mCamera.startPreview();// 开始预览


        }catch (Exception io){
            io.printStackTrace();
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        freeCameraResource();
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        try {
            if (mr != null)
                mr.reset();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 录制完成回调接口
     */
    public interface OnRecordFinishListener {
        void onRecordFinish();
    }



}

