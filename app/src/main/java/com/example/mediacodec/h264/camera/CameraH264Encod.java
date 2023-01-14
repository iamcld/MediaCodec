package com.example.mediacodec.h264.camera;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.example.mediacodec.FileUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraH264Encod {
    MediaCodec mMediaCodec;
    int frameIndex;
    int width;
    int height;

    public CameraH264Encod(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void startLive() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        try {
            // 创建H264编码器
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);//帧率，1秒钟15帧
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);// 每2秒1个I帧
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height);//设置码率，码率越高，视频越清晰.编码文件越大
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mMediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 1.数据生成方（左侧Client）从input缓冲队列申请empty buffer—》dequeueinputBuffer
     * 2.数据生产方（左侧Client）把需要编解码的数据copy到empty buffer，然后放入到input缓冲队列 —》queueInputBuffer
     * 3.MediaCodec从input缓冲区队列中取一帧进行编解码处理
     * 4.编解码处理结束后，MediaCodec将原始inputbuffer置为empty后放回左侧的input缓冲队列，将编解码后的数据放入到右侧output缓冲区队列
     * 5.消费方Client（右侧Client）从output缓冲区队列申请编解码后的buffer —》dequeueOutputBuffer
     * 6.消费方client（右侧Client）对编解码后的buffer进行渲染或者播放
     * 7.渲染/播放完成后，消费方Client再将该buffer放回到output缓冲区队列 —》releaseOutputBuffer
     * @param input
     * @return
     */

    // 数据流：摄像头-》CPU-》给到dsp进行编码-》编码后的数据重新给到cpu
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int encodeFrame(byte[] input) {
        // 输入,需要对input数据进行处理，不然画面是横的
        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(10000);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        if(inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(input);
            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length,
                    computPts(), 0);
            frameIndex++;
        }

        // 输出
        int outIndex = mMediaCodec.dequeueOutputBuffer(info, 10000);
        if (outIndex >= 0) {
            // 拿到编码后的数据
            ByteBuffer byteBuffer = mMediaCodec.getOutputBuffer(outIndex);
//                byte[] ba = new byte[info.size];
            byte[] ba = new byte[byteBuffer.remaining()];

            byteBuffer.get(ba);

            FileUtils.writeBytes("/sdcard/codec.h264", ba);
            FileUtils.writeContent("/sdcard/codecH264.txt", ba);
            mMediaCodec.releaseOutputBuffer(outIndex, false);
        }

        return -1;

    }
    /**
     * pts时间
     * 假设帧率是1秒钟15帧，所以第1帧的播放时间是 100 000(微秒) /15
     * 第2帧的播放时间是 100 000(微秒)/15 * 2
     * 第3帧的播放时间是 100 000(微秒)/15 *3
     */
    public int computPts() {
        return 1000000/15 * frameIndex;
    }
}
