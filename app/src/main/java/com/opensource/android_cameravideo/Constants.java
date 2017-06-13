package com.opensource.android_cameravideo;

import android.os.Environment;

/**
 * Created by Administrator
 */
public class Constants {



    //存放照片的文件夹
    public final static String  BASE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/video/";

    //拍摄的视频默认命名
    public final static String VEDIO_DEFAULT_NAME = "video.mp4";

    //发布时的录音默认命名


}
