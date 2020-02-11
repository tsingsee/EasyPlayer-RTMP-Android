package org.easydarwin.easyplayer.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.easydarwin.easyplayer.R;
import org.easydarwin.easyplayer.fragments.PlayRTMPFragment;
import org.easydarwin.easyplayer.fragments.YUVExportRTMPFragment;
import org.easydarwin.easyplayer.util.SPUtil;
import org.easydarwin.video.RTMPClient;

public class YUVExportRTMPActivity extends AppCompatActivity {

    private YUVExportRTMPFragment mRenderFragment;
    private int i = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra("play_url");
        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_yuv_rtmp);

        if (savedInstanceState == null) {
            boolean useUDP = SPUtil.getUDPMode(this);

            YUVExportRTMPFragment fragment = YUVExportRTMPFragment.newInstance(url, useUDP ? RTMPClient.TRANS_TYPE_UDP : RTMPClient.TRANS_TYPE_TCP, null);
            fragment.setScaleType(PlayRTMPFragment.ASPECT_RATIO_CROPS_MATRIX);
            getSupportFragmentManager().beginTransaction().add(R.id.render_holder, fragment,"first").commit();
            mRenderFragment = fragment;
        } else {
            mRenderFragment = (YUVExportRTMPFragment) getSupportFragmentManager().findFragmentByTag("first");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onToggleAspectRatio(View view) {
        YUVExportRTMPFragment f =mRenderFragment;

        if (f == null)
            return;

        f.setScaleType(++i);

        switch (i) {
            case PlayRTMPFragment.ASPECT_RATIO_INSIDE: {
                Toast.makeText(this,"等比例居中",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayRTMPFragment.ASPECT_RATIO_CENTER_CROPS: {
                Toast.makeText(this,"等比例居中裁剪视频",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayRTMPFragment.FILL_WINDOW:{
                Toast.makeText(this,"拉伸视频,铺满区域",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayRTMPFragment.ASPECT_RATIO_CROPS_MATRIX:{
                Toast.makeText(this,"等比例显示视频,可拖拽显示隐藏区域.",Toast.LENGTH_SHORT).show();
            }
            break;
        }

        if (i == PlayRTMPFragment.FILL_WINDOW) {
            i = 0;
        }
    }

    public void onToggleDraw(View view) {
        YUVExportRTMPFragment f =mRenderFragment;
        if (f == null) return;
        f.toggleDraw();
    }
}
