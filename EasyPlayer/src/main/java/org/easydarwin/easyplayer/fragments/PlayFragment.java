package org.easydarwin.easyplayer.fragments;


import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.StringSignature;

import org.easydarwin.easyplayer.PlayActivity;
import org.easydarwin.easyplayer.PlaylistActivity;
import org.easydarwin.easyplayer.TheApp;
import org.easydarwin.easyplayer.views.AngleView;
import org.easydarwin.video.EasyPlayerClient;
import org.easydarwin.video.Client;
import org.esaydarwin.rtsp.player.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import uk.copywitchshame.senab.photoview.gestures.PhotoViewAttacher;

public class PlayFragment extends Fragment implements TextureView.SurfaceTextureListener, PhotoViewAttacher.OnMatrixChangedListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    public static final String ARG_PARAM1 = "param1";
    public static final String ARG_PARAM2 = "param2";
    public static final String ARG_PARAM3 = "param3";
    public static final int RESULT_REND_STARTED = 1;
    public static final int RESULT_REND_VIDEO_DISPLAYED = 2;
    public static final int RESULT_REND_STOPED = -1;

    protected static final String TAG = "PlayFragment";

    /**
     * 等比例,最大化区域显示,不裁剪
     */
    public static final int ASPACT_RATIO_INSIDE =  1;
    /**
     * 等比例,裁剪,裁剪区域可以通过拖拽展示\隐藏
     */
    public static final int ASPACT_RATIO_CROPE_MATRIX =  2;
    /**
     * 等比例,最大区域显示,裁剪
     */
    public static final int ASPACT_RATIO_CENTER_CROPE =  3;
    /**
     * 拉伸显示,铺满全屏
     */
    public static final int FILL_WINDOW =  4;


    /* 本Key为3个月临时授权License，如需商业使用或者更改applicationId，请邮件至support@easydarwin.org申请此产品的授权。
     */
//    public static final String KEY = "79393674363536526D343041484339617064446A70655A76636D63755A57467A65575268636E64706269356C59584E356347786865575679567778576F502B6C34456468646D6C754A6B4A68596D397A595541794D4445325257467A65555268636E6470626C526C5957316C59584E35";
    public static final String KEY = "59617A414C5A36526D3432416855566170627035792B4676636D63755A57467A65575268636E64706269356C59584E3563477868655756794C6E4A3062584170567778576F50394C34456468646D6C754A6B4A68596D397A595541794D4445325257467A65555268636E6470626C526C5957316C59584E35";


    // TODO: Rename and change types of parameters
    protected String mUrl;
    protected int mType;

    protected EasyPlayerClient mStreamRender;
    protected ResultReceiver mResultReceiver;
    protected int mWidth;
    protected int mHeight;
    protected View.OnLayoutChangeListener listener;
    private PhotoViewAttacher mAttacher;
    protected TextureView mSurfaceView;
    private AngleView mAngleView;
    private MediaScannerConnection mScanner;
    private ImageView mRenderCover;
    private AsyncTask<Void, Void, Bitmap> mLoadingPictureThumbTask;
    private ImageView mTakePictureThumb;
    private ResultReceiver mRR;
    protected ImageView cover;
    /**
     * 抓拍后隐藏thumb的task
     */
    private final Runnable mAnimationHiddenTakepictureThumbTask = new Runnable() {
        @Override
        public void run() {
            ViewCompat.animate(mTakePictureThumb).scaleX(0.0f).scaleY(0.0f).setListener(new ViewPropertyAnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(View view) {
                    super.onAnimationEnd(view);
                    view.setVisibility(View.INVISIBLE);
                }
            });
        }
    };
    private boolean mFullscreenMode;
    private int mRatioType = ASPACT_RATIO_CROPE_MATRIX;

    public void setSelected(boolean selected) {
        mSurfaceView.animate().scaleX(selected ? 0.9f : 1.0f);
        mSurfaceView.animate().scaleY(selected ? 0.9f : 1.0f);
        mSurfaceView.animate().alpha(selected ? 0.7f : 1.0f);
    }

    public long getReceivedStreamLength() {
        if (mStreamRender != null) {
            return mStreamRender.receivedDataLength();
        }
        return 0;
    }

    public boolean onRecordOrStop() {
        if (!mStreamRender.isRecording()) {
            File f = new File(TheApp.sMoviePath);
            f.mkdirs();
            mStreamRender.startRecord(new File(f, new SimpleDateFormat("yy_MM_dd-HH_mm_ss").format(new Date()) + ".mp4").getPath());
            return true;
        } else {
            mStreamRender.stopRecord();
            return false;
        }
    }

    public static class ReverseInterpolator extends AccelerateDecelerateInterpolator {
        @Override
        public float getInterpolation(float paramFloat) {
            return super.getInterpolation(1.0f - paramFloat);
        }
    }

    public static PlayFragment newInstance(String url, int type, ResultReceiver rr) {
        PlayFragment fragment = new PlayFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, url);
        args.putInt(ARG_PARAM2, type);
        args.putParcelable(ARG_PARAM3, rr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUrl = getArguments().getString(ARG_PARAM1);
            mType = getArguments().getInt(ARG_PARAM2);
            mRR = getArguments().getParcelable(ARG_PARAM3);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_play, container, false);
