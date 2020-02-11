package org.easydarwin.video;

/*
 * 拉取的编码后的数据
 * */
public class FrameInfo {
    public int codec;           /* 音视频格式 */
    public int length;			/* 音视频帧大小 */
    public byte[] buffer;
    public int offset = 0;
    public boolean audio;

    public long timestamp_usec;	/* 时间戳,微妙 */
    public long timestamp_sec;	/* 时间戳 秒 */
    public long stamp;

    public int type;            /* 视频帧类型 I/B/P */
    public byte fps;	        /* 视频帧率 */
    public short width;	        /* 视频宽 */
    public short height;	    /* 视频高 */

    public int sample_rate;     /* 音频采样率 */
    public int channels;	    /* 音频声道数 */
    public int bits_per_sample;	/* 音频采样精度 */

//        public int reserved1;	    /* 保留参数1 */
//        public int reserved2;	    /* 保留参数2 */
//        public float bitrate;		/* 比特率 */
//        public float losspacket;	/* 丢包率 */
}
