package com.example.mediacodec.h265;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.example.mediacodec.FileUtils;
import com.example.mediacodec.H264SocketLiveService;

import java.io.IOException;
import java.nio.ByteBuffer;

public class H265Encoder extends Thread{
    private static final String TAG = H265Encoder.class.getCanonicalName();

    // 数据源
    private MediaProjection mMediaProjection;
    // 编码器
    private MediaCodec mMediaCodec;
    private H264SocketLiveService socketLiveService;

    //视频宽高
    private int width;
    private int height;

    public static final int NAL_I = 19;//i帧
    public static final int NAL_VPS = 19;//vps帧

    // H264文件开头是vps+sps+pps
    private byte[] vps_sps_pps_buf;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public H265Encoder(H264SocketLiveService socketLiveService, MediaProjection mediaProjection) {
        mMediaProjection = mediaProjection;
        width = 720;
        height = 1080;
        // video/hevc  h265格式
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        try {
            // 创建H265编码器
            mMediaCodec = MediaCodec.createEncoderByType("video/hevc");
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 20);//帧率，1秒钟20帧
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);// 设置I帧间隔。每隔30帧有1个I帧
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height);//设置码率，码率越高，视频越清晰.编码文件越大
//            getSupportColorFormat();

            // 代码有问题
            // 数据来源从surface
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Surface surface = mMediaCodec.createInputSurface();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection.createVirtualDisplay("H265Demo", width, height, 1,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        mMediaCodec.start();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int outIndex = mMediaCodec.dequeueOutputBuffer(info, 10000);
            if (outIndex >= 0) {
                // 拿到编码后的数据
                ByteBuffer byteBuffer = mMediaCodec.getOutputBuffer(outIndex);

                // 网络传输功能
                byteBuffer.rewind();
                dealFram(byteBuffer, info);

                // 投屏功能
                byte[] ba = new byte[info.size];
//                byte[] ba = new byte[byteBuffer.remaining()];
                byteBuffer.get(ba);
                FileUtils.writeBytes("/sdcard/codec.h265",ba);
                FileUtils.writeContent("/sdcard/codecH265.txt",ba);


                mMediaCodec.releaseOutputBuffer(outIndex, false);
            }
        }
    }

    public void dealFram(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        // ox67  0 1
        // 分隔符由00 00 00 00 01 和 00 00 00 01 两种
        // 默认分隔符是00 00 00 00 01
        // 帧类型信息所在的位数，偏移量4处为 帧类型信息所在的位数67
        int offset = 4;
        if (bb.get(2) == 0x01) {
            // 分隔符是00 00 00 01，偏移量3处为 帧类型信息所在的位数67
            offset = 3;
        }

        // 取得帧类型信息
        int type = (bb.get(offset) & 0x7E) >> 1;

        // 此句代码关键，若要重新读取bytebuffer中数据，需要调用此句，对byteoffer里面的数组下标复位
        bb.rewind();

        // type为7，代表sps帧类型，编码器只会输出1次，故要保存起来
        if(type == NAL_VPS) {
            vps_sps_pps_buf = new byte[bufferInfo.size];
            bb.get(vps_sps_pps_buf);
        }
    }

}