//        view.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                PlayActivity activity = (PlayActivity) getActivity();
//                activity.onPlayFragmentClicked(PlayFragment.this);
//            }
//        });

        cover = (ImageView) view.findViewById(R.id.surface_cover);
//        Glide.with(this).load(PlaylistActivity.url2localPosterFile(getActivity(), mUrl)).diskCacheStrategy(DiskCacheStrategy.NONE).placeholder(R.drawable.placeholder).into(new ImageViewTarget<GlideDrawable>(cover) {
//            @Override
//            protected void setResource(GlideDrawable resource) {
//                int width = resource.getIntrinsicWidth();
//                int height = resource.getIntrinsicHeight();
//                fixPlayerRatio(view, container.getWidth(), container.getHeight(), width, height);
//                cover.setImageDrawable(resource);
//            }
//        });

        if (!TextUtils.isEmpty(mUrl)) {
            Glide.with(this).load(PlaylistActivity.url2localPosterFile(getActivity(), mUrl)).signature(new StringSignature(UUID.randomUUID().toString())).fitCenter().into(cover);
        }
        return view;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSurfaceView = (TextureView) view.findViewById(R.id.surface_view);




        mSurfaceView.setOpaque(false);
        mSurfaceView.setSurfaceTextureListener(this);
        mAngleView = (AngleView) getView().findViewById(R.id.render_angle_view);
        mRenderCover = (ImageView) getView().findViewById(R.id.surface_cover);
        mTakePictureThumb = (ImageView) getView().findViewById(R.id.live_video_snap_thumb);
        mResultReceiver = new ResultReceiver(new Handler()) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                super.onReceiveResult(resultCode, resultData);
                Activity activity = getActivity();
                if (activity == null)return;
                if (resultCode == EasyPlayerClient.RESULT_VIDEO_DISPLAYED) {

                    onVideoDisplayed();
                } else if (resultCode == EasyPlayerClient.RESULT_VIDEO_SIZE) {
                    mWidth = resultData.getInt(EasyPlayerClient.EXTRA_VIDEO_WIDTH);
                    mHeight = resultData.getInt(EasyPlayerClient.EXTRA_VIDEO_HEIGHT);


                    onVideoSizeChange();
                } else if (resultCode == EasyPlayerClient.RESULT_TIMEOUT) {
                    new AlertDialog.Builder(getActivity()).setMessage("试播时间到").setTitle("SORRY").setPositiveButton(android.R.string.ok, null).show();
                } else if (resultCode == EasyPlayerClient.RESULT_UNSUPPORTED_AUDIO) {
                    new AlertDialog.Builder(getActivity()).setMessage("音频格式不支持").setTitle("SORRY").setPositiveButton(android.R.string.ok, null).show();
                } else if (resultCode == EasyPlayerClient.RESULT_UNSUPPORTED_VIDEO) {
                    new AlertDialog.Builder(getActivity()).setMessage("视频格式不支持").setTitle("SORRY").setPositiveButton(android.R.string.ok, null).show();
                }else if (resultCode == EasyPlayerClient.RESULT_EVENT){

                    int errorcode = resultData.getInt("errorcode");
//                    if (errorcode != 0){
//                        stopRending();
//                    }
                    if (activity instanceof PlayActivity) {
                        String msg = resultData.getString("event-msg");
                        if (!TextUtils.isEmpty(msg)) {
                            ((PlayActivity) activity).onEvent(PlayFragment.this, errorcode, msg);
                        }
                    }
                }else if (resultCode == EasyPlayerClient.RESULT_RECORD_BEGIN){
                    if (activity instanceof PlayActivity)
                        ((PlayActivity)activity).onRecordState(1);
                }else if (resultCode == EasyPlayerClient.RESULT_RECORD_END){
                    if (activity instanceof PlayActivity)
                        ((PlayActivity)activity).onRecordState(-1);
                }
            }
        };

        listener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                Log.d(TAG, String.format("onLayoutChange left:%d,top:%d,right:%d,bottom:%d->oldLeft:%d,oldTop:%d,oldRight:%d,oldBottom:%d", left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom));
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    if (!isLandscape()) {
                        fixPlayerRatio(view, right - left, bottom - top);
                    } else {
                        PlayActivity activity = (PlayActivity) getActivity();
                        if (!activity.multiWindows()) {
                            view.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                            view.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                            view.requestLayout();
                        } else {
                            fixPlayerRatio(view, right - left, bottom - top);
                        }
                    }
                }
            }
        };
        ViewGroup parent = (ViewGroup) view.getParent();
        parent.addOnLayoutChangeListener(listener);

        if (mFullscreenMode) enterFullscreen();else quiteFullscreen();
    }



    public void enterFullscreen() {
        mFullscreenMode = true;
        if (getView() == null){
            return;
        }
        if (mAttacher != null) {
            mAttacher.cleanup();
        } mSurfaceView.setTransform(new Matrix());
        mAngleView.setVisibility(View.GONE);
    }

    public void quiteFullscreen() {
        mFullscreenMode = false;
        if (getView() == null){
            return;
        }
        if (mAttacher != null) {
            mAttacher.cleanup();
        }
        ViewGroup parent = (ViewGroup) getView().getParent();
        parent.addOnLayoutChangeListener(listener);
        fixPlayerRatio(getView(), parent.getWidth(), parent.getHeight());


        mAttacher = new PhotoViewAttacher(mSurfaceView, mWidth, mHeight);
        mAttacher.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mAttacher.setOnMatrixChangeListener(PlayFragment.this);
        mAttacher.update();
        mAngleView.setVisibility(View.VISIBLE);
    }

    private void onVideoSizeChange() {
        Log.i(TAG, String.format("RESULT_VIDEO_SIZE RECEIVED :%d*%d", mWidth, mHeight));
        if (mWidth == 0 || mHeight == 0) return;
        if (mAttacher != null){
            mAttacher.cleanup();
            mAttacher = null;
        }
        if (mRatioType == ASPACT_RATIO_CROPE_MATRIX) {
            ViewGroup parent = (ViewGroup) getView().getParent();
            parent.addOnLayoutChangeListener(listener);
            fixPlayerRatio(getView(), parent.getWidth(), parent.getHeight());
            mSurfaceView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            mSurfaceView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            mAttacher = new PhotoViewAttacher(mSurfaceView, mWidth, mHeight);
            mAttacher.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mAttacher.setOnMatrixChangeListener(PlayFragment.this);
            mAttacher.update();
            mAngleView.setVisibility(View.VISIBLE);
        }else {
            mSurfaceView.setTransform(new Matrix());
            mAngleView.setVisibility(View.GONE);
//            int viewWidth = mSurfaceView.getWidth();
//            int viewHeight = mSurfaceView.getHeight();
            float ratioView = getView().getWidth() * 1.0f/getView().getHeight();
            float ratio = mWidth * 1.0f/mHeight;

            switch (mRatioType){
                case ASPACT_RATIO_INSIDE: {

                    if (ratioView - ratio < 0){    // 屏幕比视频的宽高比更小.表示视频是过于宽屏了.
                        // 宽为基准.
                        mSurfaceView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                        mSurfaceView.getLayoutParams().height = (int) (getView().getWidth() / ratio + 0.5f);
                    }else{                          // 视频是竖屏了.
                        mSurfaceView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                        mSurfaceView.getLayoutParams().width = (int) (getView().getHeight() * ratio + 0.5f);
                    }
                }
                    break;
                case ASPACT_RATIO_CENTER_CROPE: {
                    // 以更短的为基准
                    if (ratioView - ratio < 0){    // 屏幕比视频的宽高比更小.表示视频是过于宽屏了.
                        // 宽为基准.
                        mSurfaceView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                        mSurfaceView.getLayoutParams().width = (int) (getView().getHeight() * ratio+ 0.5f);
                    }else{                          // 视频是竖屏了.
                        mSurfaceView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                        mSurfaceView.getLayoutParams().height = (int) (getView().getWidth() / ratio+ 0.5f);
                    }
                }
                    break;
                case FILL_WINDOW:{
                    mSurfaceView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    mSurfaceView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                    break;
            }
        }
        mSurfaceView.requestLayout();
    }

    private void onVideoDisplayed() {
        View view = getView();
        Log.i(TAG, String.format("VIDEO DISPLAYED!!!!%d*%d", mWidth, mHeight));
//                    Toast.makeText(PlayActivity.this, "视频正在播放了", Toast.LENGTH_SHORT).show();
        view.findViewById(android.R.id.progress).setVisibility(View.GONE);
        mSurfaceView.post(new Runnable() {
            @Override
            public void run() {
                if (mWidth != 0 && mHeight != 0) {
                    Bitmap e = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
                    mSurfaceView.getBitmap(e);
                    File f = PlaylistActivity.url2localPosterFile(mSurfaceView.getContext(), mUrl);
                    saveBitmapInFile(f.getPath(), e);
                    e.recycle();
                }
            }
        });
        cover.setVisibility(View.GONE);
        sendResult(RESULT_REND_VIDEO_DISPLAYED, null);

    }

    @Override
    public void onDestroyView() {
        ViewGroup parent = (ViewGroup) getView().getParent();
        parent.removeOnLayoutChangeListener(listener);
        super.onDestroyView();
    }

    protected boolean isLandscape() {
        return getActivity().getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || getActivity().getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    }

    protected void fixPlayerRatio(View renderView, int widthSize, int heightSize, int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }
        float aspectRatio = width * 1.0f / height;
        if (widthSize > heightSize * aspectRatio) {
            height = heightSize;
            width = (int) (height * aspectRatio);
        } else {
            width = widthSize;
            height = (int) (width / aspectRatio);
        }
        renderView.getLayoutParams().width = width;
        renderView.getLayoutParams().height = height;
        renderView.requestLayout();
    }

    /**
     * 高度固定，宽度可更改；
     *
     * @param renderView
     * @param maxWidth
     * @param maxHeight
     */
    protected void fixPlayerRatio(View renderView, int maxWidth, int maxHeight) {
//        fixPlayerRatio(renderView, maxWidth, maxHeight, mWidth, mHeight);
    }

    protected void startRending(SurfaceTexture surface) {
        mStreamRender = new EasyPlayerClient(getContext(), KEY, new Surface(surface), mResultReceiver);

        boolean autoRecord = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("auto_record", false);

        File f = new File(TheApp.sMoviePath);
        f.mkdirs();

        try {
            mStreamRender.start(mUrl, mType, Client.EASY_SDK_VIDEO_FRAME_FLAG | Client.EASY_SDK_AUDIO_FRAME_FLAG, "", "", autoRecord ? new File(f, new SimpleDateFormat("yy-MM-dd HH:mm:ss").format(new Date()) + ".mp4").getPath() : null);
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        sendResult(RESULT_REND_STARTED, null);

    }

    protected void sendResult(int resultCode, Bundle resultData) {
        if (mRR != null)
        mRR.send(resultCode, resultData);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//        if (isHidden()){
//            return;
//        }
        startRending(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mAttacher != null) {
            mAttacher.update();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopRending();

        return true;
    }

    private void stopRending() {
        if (mStreamRender != null) {
            sendResult(RESULT_REND_STOPED, null);
            mStreamRender.stop();
            mStreamRender = null;
        }
    }



    /**
     * Called when the fragment is no longer in use.  This is called
     * after {@link #onStop()} and before {@link #onDetach()}.
     */
    @Override
    public void onDestroy() {
        stopRending();
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onMatrixChanged(Matrix matrix, RectF rect) {
        float maxMovement = (rect.width() - mSurfaceView.getWidth());
        float middle = mSurfaceView.getWidth() * 0.5f + mSurfaceView.getLeft();
        float currentMiddle = rect.width() * 0.5f + rect.left;
        mAngleView.setCurrentProgress(-(int) ((currentMiddle - middle) * 100 / maxMovement));
    }

    /**
     * 抓拍
     * @param path
     */
    public void takePicture(final String path) {
        try {
            if (mWidth <= 0 || mHeight <= 0) {
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
            mSurfaceView.getBitmap(bitmap);
            saveBitmapInFile(path, bitmap);
            bitmap.recycle();

            mRenderCover.setImageDrawable(new ColorDrawable(getResources().getColor(android.R.color.white)));
            mRenderCover.setVisibility(View.VISIBLE);
            mRenderCover.setAlpha(1.0f);
            ViewCompat.animate(mRenderCover).cancel();
            ViewCompat.animate(mRenderCover).alpha(0.3f).setListener(new ViewPropertyAnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(View view) {
                    super.onAnimationEnd(view);
                    mRenderCover.setVisibility(View.GONE);
                }
            });
            if (mLoadingPictureThumbTask != null) mLoadingPictureThumbTask.cancel(true);
            final int w = mTakePictureThumb.getWidth();
            final int h = mTakePictureThumb.getHeight();
            mLoadingPictureThumbTask = new AsyncTask<Void, Void, Bitmap>() {
                final WeakReference<ImageView> mImageViewRef = new WeakReference<>(mTakePictureThumb);
                final String mPath = path;

                @Override
                protected Bitmap doInBackground(Void... params) {
                    return decodeSampledBitmapFromResource(mPath, w, h);
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    super.onPostExecute(bitmap);
                    if (isCancelled()) {
                        bitmap.recycle();
                        return;
                    }
                    ImageView iv = mImageViewRef.get();
                    if (iv == null) return;
                    iv.setImageBitmap(bitmap);
                    iv.setVisibility(View.VISIBLE);
                    iv.removeCallbacks(mAnimationHiddenTakepictureThumbTask);
                    iv.clearAnimation();
                    ViewCompat.animate(iv).scaleX(1.0f).scaleY(1.0f).setListener(new ViewPropertyAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(View view) {
                            super.onAnimationEnd(view);
                            view.postOnAnimationDelayed(mAnimationHiddenTakepictureThumbTask, 4000);
                        }
                    });
                    iv.setTag(mPath);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } catch (OutOfMemoryError error) {
            error.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap decodeSampledBitmapFromResource(String path,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void saveBitmapInFile(final String path, Bitmap bitmap) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            if (mScanner == null) {
                MediaScannerConnection connection = new MediaScannerConnection(getContext(),
                        new MediaScannerConnection.MediaScannerConnectionClient() {
                            public void onMediaScannerConnected() {
                                mScanner.scanFile(path, null /* mimeType */);
                            }

                            public void onScanCompleted(String path1, Uri uri) {
                                if (path1.equals(path)) {
                                    mScanner.disconnect();
                                    mScanner = null;
                                }
                            }
                        });
                try {
                    connection.connect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mScanner = connection;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean toggleAudioEnable() {
        if (mStreamRender == null) {
            return false;
        }
        mStreamRender.setAudioEnable(!mStreamRender.isAudioEnable());
        return mStreamRender.isAudioEnable();
    }

    public void setUrl(String url) {
        this.mUrl = url;
        if (!TextUtils.isEmpty(mUrl)) {
            Glide.with(this).load(PlaylistActivity.url2localPosterFile(getActivity(), mUrl)).signature(new StringSignature(UUID.randomUUID().toString())).fitCenter().into(cover);
        }
    }

    public void setTransType(int transType) {
        this.mType = transType;
    }

    public void setResultReceiver(ResultReceiver rr) {
        mRR = rr;
    }

    public boolean isAudioEnable() {
        return mStreamRender != null && mStreamRender.isAudioEnable();
    }

    public void setScaleType(@IntRange(from = ASPACT_RATIO_INSIDE, to = FILL_WINDOW) int type){
        mRatioType = type;
        if (mWidth != 0 && mHeight != 0){
            onVideoSizeChange();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden){
            // stop
//            stopRending();
            if (mStreamRender != null) {
                mStreamRender.pause();
            }
        }else{
            if (mStreamRender != null) {
                mStreamRender.resume();
            }
        }
    }
}
