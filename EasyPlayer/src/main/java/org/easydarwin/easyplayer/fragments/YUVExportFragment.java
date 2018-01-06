package org.easydarwin.easyplayer.fragments;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import org.easydarwin.easyplayer.TheApp;
import org.easydarwin.easyplayer.views.OverlayCanvasView;
import org.easydarwin.video.Client;
import org.easydarwin.video.EasyPlayerClient;
import org.esaydarwin.rtsp.player.R;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by apple on 2017/12/30.
 */

public class YUVExportFragment extends PlayFragment implements EasyPlayerClient.I420DataCallback{

    OverlayCanvasView canvas;
    public static YUVExportFragment newInstance(String url, int type, ResultReceiver rr) {
        YUVExportFragment fragment = new YUVExportFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, url);
        args.putInt(ARG_PARAM2, type);
        args.putParcelable(ARG_PARAM3, rr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_play_overlay_canvas, container, false);
        cover = (ImageView) view.findViewById(R.id.surface_cover);
        canvas = view.findViewById(R.id.overlay_canvas);

        return view;
    }

    @Override
    protected void startRending(SurfaceTexture surface) {
        mStreamRender = new EasyPlayerClient(getContext(), KEY, new Surface(surface), mResultReceiver, this);

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

    @Override
    public void onI420Data(ByteBuffer buffer) {
        Log.i(TAG, "I420 data length :" + buffer.capacity());



    }

    @Override
    public void onMatrixChanged(Matrix matrix, RectF rect) {
        super.onMatrixChanged(matrix, rect);
        if (canvas != null) {
            canvas.setTransMatrix(matrix);
        }
    }

    public void toggleDraw() {
        canvas.toggleDrawable();
    }
}
