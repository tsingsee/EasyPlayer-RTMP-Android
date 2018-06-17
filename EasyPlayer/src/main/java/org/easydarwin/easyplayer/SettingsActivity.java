package org.easydarwin.easyplayer;


import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import org.easydarwin.easyplayer.databinding.ActivitySettingBinding;


/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingBinding mBinding;

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_setting);
//        "114.55.107.180"
        mBinding.serverIp.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_ip), TheApp.DEFAULT_SERVER_IP));
        mBinding.serverPort.setText(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_port), "10008"));
        mBinding.transportMode.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.key_udp_mode), false));
        mBinding.autoRecord.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("auto_record", false));
        mBinding.swCodec.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("use-sw-codec", false));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    public void onOK(View view) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(getString(R.string.key_ip), mBinding.serverIp.getText().toString());
        editor.putString(getString(R.string.key_port), mBinding.serverPort.getText().toString());
        editor.putBoolean(getString(R.string.key_udp_mode), mBinding.transportMode.isChecked());
        editor.putBoolean("auto_record", mBinding.autoRecord.isChecked());
        editor.putBoolean("use-sw-codec",mBinding.swCodec.isChecked());
        editor.apply();
        finish();
    }

    public void onWhatIpMean(View view) {
        if (mBinding.whatIpMean.getVisibility() != View.VISIBLE){
            mBinding.whatIpMean.setVisibility(View.VISIBLE);
        }else
        {
            mBinding.whatIpMean.setVisibility(View.GONE);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP){
            if (mBinding.whatIpMean.getVisibility() == View.VISIBLE){
                mBinding.whatIpMean.setVisibility(View.GONE);
            }
        }
        return super.onTouchEvent(event);
    }
}
