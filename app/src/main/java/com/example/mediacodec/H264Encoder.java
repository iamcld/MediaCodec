package com.example.mediacodec;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class H264Encoder extends Thread{
    private static final String TAG = H264Encoder.class.getCanonicalName();

    // 数据源
    private MediaProjection mMediaProjection;
    // 编码器
    private MediaCodec mMediaCodec;
    private H264SocketLiveService socketLiveService;

    //视频宽高
    private int width;
    private int height;

    public static final int NAL_I = 5;//i帧
    public static final int NAL_SPS = 7;//sps帧

    // 保存H264文件开头是sps+pps
    private byte[] sps_pps_buf;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public H264Encoder(H264SocketLiveService socketLiveService, MediaProjection mediaProjection) {
        this.socketLiveService = socketLiveService;
        mMediaProjection = mediaProjection;
        width = 1080;
        height = 1920;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        try {
            // 创建H264编码器
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);//帧率，1秒钟20帧
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);// 设置I帧间隔。每隔30帧有1个I帧
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height);//设置码率，码率越高，视频越清晰.编码文件越大
            getSupportColorFormat();

            // 代码有问题
            // 数据来源从surface
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Surface surface = mMediaCodec.createInputSurface();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection.createVirtualDisplay("H264Demo", width, height, 2,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void run() {
        super.run();
        mMediaCodec.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int outIndex = mMediaCodec.dequeueOutputBuffer(info, 10000);
            // outIndex表示冲队列中一直取到编码后的数据，一直处于编码中
            if (outIndex >= 0) {
                // 拿到编码后的数据
                ByteBuffer byteBuffer = mMediaCodec.getOutputBuffer(outIndex);

                // 网络传输功能
                byteBuffer.rewind();
                dealFram(byteBuffer, info);

                // 投屏功能
                byte[] ba = new byte[info.size];
//                byte[] ba = new byte[byteBuffer.remaining()];
                byteBuffer.rewind();
                byteBuffer.get(ba);// 调用此语句，byteBuffer里面的数组指针position会跟着移动ba.length大小，
                FileUtils.writeBytes("/sdcard/codec.h264",ba);
                FileUtils.writeContent("/sdcard/codecH264.txt",ba);

                // 取完编码后数据，释放帧索引
                mMediaCodec.releaseOutputBuffer(outIndex, false);
            }
        }
    }

    //既然不同的手机支持的KEY_COLOR_FORMAT 不一样，这里就需要动态的考虑先获取到手机可支持的颜色格式值，在进行设置，如下代码也是参考网上的资料。
    private int getSupportColorFormat() {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo codecInfo = null;
        for (int i = 0; i < numCodecs && codecInfo == null; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (!info.isEncoder()) {
                continue;
            }
            String[] types = info.getSupportedTypes();
            boolean found = false;
            for (int j = 0; j < types.length && !found; j++) {
                if (types[j].equals("video/avc")) {
                    Log.d(TAG, "found");
                    found = true;
                }
            }
            if (!found)
                continue;
            codecInfo = info;
        }
        Log.e("AvcEncoder", "Found " + codecInfo.getName() + " supporting " + "video/avc");
        // Find a color profile that the codec supports
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        Log.e("AvcEncoder",
                "length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            Log.d(TAG, "MediaCodecInfo COLOR FORMAT :" + capabilities.colorFormats[i]);
            if ((capabilities.colorFormats[i] == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) || (capabilities.colorFormats[i] == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                return capabilities.colorFormats[i];
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    public void dealFram(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        // ox67 二进制为8位，分为3部分：
        //  第一位表示这一帧是否可用 0 可用，1不可用
        // 第二 三位表示重要性
        // 后5位才表示帧类型

        // 分隔符由00 00 00 00 01 和 00 00 00 01 两种
        // 默认分隔符是00 00 00 00 01
        // 帧类型信息所在的位数，偏移量4处为 帧类型信息所在的位数67
        int offset = 4;
        if (bb.get(2) == 0x01) {
            // 分隔符是00 00 00 01，偏移量3处为 帧类型信息所在的位数67
            offset = 3;
        }

        // 取得帧类型信息
        int type = (bb.get(offset) & 0x1f);

        // 此句代码关键，若要重新读取bytebuffer中数据，需要调用此句，对byteoffer里面的数组下标复位
        bb.rewind();

        // type为7，代表sps帧类型，编码器只会输出1次，故要保存起来
        if(type == NAL_SPS) {
            Log.d(TAG, "dealFram NAL_SPS "+ bufferInfo.size);

            // 第一次时，就会缓存sps+pps帧数据
            sps_pps_buf = new byte[bufferInfo.size];//存放sps+pps帧数据
            bb.get(sps_pps_buf);

        } else if (type == NAL_I) {
            Log.d(TAG, "dealFram NAL_I ------------------> "+ bufferInfo.size);
            byte[] bytes = new byte[bufferInfo.size];// 存放I帧数据
            bb.get(bytes);
            byte[] newBuf = new byte[sps_pps_buf.length + bytes.length];// 存放sps+pps+i帧数据
            System.arraycopy(sps_pps_buf, 0, newBuf, 0, sps_pps_buf.length);//拷贝sps+pps数据到新容器newBuf中
            System.arraycopy(bytes, 0, newBuf, sps_pps_buf.length, bytes.length);//拷贝I数据到新容器newBuf中，这样新容器的数据就是sps+pps+I帧数据
            socketLiveService.sendData(newBuf);
        } else {
            Log.d(TAG, "dealFram Nor NAL_I " + bufferInfo.size);
            // 非I、sps、pps帧，直接发送
            byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);
            socketLiveService.sendData(bytes);
        }
    }
}
