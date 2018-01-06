package org.easydarwin.easyplayer;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import org.easydarwin.easyplayer.fragments.PlayFragment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by john on 2017/3/9.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PlayActivityTest {

    private boolean mVideoDisplayed;
    @Rule
    ActivityTestRule<PlayActivity> testRule = new ActivityTestRule<PlayActivity>(PlayActivity.class){
        @Override
        protected Intent getActivityIntent() {
            Intent i = super.getActivityIntent();
            i.setClass(InstrumentationRegistry.getContext(), PlayActivity.class);
            i.putExtra("play_url", "rtsp://a2047.v1412b.c1412.g.vq.akamaistream.NET/5/2047/1412/1_h264_350/1a1a1ae555c531960166df4dbc3");
            i.putExtra("rr",  new ResultReceiver(new Handler()) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    super.onReceiveResult(resultCode, resultData);
                    if (resultCode == PlayFragment.RESULT_REND_STARTED) {
                    } else if (resultCode == PlayFragment.RESULT_REND_STOPED) {
                    } else if (resultCode == PlayFragment.RESULT_REND_VIDEO_DISPLAYED) {
                        mVideoDisplayed = true;
                    }
                }
            });
            return i;
        }
    };
}