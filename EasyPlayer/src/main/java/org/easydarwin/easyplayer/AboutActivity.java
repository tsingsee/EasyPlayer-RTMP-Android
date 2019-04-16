package org.easydarwin.easyplayer;

import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import org.easydarwin.easyplayer.databinding.*;
import org.easydarwin.video.Client;

public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         binding = DataBindingUtil.setContentView(this, R.layout.activity_about);

        setSupportActionBar(binding.toolbar);

        int activeDays  = TheApp.activeDays;
        SpannableString
                activeDayString;
        if (activeDays >= 9999) {
            activeDayString = new SpannableString("激活码永久有效");
            activeDayString.setSpan(new ForegroundColorSpan(Color.GREEN), 0, activeDayString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }else if (activeDays > 0){
            activeDayString = new SpannableString(String.format("激活码还剩%d天可用",activeDays));
            activeDayString.setSpan(new ForegroundColorSpan(Color.YELLOW), 0, activeDayString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }else{
            activeDayString = new SpannableString(String.format("激活码已过期(%d)",activeDays));
            activeDayString.setSpan(new ForegroundColorSpan(Color.RED), 0, activeDayString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        binding.title.setText("EasyPlayer RTMP播放器("+activeDayString+")：");
        binding.desc.setText("EasyPlayer RTMP是由EasyDarwin开源团队开发 者开发和维护的一个RTMP播放器项目，目前 支持Windows/Android/iOS，视频支持 H.264/H.265/MPEG4/MJPEG，音频支持 G711A/G711U/G726/AAC，支持硬解码，是一套极佳的 RTMP播放组件！项目地址：");

        binding.desc.setMovementMethod(LinkMovementMethod.getInstance());
        SpannableString spannableString = new SpannableString("https://github.com/EasyDarwin/EasyPlayer");
        //设置下划线文字
        spannableString.setSpan(new URLSpan("https://github.com/EasyDarwin/EasyPlayer"), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        //设置文字的前景色
        spannableString.setSpan(new ForegroundColorSpan(Color.RED), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        binding.desc.append(spannableString);

        binding.desc.append("\n您也可以升级到我们的EasyPlayer Pro全功能版 本，支持HTTP/RTSP/RTMP/HLS等多种流媒 体协议！");
        spannableString = new SpannableString("戳我");
        //设置下划线文字
        spannableString.setSpan(new URLSpan("https://fir.im/EasyPlayerPro"), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        //设置文字的前景色
        spannableString.setSpan(new ForegroundColorSpan(Color.RED), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        binding.desc.append(spannableString);
        binding.desc.append("或者扫描下载:");
        binding.imageView.setImageResource(R.drawable.qrcode_pro);
    }
}
