package org.easydarwin.video;

import android.annotation.TargetApi;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import org.easydarwin.audio.AudioCodec;
import org.easydarwin.util.TextureLifecycler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static org.easydarwin.video.RTMPClient.EASY_SDK_VIDEO_FRAME_I;
import static org.easydarwin.video.RTMPClient.TRANS_TYPE_TCP;
import static org.easydarwin.video.EasyMuxer2.VIDEO_TYPE_H264;
import static org.easydarwin.video.EasyMuxer2.VIDEO_TYPE_H265;

/**
 * rtmp播放器拉流端
 *
 * Created by John on 2016/3/17.
 */
public class EasyRTMPPlayerClient implements RTMPClient.SourceCallBack {

    /* =========================== public property =========================== */

    /* 视频编码 */
    public static final int EASY_SDK_VIDEO_CODEC_H264 = 0x1C;       /* H264  */
    public static final int EASY_SDK_VIDEO_CODEC_H265 = 0x48323635; /* H265 */
//    public static final int EASY_SDK_VIDEO_CODEC_MJPEG = 0x08;      /* MJPEG */
//    public static final int EASY_SDK_VIDEO_CODEC_MPEG4 = 0x0D;      /* MPEG4 */

    /* 音频编码 */
    public static final int EASY_SDK_AUDIO_CODEC_AAC = 0x15002;     /* AAC */
    public static final int EASY_SDK_AUDIO_CODEC_G711U = 0x10006;   /* G711 ulaw */
    public static final int EASY_SDK_AUDIO_CODEC_G711A = 0x10007;   /* G711 alaw */
    public static final int EASY_SDK_AUDIO_CODEC_G726 = 0x1100B;    /* G726 */

    // 表示视频的解码方式
    public static final String KEY_VIDEO_DECODE_TYPE = "video-decode-type";
    // 表示视频的宽度
    public static final String EXTRA_VIDEO_WIDTH = "extra-video-width";
    // 表示视频的高度
    public static final String EXTRA_VIDEO_HEIGHT = "extra-video-height";

    // 表示视频显示出来了
    public static final int RESULT_VIDEO_DISPLAYED = 01;
    // 表示视频的尺寸获取到了。具体尺寸见 EXTRA_VIDEO_WIDTH、EXTRA_VIDEO_HEIGHT
    public static final int RESULT_VIDEO_SIZE = 02;
    // 表示KEY的可用播放时间已用完
    public static final int RESULT_TIMEOUT = 03;
    // 表示KEY的可用播放时间已用完
    public static final int RESULT_EVENT = 04;
    public static final int RESULT_UNSUPPORTED_VIDEO = 05;
    public static final int RESULT_UNSUPPORTED_AUDIO = 06;
    public static final int RESULT_RECORD_BEGIN = 7;
    public static final int RESULT_RECORD_END = 8;

    /* =========================== private property =========================== */

    private static final String TAG = EasyRTMPPlayerClient.class.getSimpleName();
    private final String mKey;

    private static final int NAL_VPS = 32;
    private static final int NAL_SPS = 33;
    private static final int NAL_PPS = 34;

    private Surface mSurface;
    private final TextureLifecycler lifeCycle;
    private volatile Thread mVideoThread, mAudioThread;
    private final ResultReceiver mRR;

    private RTMPClient mRTMPClient;

    private boolean mAudioEnable = true;
    private volatile long mReceivedDataLength;

    private AudioTrack mAudioTrack;
    private String mRecordingPath;

//    private EasyAACMuxer mObject;
    private EasyMuxer2 muxer2;

    private RTMPClient.MediaInfo mMediaInfo;
    private short mHeight = 0;
    short mWidth = 0;

    private ByteBuffer mCSD0;
    private ByteBuffer mCSD1;

    private final I420DataCallback i420callback;
    private boolean mMuxerWaitingKeyVideo;

    /*
    * 缓存音视频帧的队列
    * */
    private static class FrameInfoQueue extends PriorityQueue<FrameInfo> {
        private static final int CAPACITY = 500;         // 最大长度，达到最大长度清空队列
        private static final int INITIAL_CAPACITY = 300; // 设置队列长度，达到设置队列长度开始丢帧

        final ReentrantLock lock = new ReentrantLock();
        final Condition notFull = lock.newCondition();
        final Condition notVideo = lock.newCondition();
        final Condition notAudio = lock.newCondition();

        public FrameInfoQueue() {
            super(INITIAL_CAPACITY, new Comparator<FrameInfo>() {
                @Override
                public int compare(FrameInfo frameInfo, FrameInfo t1) {
                    return (int) (frameInfo.stamp - t1.stamp);
                }
            });
        }

