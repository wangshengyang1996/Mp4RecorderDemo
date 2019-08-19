package com.wsy.mp4recorderdemo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class Mp4Recorder {

    private final static int TIMEOUT_USEC = 10000;
    private final static int frameRate = 25;

    private int videoWidth;
    private int videoHeight;
    private FileOutputStream videoOutputStream;
    private MediaCodec videoMediaCodec;
    private byte[] configByte;
    private byte[] nv12;
    boolean recording = false;

    public boolean isRecording() {
        return recording;
    }

    public Mp4Recorder(int videoWidth, int videoHeight, File file) {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new RuntimeException("file can not be created");
        }
        try {
            videoOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file not exists");
        }
        nv12 = new byte[videoWidth * videoHeight * 3 / 2];
    }

    public void startRecord() {
        MediaFormat mediaFormat;
        mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoWidth * videoHeight * 3);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        try {
            videoMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            throw new RuntimeException("createEncoderByType failed: " + e.getMessage());
        }
        videoMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoMediaCodec.start();
        recording = true;
    }

    public void pushFrame(byte[] data) {
        if (recording) {
            try {
                encodeVideo(data);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private void encodeVideo(byte[] nv21) throws IOException {

        nv21ToNv12(nv21, nv12);
        //得到编码器的输入和输出流, 输入流写入源数据 输出流读取编码后的数据
        //得到要使用的缓存序列角标
        int inputIndex = videoMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = videoMediaCodec.getInputBuffer(inputIndex);
            } else {
                inputBuffer = videoMediaCodec.getInputBuffers()[inputIndex];
            }
            inputBuffer.clear();
            //把要编码的数据添加进去
            inputBuffer.put(nv12);
            //塞到编码序列中, 等待MediaCodec编码
            videoMediaCodec.queueInputBuffer(inputIndex, 0, nv12.length, System.nanoTime() / 1000, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        //读取MediaCodec编码后的数据
        int outputIndex = videoMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
        byte[] frameData = null;
        int destPos = 0;
        while (outputIndex >= 0) {
            ByteBuffer outputBuffer = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                outputBuffer = videoMediaCodec.getOutputBuffer(outputIndex);
            } else {
                outputBuffer = videoMediaCodec.getOutputBuffers()[outputIndex];
            }
            byte[] h264 = new byte[bufferInfo.size];
            //这步就是编码后的h264数据了
            outputBuffer.get(h264);
            switch (bufferInfo.flags) {
                case MediaCodec.BUFFER_FLAG_CODEC_CONFIG://视频信息
                    configByte = new byte[bufferInfo.size];
                    configByte = h264;
                    break;
                case MediaCodec.BUFFER_FLAG_KEY_FRAME://关键帧
                    videoOutputStream.write(configByte, 0, configByte.length);
                    videoOutputStream.write(h264, 0, h264.length);
                    break;
                default://正常帧
                    videoOutputStream.write(h264, 0, h264.length);
                    if (frameData == null) {
                        frameData = new byte[bufferInfo.size];
                    }
                    System.arraycopy(h264, 0, frameData, destPos, h264.length);
                    break;
            }
            videoOutputStream.flush();
            //数据写入本地成功 通知MediaCodec释放data
            videoMediaCodec.releaseOutputBuffer(outputIndex, false);

            //读取下一次编码数据
            outputIndex = videoMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

            if (frameData != null) {
                if (outputIndex >= 0) {
                    destPos = frameData.length;
                    byte[] temp = new byte[frameData.length + bufferInfo.size];
                    System.arraycopy(frameData, 0, temp, 0, frameData.length);
                    frameData = temp;
                }
            }
        }
    }

    public void stopRecord() {
        recording = false;
        try {
            videoMediaCodec.stop();
            videoMediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //TODO 异常检查
    private void nv21ToNv12(byte[] nv21, byte[] nv12) {
        System.arraycopy(nv21, 0, nv12, 0, nv21.length * 2 / 3);

        int uvStart = nv21.length * 2 / 3;
        for (int i = uvStart; i < nv21.length; i += 2) {
            nv12[i + 1] = nv21[i];
            nv12[i] = nv21[i + 1];
        }
    }
}
