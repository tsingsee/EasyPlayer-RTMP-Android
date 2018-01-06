package org.easydarwin.easyplayer;

import android.databinding.DataBindingUtil;
import android.media.SoundPool;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.easydarwin.easyplayer.fragments.PlayFragment;
import org.easydarwin.video.Client;
import org.esaydarwin.rtsp.player.R;
import org.esaydarwin.rtsp.player.databinding.ActivityTwoWndPlayBinding;

public class TwoWndPlayActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0x111;
    private GestureDetectorCompat mDetector;
    private SoundPool mSoundPool;
    private int mTalkPictureSound;
    private int mActionStartSound;
    private int mActionStopSound;
    private PlayFragment mRenderFragment;
    private float mAudioVolumn;
    private float mMaxVolume;
    private ActivityTwoWndPlayBinding mBinding;
    private long mLastReceivedLength;
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

        if (savedInstanceState == null) {
            boolean useUDP = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_udp_mode), false);
            PlayFragment fragment = PlayFragment.newInstance("rtsp://cloud.easydarwin.org:554/uvc_956.sdp", useUDP ? Client.TRANSTYPE_UDP : Client.TRANSTYPE_TCP, null);
            getSupportFragmentManager().beginTransaction().add(R.id.render_holder, fragment,"second").hide(fragment).commit();
            fragment.setScaleType(++i);

            fragment = PlayFragment.newInstance(url, useUDP ? Client.TRANSTYPE_UDP : Client.TRANSTYPE_TCP, null);
            fragment.setScaleType(++i);
            getSupportFragmentManager().beginTransaction().add(R.id.render_holder, fragment,"first").commit();
            mRenderFragment = fragment;
        } else {
            mRenderFragment = (PlayFragment) getSupportFragmentManager().findFragmentByTag("first");
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_two_wnd_play);

    }

    public void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.add_url) {

        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void onSwitchPlayer(View view) {
        PlayFragment f = (PlayFragment) getSupportFragmentManager().findFragmentByTag("first");
        PlayFragment s = (PlayFragment) getSupportFragmentManager().findFragmentByTag("second");

        if (!s.isHidden()){
            getSupportFragmentManager().beginTransaction().show(f).commit();
            getSupportFragmentManager().beginTransaction().hide(s).commit();
            mRenderFragment = f;
        }else{
            getSupportFragmentManager().beginTransaction().show(s).commit();
            getSupportFragmentManager().beginTransaction().hide(f).commit();
            mRenderFragment = s;
        }
    }

    public void onToggleAspectRatio(View view) {
        PlayFragment f =mRenderFragment;
        if (f == null) return;
        f.setScaleType(++i);
        switch (i){
            case PlayFragment.ASPACT_RATIO_INSIDE: {
                Toast.makeText(this,"等比例居中",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayFragment.ASPACT_RATIO_CENTER_CROPE: {
                Toast.makeText(this,"等比例居中裁剪视频",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayFragment.FILL_WINDOW:{
                Toast.makeText(this,"拉伸视频,铺满区域",Toast.LENGTH_SHORT).show();
            }
            break;
            case PlayFragment.ASPACT_RATIO_CROPE_MATRIX:{
                Toast.makeText(this,"等比例显示视频,可拖拽显示隐藏区域.",Toast.LENGTH_SHORT).show();
            }
            break;
        }
        if (i == PlayFragment.FILL_WINDOW){
            i = 0;
        }


    }
}