        @Override
        public int size() {
            lock.lock();

            try {
                return super.size();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void clear() {
            lock.lock();

            try {
                int size = super.size();
                super.clear();
                int k = size;

                for (; k > 0 && lock.hasWaiters(notFull); k--) {
                    notFull.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        public void put(FrameInfo x) throws InterruptedException {
            // lockInterruptibly方法，可以对线程interrupt方法做出响应；在一个线程等待锁的过程中，可以被打断
            lock.lockInterruptibly();

            try {
                int size;

                while ((size = super.size()) == CAPACITY) {
                    Log.v(TAG, "queue full:" + CAPACITY);
                    notFull.await();
                }

                // 添加元素
                offer(x);
                Log.d(TAG, String.format("queue size : " + size));

                // 这里是乱序的。并非只有空的queue才丢到首位。因此不能做限制
//                if (size == 0) {
                    if (x.audio) {
                        notAudio.signal();
                    } else {
                        notVideo.signal();
                    }
//                }
            } finally {
                lock.unlock();
            }
        }

        public FrameInfo takeVideoFrame() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    FrameInfo x = peek();

                    if (x == null) {
                        notVideo.await();
                    } else {
                        if (!x.audio) {

                            // 删除元素
                            remove();

                            notFull.signal();
                            notAudio.signal();

                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public FrameInfo takeVideoFrame(long ms) throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    // peek()方法：获取但不移除此队列的头；如果此队列为空，则返回 null。
                    FrameInfo x = peek();

                    if (x == null) {
                        if (!notVideo.await(ms, TimeUnit.MILLISECONDS))
                            return null;
                    } else {
                        if (!x.audio) {
                            remove();

                            notFull.signal();
                            notAudio.signal();

                            return x;
                        } else {
                            notVideo.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public FrameInfo takeAudioFrame() throws InterruptedException {
            lock.lockInterruptibly();

            try {
                while (true) {
                    FrameInfo x = peek();

                    if (x == null) {
                        notAudio.await();
                    } else {
                        if (x.audio) {
                            remove();

                            notFull.signal();
                            notVideo.signal();

                            return x;
                        } else {
                            notAudio.await();
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private FrameInfoQueue mQueue = new FrameInfoQueue();

    private final Context mContext;

    /**
     * 最新的视频时间戳
     */
    private volatile long mNewestStamp;
    private boolean mWaitingKeyFrame;
    private boolean mTimeout;
    private boolean mNotSupportedVideoCB, mNotSupportedAudioCB;

    /* =========================== public method =========================== */

    /**
     * 创建SDK对象
     *
     * @param context 上下文对象
     * @param key     SDK key
     * @param surface 显示视频用的surface
     */
    public EasyRTMPPlayerClient(Context context, String key, Surface surface, ResultReceiver receiver) {
        this(context, key, surface, receiver, null);
    }

    /**
     * 创建SDK对象
     *
     * @param context 上下文对象
     * @param key     SDK key
     * @param surface 显示视频用的surface
     */
    public EasyRTMPPlayerClient(Context context, String key, Surface surface, ResultReceiver receiver, I420DataCallback callback) {
        mSurface = surface;
        mContext = context;
        mKey = key;
        mRR = receiver;
        i420callback = callback;
        lifeCycle = null;
    }

    public EasyRTMPPlayerClient(Context context, String key, final TextureView view, ResultReceiver receiver, I420DataCallback callback) {
        lifeCycle = new TextureLifecycler(view);
        mContext = context;
        mKey = key;
        mRR = receiver;
        i420callback = callback;

        // LifecycleObserver
        // 生命周期监听者。通过注解将处理函数与希望监听的Event绑定,当相应的Event发生时,LifecycleRegistry会通知相应的函数进行处理。
        LifecycleObserver observer1 = new LifecycleObserver() {
            @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
            public void destory() {
                stop();
                mSurface.release();
                mSurface = null;
            }

            @OnLifecycleEvent(value = Lifecycle.Event.ON_CREATE)
            private void create() {
                mSurface = new Surface(view.getSurfaceTexture());
            }
        };

        lifeCycle.getLifecycle().addObserver(observer1);

        if (context instanceof LifecycleOwner) {
            LifecycleObserver observer = new LifecycleObserver() {
                @OnLifecycleEvent(value = Lifecycle.Event.ON_DESTROY)
                public void destory() {
                    stop();
                }

                @OnLifecycleEvent(value = Lifecycle.Event.ON_PAUSE)
                private void pause() {
                    EasyRTMPPlayerClient.this.pause();
                }

                @OnLifecycleEvent(value = Lifecycle.Event.ON_RESUME)
                private void resume() {
                    EasyRTMPPlayerClient.this.resume();
                }
            };

            ((LifecycleOwner) context).getLifecycle().addObserver(observer);
        }
    }

    /**
     * 启动播放
     *
     * @param url
     * @return
     */
    public void play(final String url) {
        if (lifeCycle.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)) {
            start(url,
                    TRANS_TYPE_TCP,
                    RTMPClient.EASY_SDK_VIDEO_FRAME_FLAG | RTMPClient.EASY_SDK_AUDIO_FRAME_FLAG,
                    "",
                    "",
                    null);
        } else {
            lifeCycle.getLifecycle().addObserver(new LifecycleObserver() {
                @OnLifecycleEvent(value = Lifecycle.Event.ON_CREATE)
                void create() {
                    start(url,
                            TRANS_TYPE_TCP,
                            RTMPClient.EASY_SDK_VIDEO_FRAME_FLAG | RTMPClient.EASY_SDK_AUDIO_FRAME_FLAG,
                            "",
                            "",
                            null);
                }
            });
        }
    }

    /**
     * 启动播放
     *
     * @param url
     * @param type
     * @param mediaType
     * @param user
     * @param pwd
     * @return
     */
    public int start(final String url, int type, int mediaType, String user, String pwd, String recordPath) {
        if (url == null) {
            throw new NullPointerException("url is null");
        }

        Log.i(TAG, String.format("playing url:\n%s\n", url));

        if (type == 0)
            type = TRANS_TYPE_TCP;

        mNewestStamp = 0;
        mWaitingKeyFrame = true;
        mWidth = mHeight = 0;
        mQueue.clear();

        startCodec();
        startAudio();

        mTimeout = false;
        mNotSupportedVideoCB = mNotSupportedAudioCB = false;
        mReceivedDataLength = 0;

        mRecordingPath = recordPath;

        mRTMPClient = new RTMPClient(mContext, mKey);
        int channel = mRTMPClient.registerCallback(this);
        return mRTMPClient.openStream(channel, url, type, mediaType, user, pwd);
    }

    /**
     * 终止播放
     */
    public void stop() {
        Thread t = mVideoThread;
        mVideoThread = null;
        if (t != null) {
            t.interrupt();

            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        t = mAudioThread;
        mAudioThread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        stopRecord();

        mQueue.clear();

        if (mRTMPClient != null) {
            mRTMPClient.removeCallback(this);
            mRTMPClient.closeStream();

            try {
                mRTMPClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mQueue.clear();
        mRTMPClient = null;
        mNewestStamp = 0;
    }

    public void pause() {
        mQueue.clear();

        if (mRTMPClient != null) {
            mRTMPClient.pause();
        }

        mQueue.clear();
    }

    public void resume() {
        if (mRTMPClient != null) {
            mRTMPClient.resume();
        }
    }

    public boolean isAudioEnable() {
        return mAudioEnable;
    }

    public void setAudioEnable(boolean enable) {
        mAudioEnable = enable;
        AudioTrack at = mAudioTrack;

        if (at != null) {
            Log.i(TAG, String.format("audio will be %s", enable ? "enabled" : "disabled"));

            synchronized (at) {
                if (!enable) {
                    /*
                    * 对于MODE_STREAM模式，如果单是调用stop方法,AudioTrack会等待缓冲的最后一帧数据播放完毕之后才会停止
                    * 如果需要立即停止,就需要调用pause然后调用flush,那么AudioTrack就会丢弃缓冲区中剩余的数据。
                    * */
                    at.pause();
                    at.flush();
                } else {
                    at.flush();
                    at.play();
                }
            }
        }
    }

    public boolean isRecording() {
        return !TextUtils.isEmpty(mRecordingPath);
    }

    /*
     * 开始录像
     * */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public synchronized void startRecord(String path) {
        if (mMediaInfo == null || mWidth == 0 || mHeight == 0 || mCSD0 == null)
            return;

        mRecordingPath = path;
        EasyMuxer2 muxer2 = new EasyMuxer2();

        ByteBuffer csd1 = this.mCSD1;

        if (csd1 == null)
            csd1 = ByteBuffer.allocate(0);

        byte[] extra = new byte[mCSD0.capacity() + csd1.capacity()];

        // position = 0;limit = capacity;mark = -1;  有点初始化的味道，但是并不影响底层byte数组的内容
        mCSD0.clear();
        csd1.clear();

        // get(byte[] dst, int offset, int length)
        // 从position位置开始相对读，读length个byte，并写入dst下标从offset到offset+length的区域
        mCSD0.get(extra, 0, mCSD0.capacity());
        csd1.get(extra, mCSD0.capacity(), csd1.capacity());

        int r = muxer2.create(path,
                mMediaInfo.videoCodec == EASY_SDK_VIDEO_CODEC_H265 ? VIDEO_TYPE_H265 : VIDEO_TYPE_H264,
                mWidth,
                mHeight,
                extra,
                mMediaInfo.sample,
                mMediaInfo.channel);

        if (r != 0) {
            Log.w(TAG, "create muxer2:" + r);
            return;
        }

        mMuxerWaitingKeyVideo = true;
        this.muxer2 = muxer2;
        ResultReceiver rr = mRR;

        if (rr != null) {
            rr.send(RESULT_RECORD_BEGIN, null);
        }
    }

    /*
     * 停止录像
     * */
    public synchronized void stopRecord() {
        mRecordingPath = null;
        EasyMuxer2 muxer2 = this.muxer2;

        if (muxer2 == null)
            return;

        this.muxer2 = null;
        muxer2.close();
//        mObject = null;
        ResultReceiver rr = mRR;

        if (rr != null) {
            rr.send(RESULT_RECORD_END, null);
        }
    }

    public long receivedDataLength() {
        return mReceivedDataLength;
    }

    /* =========================== private method =========================== */

    private void startCodec() {
        mVideoThread = new Thread("VIDEO_CONSUMER") {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                MediaCodec mCodec = null;   // 硬解码
                VideoCodec.VideoDecoderLite mDecoder = null, displayer = null;// 软解码

                int mColorFormat = 0;

                try {
                    boolean pushBlankBuffersOnStop = true;

                    int index;

                    // previous
                    long previousStampUs = 0l;
                    long lastFrameStampUs = 0l;
                    long differ = 0;
//
//                    long decodeBegin = 0;
//                    long current = 0;

                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                    while (mVideoThread != null) {
                        FrameInfo frameInfo;

                        if (mCodec == null && mDecoder == null) {
                            frameInfo = mQueue.takeVideoFrame();

                            try {
                                // 使用软解码时候，抛异常，到catch里面解码
                                if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("use-sw-codec", false)) {
                                    throw new IllegalStateException("user set sw codec");
                                }

                                final String mime = frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264 ? "video/avc" : "video/hevc";

                                // 配置编码器
                                MediaFormat format = MediaFormat.createVideoFormat(mime, mWidth, mHeight);
                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
                                format.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, pushBlankBuffersOnStop ? 1 : 0);

                                /*
                                * 编解码器特定的数据：
                                *       CSD buffer #0   CSD buffer #1
                                * H264  sps             pps
                                * H265  VPS+SPS+PPS     Not Used
                                * */
                                if (mCSD0 != null) {
                                    format.setByteBuffer("csd-0", mCSD0);
                                } else {
                                    throw new InvalidParameterException("csd-0 is invalid.");
                                }

                                if (mCSD1 != null) {
                                    format.setByteBuffer("csd-1", mCSD1);
                                } else {
                                    if (frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264)
                                        throw new InvalidParameterException("csd-1 is invalid.");
                                }

                                MediaCodecInfo ci = selectCodec(mime);

                                /*
                                * 1、选择出一个 MediaCodecInfo，初始化MediaCodec
                                *   如果不存在这个编解码器，将抛出IOException。*/
                                MediaCodec codec = MediaCodec.createByCodecName(ci.getName());
                                MediaCodecInfo.CodecCapabilities capabilities = ci.getCapabilitiesForType(mime);

                                if (capabilities.colorFormats != null && capabilities.colorFormats.length > 0) {
                                    mColorFormat = capabilities.colorFormats[0];
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    boolean supported = capabilities.getVideoCapabilities().isSizeSupported(mWidth, mHeight);
                                    Log.i(TAG, "media codec " + ci.getName() + (supported ? "support" : "not support") + mWidth + "*" + mHeight);
                                }

                                Log.i(TAG, String.format("config codec:%s", format));

                                // 2、配置，进入Configured状态
                                codec.configure(format,
                                        i420callback != null ? null : mSurface,//生成原始视频数据的解码器指定输出的Surface
                                        null,
                                        0);

                                // 3、start()进入到执行状态，编解码器立即处于Flushed子状态，它拥有所有的缓冲区。
                                codec.start();

                                // 设置缩放模式（此方法必须在configure和start之后执行才有效）
                                codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);

                                mCodec = codec;

                                if (i420callback != null) {
                                    final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                    decoder.create(mSurface, frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);
                                    displayer = decoder;
                                }
                            } catch (Throwable e) {
                                Log.e(TAG, String.format("init codec error due to %s", e.getMessage()));
                                e.printStackTrace();

                                final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                decoder.create(mSurface, frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);

                                mDecoder = decoder;
                            }
//                            previewTickUs = mTexture.getTimestamp();
//                            differ = previewTickUs - frameInfo.stamp;
//                            index = mCodec.dequeueInputBuffer(0);
//                            if (index >= 0) {
//                                ByteBuffer buffer = mCodec.getInputBuffers()[index];
//                                buffer.clear();
//                                mCSD0.clear();
//                                mCSD1.clear();
//                                buffer.put(mCSD0.array(), 0, mCSD0.remaining());
//                                buffer.put(mCSD1.array(), 0, mCSD1.remaining());
//                                mCodec.queueInputBuffer(index, 0, buffer.position(), 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
//                            }
                        } else {
                            frameInfo = mQueue.takeVideoFrame(5);
                        }

                        if (frameInfo != null) {
                            Log.d(TAG, "video " + frameInfo.stamp + " take[" + (frameInfo.stamp - lastFrameStampUs) + "]");
                            pumpVideoSample(frameInfo);
                            lastFrameStampUs = frameInfo.stamp;
                        }

                        do {
                            if (mDecoder != null) {
                                if (frameInfo != null) {
                                    long decodeBegin = SystemClock.elapsedRealtime();
                                    int[] size = new int[2];
//                                    mDecoder.decodeFrame(frameInfo, size);
                                    ByteBuffer buf = mDecoder.decodeFrameYUV(frameInfo, size);

                                    if (i420callback != null && buf != null) {
                                        i420callback.onI420Data(buf);
                                    }

                                    if (buf != null)
                                        mDecoder.releaseBuffer(buf);

                                    long decodeSpend = SystemClock.elapsedRealtime() - decodeBegin;
                                    boolean firstFrame = previousStampUs == 0l;

                                    if (firstFrame) {
                                        Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));

                                        ResultReceiver rr = mRR;

                                        if (rr != null) {
                                            Bundle data = new Bundle();
                                            data.putInt(KEY_VIDEO_DECODE_TYPE, 0);
                                            rr.send(RESULT_VIDEO_DISPLAYED, data);
                                        }
                                    }

                                    //Log.d(TAG, String.format("timestamp=%d diff=%d",current, current - previousStampUs ));

                                    if (previousStampUs != 0l) {
                                        long sleepTime = frameInfo.stamp - previousStampUs - decodeSpend * 1000;

                                        if (sleepTime > 100000) {
                                            Log.w(TAG, "sleep time.too long:" + sleepTime);
                                            sleepTime = 100000;
                                        }

                                        if (sleepTime > 0) {
                                            sleepTime %= 100000;
                                            long cache = mNewestStamp - frameInfo.stamp;
                                            sleepTime = fixSleepTime(sleepTime, cache, 50000);

                                            if (sleepTime > 0) {
                                                // sleepTime是微秒值
                                                Thread.sleep(sleepTime / 1000);// 单位是毫秒
                                            }

                                            Log.d(TAG, "cache:" + cache);
                                        }
                                    }

                                    previousStampUs = frameInfo.stamp;
                                }
                            } else {
                                try {
                                    do {
                                        if (frameInfo != null) {
                                            byte[] pBuf = frameInfo.buffer;
                                            index = mCodec.dequeueInputBuffer(10);

//                                            if (false)
//                                                throw new IllegalStateException("fake state");

                                            if (index >= 0) {
                                                ByteBuffer buffer = mCodec.getInputBuffers()[index];
                                                buffer.clear();

                                                if (pBuf.length > buffer.remaining()) {
                                                    mCodec.queueInputBuffer(index, 0, 0, frameInfo.stamp, 0);
                                                } else {
                                                    buffer.put(pBuf, frameInfo.offset, frameInfo.length);
                                                    mCodec.queueInputBuffer(index, 0, buffer.position(), frameInfo.stamp + differ, 0);
                                                }

                                                frameInfo = null;
                                            }
                                        }

                                        index = mCodec.dequeueOutputBuffer(info, 10); //
                                        switch (index) {
                                            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                                Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                                                break;
                                            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                                MediaFormat mf = mCodec.getOutputFormat();
                                                Log.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED ：" + mf);
                                                break;
                                            case MediaCodec.INFO_TRY_AGAIN_LATER:
                                                // 输出为空
                                                break;
                                            default:
                                                // 输出队列不为空
                                                // -1表示为第一帧数据
                                                long newSleepUs = -1;
                                                boolean firstTime = previousStampUs == 0l;

                                                if (!firstTime) {
                                                    long sleepUs = (info.presentationTimeUs - previousStampUs);

                                                    if (sleepUs > 100000) {
                                                        // 时间戳异常，可能服务器丢帧了。
                                                        Log.w(TAG, "sleep time.too long:" + sleepUs);
                                                        sleepUs = 100000;
                                                    } else if (sleepUs < 0) {
                                                        Log.w(TAG, "sleep time.too short:" + sleepUs);
                                                        sleepUs = 0;
                                                    }

                                                    long cache = mNewestStamp - lastFrameStampUs;
                                                    newSleepUs = fixSleepTime(sleepUs, cache, 100000);
                                                    // Log.d(TAG, String.format("sleepUs:%d,newSleepUs:%d,Cache:%d", sleepUs, newSleepUs, cache));
                                                }

                                                // previousStampUs = info.presentationTimeUs;
                                                ByteBuffer outputBuffer;

                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                    outputBuffer = mCodec.getOutputBuffer(index);
                                                } else {
                                                    outputBuffer = mCodec.getOutputBuffers()[index];
                                                }

                                                if (i420callback != null && outputBuffer != null) {
                                                    if (mColorFormat != COLOR_FormatYUV420Flexible && mColorFormat != COLOR_FormatYUV420Planar && mColorFormat != 0) {
//                                                        JNIUtil.yuvConvert();
                                                    }

                                                    i420callback.onI420Data(outputBuffer);
                                                    displayer.decoder_decodeBuffer(outputBuffer, mWidth, mHeight);
                                                }

                                                // previewStampUs = info.presentationTimeUs;
                                                if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                    Log.d(TAG, String.format("release output buffer:%d,stampUs:%d", index, previousStampUs));

                                                    // 使用特定时间戳渲染缓冲区
                                                    mCodec.releaseOutputBuffer(index, previousStampUs);
                                                } else {
                                                    if (newSleepUs < 0) {
                                                        newSleepUs = 0;
                                                    }

                                                    Log.d(TAG,String.format("sleep:%d", newSleepUs / 1000));
                                                    Thread.sleep(newSleepUs / 1000);

                                                    /*
                                                    * render：false， 不要渲染缓冲区
                                                    * render：true，  使用默认时间戳渲染缓冲区
                                                    */
                                                    mCodec.releaseOutputBuffer(index, i420callback == null);
                                                }

                                                if (firstTime) {
                                                    Log.i(TAG, String.format("POST VIDEO_DISPLAYED!!!"));
                                                    ResultReceiver rr = mRR;

                                                    if (rr != null) {
                                                        Bundle data = new Bundle();
                                                        data.putInt(KEY_VIDEO_DECODE_TYPE, 1);
                                                        rr.send(RESULT_VIDEO_DISPLAYED, data);
                                                    }
                                                }

                                                previousStampUs = info.presentationTimeUs;
                                        }
                                    } while (frameInfo != null || index < MediaCodec.INFO_TRY_AGAIN_LATER);
                                } catch (IllegalStateException ex) {
                                    // media codec error...
                                    ex.printStackTrace();
                                    Log.e(TAG, String.format("init codec error due to %s", ex.getMessage()));

                                    final VideoCodec.VideoDecoderLite decoder = new VideoCodec.VideoDecoderLite();
                                    decoder.create(mSurface, frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264);
                                    mDecoder = decoder;

                                    if (mCodec != null) {
                                        mCodec.release();
                                        mCodec = null;
                                    }

                                    continue;
                                }
                            }
                            break;
                        } while (true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (mCodec != null) {
//                        mCodec.stop();
                        mCodec.release();
                    }
                    if (mDecoder != null) {
                        mDecoder.close();
                    }
                    if (displayer != null) {
                        displayer.close();
                    }
                }
            }
        };

        mVideoThread.start();
    }

    private void startAudio() {
        mAudioThread = new Thread("AUDIO_CONSUMER") {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                FrameInfo frameInfo;
                long handle = 0;

                // AudioManager音频管理器,获得AudioManager对象实例
                final AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                AudioManager.OnAudioFocusChangeListener l = new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {// 当其他应用申请焦点之后又释放焦点会触发此回调,可重新播放
                            AudioTrack audioTrack = mAudioTrack;

                            if (audioTrack != null) {
                                audioTrack.setStereoVolume(1.0f, 1.0f);

                                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED) {
                                    audioTrack.flush();
                                    audioTrack.play();
                                }
                            }
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS || //长时间丢失焦点,当其他应用申请的焦点为AUDIOFOCUS_GAIN时，会触发此回调事件
                                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {//短暂性丢失焦点，当其他应用申请AUDIOFOCUS_GAIN_TRANSIENT或AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE时，会触发此回调事件，例如播放短视频，拨打电话等。通常需要暂停
                            AudioTrack audioTrack = mAudioTrack;

                            if (audioTrack != null) {
                                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                                    audioTrack.pause();
                                }
                            }
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {//短暂性丢失焦点并作降音处理
                            AudioTrack audioTrack = mAudioTrack;

                            if (audioTrack != null) {
                                audioTrack.setStereoVolume(0.5f, 0.5f);
                            }
                        }
                    }
                };

                try {
                    // 请求音频的焦点
                    int requestCode = am.requestAudioFocus(l, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                    if (requestCode != AUDIOFOCUS_REQUEST_GRANTED) {
                        return;
                    }

                    do {
                        // 取出音频帧，如果已经获取到mMediaInfo，则只取出一帧
                        frameInfo = mQueue.takeAudioFrame();

                        if (mMediaInfo != null)
                            break;
                    } while (true);

                    if (mAudioTrack == null) {
                        int sampleRateInHz = (int) (mMediaInfo.sample * 1.001);
                        int channelConfig = mMediaInfo.channel == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                        int bfSize = AudioTrack.getMinBufferSize(mMediaInfo.sample, channelConfig, audioFormat) * 8;

                        /*
                        * 1、配置AudioTrack参数，初始化内部的音频播放缓冲区
                        * streamType：当前应用使用的哪一种音频管理策略，STREAM_VOCIE_CALL：电话声音、STREAM_SYSTEM：系统声音、STREAM_RING：铃声、STREAM_MUSCI：音乐声、STREAM_ALARM：警告声、STREAM_NOTIFICATION：通知声
                        * sampleRateInH：设置音频数据的采样率
                        * channelConfig：设置输出声道为双声道立体声
                        * audioFormat：设置音频数据块是8位还是16位
                        * bufferSizeInBytes：缓冲区大小
                        * mode：设置模式类型，在这里设置为流类型：static 静态的数据；streaming 流模式播放数据
                        * */
                        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                                sampleRateInHz,
                                channelConfig,
                                audioFormat,
                                bfSize,
                                AudioTrack.MODE_STREAM);

                        // 2、启动音频设备、开始播放
                        mAudioTrack.play();
                    }

                    // 创建音频解码器
                    handle = AudioCodec.create(frameInfo.codec,
                            mMediaInfo.sample,
                            mMediaInfo.channel,
                            mMediaInfo.bitPerSample);

//                    Log.w(TAG, String.format("POST VIDEO_DISPLAYED IN AUDIO THREAD!!!"));
//                    ResultReceiver rr = mRR;
//                    if (rr != null) rr.send(RESULT_VIDEO_DISPLAYED, null);

                    // 半秒钟的数据缓存
                    byte[] mBufferReuse = new byte[16000];
                    int[] outLen = new int[1];

                    while (mAudioThread != null) {
                        if (frameInfo == null) {
                            frameInfo = mQueue.takeAudioFrame();
                        }

//                        if (frameInfo.codec == EASY_SDK_AUDIO_CODEC_AAC && false) {
//                            pumpAACSample(frameInfo);
//                        }

                        outLen[0] = mBufferReuse.length;

                        // 解码音频
                        int nRet = AudioCodec.decode(handle,
                                frameInfo.buffer,
                                0,
                                frameInfo.length,
                                mBufferReuse,
                                outLen);

                        if (nRet == 0) {
//                            if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC) {
//                                save2path(mBufferReuse, 0, outLen[0],"/sdcard/111.pcm", true);
                                pumpPCMSample(mBufferReuse, outLen[0], frameInfo.stamp);
//                            }

                            // 3、开启一个子线程不断的向AudioTrack的缓冲区写入音频数据。这个过程要及时，否则就会出现“underrun”的错误
                            if (mAudioEnable) {
                                // 最关键的是将解码后的数据，从缓冲区写入到AudioTrack对象中
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    mAudioTrack.write(mBufferReuse, 0, outLen[0], AudioTrack.WRITE_NON_BLOCKING);
                                } else {
                                    mAudioTrack.write(mBufferReuse, 0, outLen[0]);
                                }
                            }
                        }

                        frameInfo = null;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    am.abandonAudioFocus(l);// 放弃音频的焦点

                    if (handle != 0) {
                        AudioCodec.close(handle);
                    }

                    AudioTrack track = mAudioTrack;

                    // 4、停止播放，释放资源
                    if (track != null) {
                        synchronized (track) {
                            mAudioTrack = null;
                            track.release();// 释放本地 AudioTrack 对象。
                        }
                    }
                }
            }
        };

        mAudioThread.start();
    }

    /**
     * 根据 mineType 以及是否为编码器，选择出一个 MediaCodecInfo，然后初始化MediaCodec
     * */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            // 若为编码器，则直接进入下一次循环
            if (codecInfo.isEncoder()) {
                continue;
            }

            // 如果是解码器，判断是否支持Mime类型
            String[] types = codecInfo.getSupportedTypes();

            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }

        return null;
    }

    /**
     该方法主要是播放器上层用于缓存流媒体数据，使播放更加的平滑(https://blog.csdn.net/jinlong0603/article/details/85041569)

     @param sleepTimeUs 当前帧时间戳与前一帧时间戳的差值并去除了解码的耗时（单位是微秒）
     @param total 当前中缓存的时间长度（单位是微秒）
     @param delayUs 个人设置的缓存的总大小：
                        硬解码，设置的默认缓存为100000微秒，软解码，设置的是50000微秒。
                        如果想将延迟降到极限，就调整第三个参数为0，这样即不希望上层缓存数据，尽快的解码上屏显示。
     @return 延迟时间戳
     */
    private static final long fixSleepTime(long sleepTimeUs, long total, long delayUs) {
        if (total < 0l) {
            Log.w(TAG, String.format("total is:%d, this should not be happen.", total));
            total = 0;
        }

        double dValue = ((double) (delayUs - total)) / 1000000d;
        double radio = Math.exp(dValue);
        double r = sleepTimeUs * radio + 0.5f;

        Log.i(TAG, String.format("%d,%d,%d->%d微秒", sleepTimeUs, total, delayUs, (int) r));

        return (long) r;
    }

    private synchronized void pumpVideoSample(FrameInfo frameInfo) {
        EasyMuxer2 muxer2 = this.muxer2;
        if (muxer2 == null)
            return;

        if (mMuxerWaitingKeyVideo) {
            if (frameInfo.type == EASY_SDK_VIDEO_FRAME_I) {
                mMuxerWaitingKeyVideo = false;
            }
        }

        // 找到关键帧 才开始写入
        if (mMuxerWaitingKeyVideo) {
            Log.i(TAG, "writeFrame ignore due to no key frame!");
            return;
        }

//        if (frameInfo.type == EASY_SDK_VIDEO_FRAME_I) {
//            frameInfo.offset = 60;
//            frameInfo.length -= 60;
//        }

        int r = muxer2.writeFrame(EasyMuxer2.AVMEDIA_TYPE_VIDEO,
                frameInfo.buffer,
                frameInfo.offset,
                frameInfo.length,
                frameInfo.stamp / 1000);

        Log.i(TAG, "writeFrame video ret:" + r);
    }

    private synchronized void pumpPCMSample(byte[] pcm, int length, long stampUS) {
        if (i420callback != null && pcm != null) {
            i420callback.onPcmData(pcm);
        }

        EasyMuxer2 muxer2 = this.muxer2;
        if (muxer2 == null)
            return;

        int r = muxer2.writeFrame(EasyMuxer2.AVMEDIA_TYPE_AUDIO,
                pcm,
                0,
                length,
                stampUS / 1000);

        Log.i(TAG, "writeFrame audio ret：" + r);
    }

//    private void pumpAACSample(RTMPClient.FrameInfo frameInfo) {
//        EasyMuxer muxer = mObject;
//
//        if (muxer == null)
//            return;
//
//        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
//        bi.offset = frameInfo.offset;
//        bi.size = frameInfo.length;
//        ByteBuffer buffer = ByteBuffer.wrap(frameInfo.buffer, bi.offset, bi.size);
//        bi.presentationTimeUs = frameInfo.stamp;
//
//        try {
//            if (!frameInfo.audio) {
//                throw new IllegalArgumentException("frame should be audio!");
//            }
//
//            if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC) {
//                throw new IllegalArgumentException("audio codec should be aac!");
//            }
//
//            bi.offset += 7;
//            bi.size -= 7;
//            muxer.pumpStream(buffer, bi, false);
//        } catch (IllegalStateException ex) {
//            ex.printStackTrace();
//        }
//    }

    public interface I420DataCallback {
        void onI420Data(ByteBuffer buffer);
        public void onPcmData(byte[] pcm);
    }

    /*
    * 拉流获取到的视频帧/音频帧
    * */
    public void onRTMPSourceCallBack(int _channelId, int _channelPtr, int _frameType, FrameInfo frameInfo) {
        Thread.currentThread().setName("PRODUCER_THREAD");

        if (frameInfo != null) {
            mReceivedDataLength += frameInfo.length;
        }

        Log.i(TAG, "_frameType：" + _frameType);

        if (_frameType == RTMPClient.EASY_SDK_VIDEO_FRAME_FLAG) {
            // Log.d(TAG,String.format("receive video frame"));

            if (frameInfo.codec != EASY_SDK_VIDEO_CODEC_H264 && frameInfo.codec != EASY_SDK_VIDEO_CODEC_H265) {
                ResultReceiver rr = mRR;

                if (!mNotSupportedVideoCB && rr != null) {
                    mNotSupportedVideoCB = true;
                    rr.send(RESULT_UNSUPPORTED_VIDEO, null);
                }

                return;
            }

//            save2path(frameInfo.buffer, 0, frameInfo.length, "/sdcard/264.h264", true);
            if (frameInfo.width == 0 || frameInfo.height == 0) {
                return;
            }

            if (frameInfo.length >= 4) {
                if (frameInfo.buffer[0] == 0 &&
                        frameInfo.buffer[1] == 0 &&
                        frameInfo.buffer[2] == 0 &&
                        frameInfo.buffer[3] == 1) {
                    if (frameInfo.length >= 8) {
                        if (frameInfo.buffer[4] == 0 &&
                                frameInfo.buffer[5] == 0 &&
                                frameInfo.buffer[6] == 0 &&
                                frameInfo.buffer[7] == 1) {
                            frameInfo.offset += 4;
                            frameInfo.length -= 4;
                        }
                    }
                }
            }

//            int offset = frameInfo.offset;
//            byte nal_unit_type = (byte) (frameInfo.buffer[offset + 4] & (byte) 0x1F);
//            if (nal_unit_type == 7 || nal_unit_type == 5) {
//                Log.i(TAG,String.format("receive I frame"));
//            }

            if (frameInfo.type == EASY_SDK_VIDEO_FRAME_I) {
                Log.i(TAG, String.format("receive I frame"));
            }

//            boolean firstFrame = mNewestStamp == 0;
            mNewestStamp = frameInfo.stamp;
            frameInfo.audio = false;

            if (mWaitingKeyFrame) {
                ResultReceiver rr = mRR;
                Bundle bundle = new Bundle();
                bundle.putInt(EXTRA_VIDEO_WIDTH, frameInfo.width);
                bundle.putInt(EXTRA_VIDEO_HEIGHT, frameInfo.height);
                mWidth = frameInfo.width;
                mHeight = frameInfo.height;

                Log.i(TAG, String.format("width:%d,height:%d", mWidth, mHeight));

                if (frameInfo.codec == EASY_SDK_VIDEO_CODEC_H264) {
                    byte[] dataOut = new byte[128];
                    int[] outLen = new int[]{128};
                    int result = getXPS(frameInfo.buffer, 0, 256, dataOut, outLen, 7);

                    if (result >= 0) {
                        ByteBuffer csd0 = ByteBuffer.allocate(outLen[0]);
                        csd0.put(dataOut, 0, outLen[0]);
                        csd0.clear();
                        mCSD0 = csd0;
                        Log.i(TAG, String.format("CSD-0 searched"));
                    }

                    outLen[0] = 128;
                    result = getXPS(frameInfo.buffer, 0, 256, dataOut, outLen, 8);

                    if (result >= 0) {
                        ByteBuffer csd1 = ByteBuffer.allocate(outLen[0]);
                        csd1.put(dataOut, 0, outLen[0]);
                        csd1.clear();
                        mCSD1 = csd1;
                        Log.i(TAG, String.format("CSD-1 searched"));
                    }

//                    if (false) {
//                        int off = (result - frameInfo.offset);
//                        frameInfo.offset += off;
//                        frameInfo.length -= off;
//                    }
                } else {
                    byte[] spsPps = getVps_sps_pps(frameInfo.buffer, 0, 256);

                    if (spsPps != null) {
                        mCSD0 = ByteBuffer.wrap(spsPps);// wrap通过包装的方法创建的缓冲区保留了被包装数组内保存的数据.
                    }
                }

                Log.i(TAG, String.format("RESULT_VIDEO_SIZE:%d*%d", frameInfo.width, frameInfo.height));

                if (rr != null)
                    rr.send(RESULT_VIDEO_SIZE, bundle);

                if (frameInfo.type != EASY_SDK_VIDEO_FRAME_I) {
                    Log.w(TAG, String.format("discard p frame."));
                    return;
                }

                mWaitingKeyFrame = false;

                synchronized (this) {
//                    if (!TextUtils.isEmpty(mRecordingPath) && mObject == null) {
//                        startRecord(mRecordingPath);
//                    }
                    if (!TextUtils.isEmpty(mRecordingPath)) {
                        startRecord(mRecordingPath);
                    }
                }
            }

            Log.d(TAG, String.format("queue size :%d", mQueue.size()));

            try {
                mQueue.put(frameInfo);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (_frameType == RTMPClient.EASY_SDK_AUDIO_FRAME_FLAG) {
            mNewestStamp = frameInfo.stamp;
            frameInfo.audio = true;

            if (frameInfo.codec != EASY_SDK_AUDIO_CODEC_AAC &&
                    frameInfo.codec != EASY_SDK_AUDIO_CODEC_G711A &&
                    frameInfo.codec != EASY_SDK_AUDIO_CODEC_G711U &&
                    frameInfo.codec != EASY_SDK_AUDIO_CODEC_G726) {
                ResultReceiver rr = mRR;

                if (!mNotSupportedAudioCB && rr != null) {
                    mNotSupportedAudioCB = true;

                    if (rr != null) {
                        rr.send(RESULT_UNSUPPORTED_AUDIO, null);
                    }
                }

                return;
            }

            Log.d(TAG, String.format("queue size :%d", mQueue.size()));

            try {
                mQueue.put(frameInfo);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (_frameType == 0) {
            if (!mTimeout) {// time out...
                mTimeout = true;
                ResultReceiver rr = mRR;

                if (rr != null)
                    rr.send(RESULT_TIMEOUT, null);
            }
        } else if (_frameType == RTMPClient.EASY_SDK_EVENT_FRAME_FLAG) {
            ResultReceiver rr = mRR;
            Bundle resultData = new Bundle();
            resultData.putString("event-msg", new String(frameInfo.buffer));

            if (rr != null) {
                rr.send(RESULT_EVENT, null);
            }
        }
    }

    private static byte[] getVps_sps_pps(byte[] data, int offset, int length) {
        int i, vps = -1, sps = -1, pps = -1;

        length = Math.min(length, data.length);

        do {
            if (vps == -1) {
                for (i = offset; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;

                        if (nal_type == NAL_VPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                vps = i - 1;
                            } else {                    // start with 00 00 01
                                vps = i;
                            }

                            break;
                        }
                    }
                }
            }

            if (sps == -1) {
                for (i = vps; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;

                        if (nal_type == NAL_SPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                sps = i - 1;
                            } else {                    // start with 00 00 01
                                sps = i;
                            }

                            break;
                        }
                    }
                }
            }

            if (pps == -1) {
                for (i = sps; i < length - 4; i++) {
                    if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                        byte nal_spec = data[i + 3];
                        int nal_type = (nal_spec >> 1) & 0x03f;

                        if (nal_type == NAL_PPS) {
                            // vps found.
                            if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                                pps = i - 1;
                            } else {                    // start with 00 00 01
                                pps = i;
                            }
                            break;
                        }
                    }
                }
            }
        } while (vps == -1 || sps == -1 || pps == -1);

        if (vps == -1 || sps == -1 || pps == -1) {// 没有获取成功。
            return null;
        }

        // 计算csd buffer的长度。即从vps的开始到pps的结束的一段数据
        int begin = vps;
        int end = -1;

        for (i = pps + 4; i < length - 4; i++) {
            if ((0x00 == data[i]) && (0x00 == data[i + 1]) && (0x01 == data[i + 2])) {
                if (data[i - 1] == 0x00) {  // start with 00 00 00 01
                    end = i - 1;
                } else {                    // start with 00 00 01
                    end = i;
                }

                break;
            }
        }

        if (end == -1 || end < begin) {
            return null;
        }

        // 拷贝并返回
        byte[] buf = new byte[end - begin];
        System.arraycopy(data, begin, buf, 0, buf.length);
        return buf;
    }

    /*
    * H264
    * NALU_TYPE_SPS = 7,
    * NALU_TYPE_PPS = 8,
	*/
    private static int getXPS(byte[] data, int offset, int length, byte[] dataOut, int[] outLen, int type) {
        int i, pos0 = -1, pos1 = -1;

        length = Math.min(length, data.length);

        for (i = offset; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2]) && (type == (0x0F & data[i + 3]))) {
                pos0 = i;
                break;
            }
        }

        if (-1 == pos0) {
            return -1;
        }

        if (pos0 > 0 && data[pos0 - 1] == 0) { // 0 0 0 1
            pos0 = pos0 - 1;
        }

        for (i = pos0 + 4; i < length - 4; i++) {
            if ((0 == data[i]) && (0 == data[i + 1]) && (1 == data[i + 2])) {
                pos1 = i;
                break;
            }
        }

        if (-1 == pos1 || pos1 == 0) {
            return -2;
        }

        if (data[pos1 - 1] == 0) {
            pos1 -= 1;
        }

        if (pos1 - pos0 > outLen[0]) {
            return -3; // 输入缓冲区太小
        }

        dataOut[0] = 0;
        System.arraycopy(data, pos0, dataOut, 0, pos1 - pos0);
        // memcpy(pXPS+1, pES+pos0, pos1-pos0);
        // *pMaxXPSLen = pos1-pos0+1;
        outLen[0] = pos1 - pos0;
        return pos1;
    }

    /* =========================== SourceCallBack =========================== */

    @Override
    public void onMediaInfoCallBack(int _channelId, RTMPClient.MediaInfo mi) {
        mMediaInfo = mi;
        Log.i(TAG, String.format("MediaInfo fetch %s %d", mi, _channelId));
    }

    @Override
    public void onEvent(int channel, int err, int state) {
        ResultReceiver rr = mRR;
        Bundle resultData = new Bundle();
        resultData.putInt("state", state);

        switch (state) {
            case 1:
                resultData.putString("event-msg", "连接中...");
                break;
            case 2:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("错误：%d", err));
                break;
            case 3:
                resultData.putInt("errorcode", err);
                resultData.putString("event-msg", String.format("线程退出。%d", err));
                break;
        }
        if (rr != null) rr.send(RESULT_EVENT, resultData);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onSourceCallBack(int _channelId, int _channelPtr, int _frameType, FrameInfo frameInfo) {
        long begin = SystemClock.elapsedRealtime();

        try {
            onRTMPSourceCallBack(_channelId, _channelPtr, _frameType, frameInfo);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            Log.d(TAG, String.format("onRTMPSourceCallBack %d", SystemClock.elapsedRealtime() - begin));
        }
    }

//    /**
//     * 启动播放
//     *
//     * @param url
//     * @param type
//     * @param mediaType
//     * @param user
//     * @param pwd
//     * @return
//     */
//    public int start(final String url, int type, int mediaType, String user, String pwd) {
//        return start(url, type, mediaType, user, pwd, null);
//    }

//    private static int getSampleIndex(int sample) {
//        for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE.length; i++) {
//            if (sample == AUDIO_SPECIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    private void pumpVideoSample1(RTMPClient.FrameInfo frameInfo) {
//        EasyMuxer muxer = mObject;
//
//        if (muxer == null)
//            return;
//
//        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
//        bi.offset = frameInfo.offset;
//        bi.size = frameInfo.length;
//        ByteBuffer buffer = ByteBuffer.wrap(frameInfo.buffer, bi.offset, bi.size);
//        bi.presentationTimeUs = frameInfo.stamp;
//
//        try {
//            if (frameInfo.audio) {
//                throw new IllegalArgumentException("frame should be video!");
//            }
//
//            if (frameInfo.type != 1) {
//                bi.flags = 0;
//            } else {
//                bi.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
//            }
//
//            muxer.pumpStream(buffer, bi, true);
//        } catch (IllegalStateException ex) {
//            ex.printStackTrace();
//        }
//    }

//    public synchronized void stopRecord1() {
//        mRecordingPath = null;
//
//        if (mObject == null)
//            return;
//
//        mObject.release();
//        mObject = null;
//        ResultReceiver rr = mRR;
//
//        if (rr != null) {
//            rr.send(RESULT_RECORD_END, null);
//        }
//    }

//    private void pumpPCMSample1(byte[] pcm, int length, long stampUS) {
//        EasyAACMuxer muxer = mObject;
//
//        if (muxer == null)
//            return;
//
//        try {
//            muxer.pumpPCMStream(pcm, length, stampUS);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
//    public synchronized void startRecord1(String path) {
//        if (mMediaInfo == null || mWidth == 0 || mHeight == 0 || mCSD0 == null || mCSD1 == null)
//            return;
//        mRecordingPath = path;
//        mObject = new EasyAACMuxer(path, mMediaInfo.sample != 0, Integer.MAX_VALUE);
//        MediaFormat format = new MediaFormat();
//        format.setInteger(MediaFormat.KEY_WIDTH, mWidth);
//        format.setInteger(MediaFormat.KEY_HEIGHT, mHeight);
//        mCSD0.clear();
//        format.setByteBuffer("csd-0", mCSD0);
//        mCSD1.clear();
//        format.setByteBuffer("csd-1", mCSD1);
//        format.setString(MediaFormat.KEY_MIME, "video/avc");
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, 0);
////        format.setInteger(MediaFormat.KEY_BIT_RATE, mWidth*mHeight*0.7*2);
//        mObject.addTrack(format, true);
//
//        format = new MediaFormat();
//        int audioObjectType = 2;
//        int sampleRateIndex = getSampleIndex(mMediaInfo.sample);
//        if (sampleRateIndex > 0) {
//            int channelConfig = mMediaInfo.channel;
//            byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAacAudioSpecificConfig(audioObjectType, sampleRateIndex, channelConfig);
//            Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAacAudioSpecificConfig(audioSpecificConfig);
////                                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
//            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
//            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioParams.second);
//            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioParams.first);
//
//            List<byte[]> bytes = Collections.singletonList(audioSpecificConfig);
//            for (int j = 0; j < bytes.size(); j++) {
//                format.setByteBuffer("csd-" + j, ByteBuffer.wrap(bytes.get(j)));
//            }
//            mObject.addTrack(format, false);
//        }
//        ResultReceiver rr = mRR;
//        if (rr != null) {
//            rr.send(RESULT_RECORD_BEGIN, null);
//        }
//    }

//    private static String codecName() {
//        ArrayList<String> array = new ArrayList<>();
//        int numCodecs = MediaCodecList.getCodecCount();
//        for (int i1 = 0; i1 < numCodecs; i1++) {
//            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i1);
//
//            if (codecInfo.isEncoder()) {
//                continue;
//            }
//
//            if (codecMatch("video/avc", codecInfo)) {
//                String name = codecInfo.getName();
//                Log.d("DECODER", String.format("decoder:%s", name));
//                array.add(name);
//            }
//        }
////        if (array.remove("OMX.qcom.video.decoder.avc")) {
////            array.add("OMX.qcom.video.decoder.avc");
////        }
////        if (array.remove("OMX.amlogic.avc.decoder.awesome")) {
////            array.add("OMX.amlogic.avc.decoder.awesome");
////        }
//
//        if (array.isEmpty()) {
//            return "";
//        }
//
//        return array.get(0);
//    }

//    private static void save2path(byte[] buffer, int offset, int length, String path, boolean append) {
//        FileOutputStream fos = null;
//        try {
//            fos = new FileOutputStream(path, append);
//            fos.write(buffer, offset, length);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (fos != null) {
//                try {
//                    fos.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//    }

//    private static boolean codecMatch(String mimeType, MediaCodecInfo codecInfo) {
//        String[] types = codecInfo.getSupportedTypes();
//        for (String type : types) {
//            if (type.equalsIgnoreCase(mimeType)) {
//                return true;
//            }
//        }
//        return false;
//    }
}
