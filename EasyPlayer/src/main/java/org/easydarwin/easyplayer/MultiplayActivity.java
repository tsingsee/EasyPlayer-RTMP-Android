package org.easydarwin.easyplayer;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayout;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RadioGroup;

import org.easydarwin.easyplayer.fragments.PlayFragment;

public class MultiplayActivity extends AppCompatActivity implements PlayFragment.OnDoubleTapListener {

    public static final String EXTRA_URL = "extra-url";
    public static final int REQUEST_SELECT_ITEM_TO_PLAY = 2001;
    ResultReceiver rr = new ResultReceiver(new Handler());
    private int mNextPlayHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplay);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (!TextUtils.isEmpty(url)) {
            addVideoToHolder(url, R.id.play_fragment_holder1);
        }
        RadioGroup rg = findViewById(R.id.switch_display_wnd_radio_group);

        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                GridLayout grid = findViewById(R.id.fragment_container_grid);
                if (checkedId == R.id.display_4_wnd) {
                    while (grid.getChildCount() > 4) {
                        grid.removeViewAt(grid.getChildCount() - 1);
                    }
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View v = grid.getChildAt(i);
                        GridLayout.LayoutParams p = (GridLayout.LayoutParams) v.getLayoutParams();
                        p.columnSpec = GridLayout.spec(i % 2, 1.0f);
                        p.rowSpec = GridLayout.spec(i / 2, 1.0f);
                        v.setId(i + 1);
                    }
                    grid.setColumnCount(2);
                    grid.setRowCount(2);
                } else if (checkedId == R.id.display_9_wnd) {
                    grid.setColumnCount(3);
                    grid.setRowCount(3);
                    while (grid.getChildCount() < 9) {
                        View view = LayoutInflater.from(MultiplayActivity.this).inflate(R.layout.grid_item, grid, false);
                        grid.addView(view);
                    }
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View v = grid.getChildAt(i);
                        GridLayout.LayoutParams p = (GridLayout.LayoutParams) v.getLayoutParams();
                        p.columnSpec = GridLayout.spec(i % 3, 1.0f);
                        p.rowSpec = GridLayout.spec(i / 3, 1.0f);
                        v.setId(i + 1);
                    }
                }
            }
        });

        GridLayout grid = findViewById(R.id.fragment_container_grid);
        grid.removeAllViews();
        if (rg.getCheckedRadioButtonId() == R.id.display_4_wnd) {
            // add 4 windows
            grid.setColumnCount(2);
            grid.setRowCount(2);
            for (int i = 0; i < 4; i++) {
                View view = LayoutInflater.from(this).inflate(R.layout.grid_item, grid, false);
                GridLayout.LayoutParams p = (GridLayout.LayoutParams) view.getLayoutParams();
                grid.addView(view);
                view.setId(i + 1);
            }
        } else {
            // add 9 windows
            grid.setColumnCount(3);
            grid.setRowCount(3);

            for (int i = 0; i < 9; i++) {
                View view = LayoutInflater.from(this).inflate(R.layout.grid_item, grid, false);
                grid.addView(view);
                view.setId(i + 1);
            }
        }
        setViewLayoutByConfiguration(getResources().getConfiguration());
    }

    public void onAddVideoSource(View view) {
        Intent intent = new Intent(this, PlaylistActivity.class);
        intent.putExtra(PlaylistActivity.EXTRA_BOOLEAN_SELECT_ITEM_TO_PLAY, true);
        startActivityForResult(intent, REQUEST_SELECT_ITEM_TO_PLAY);
        ViewGroup p = (ViewGroup) view.getParent();
        mNextPlayHolder = p.getId();
    }

    private void addVideoToHolder(String url, int holder) {
        PlayFragment f = PlayFragment.newInstance(this, url, rr);
        /**
         * 铺满全屏
         */
        f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
        f.setOnDoubleTapListener(this);
        FragmentManager manager = getSupportFragmentManager();
        manager.beginTransaction().add(holder, f).commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_ITEM_TO_PLAY) {
            if (resultCode == RESULT_OK) {
                String url = data.getStringExtra("url");
                addVideoToHolder(url, mNextPlayHolder);
            }
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setViewLayoutByConfiguration(newConfig);
    }


    void setViewLayoutByConfiguration(Configuration newConfig) {

        View container = findViewById(R.id.fragment_container_grid);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.dimensionRatio = null;
            findViewById(R.id.toolbar).setVisibility(View.GONE);


//            PlayFragment f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder1);
//            if (f != null) f.setScaleType(PlayFragment.FILL_WINDOW);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder2);
//            if (f != null) f.setScaleType(PlayFragment.FILL_WINDOW);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder3);
//            if (f != null) f.setScaleType(PlayFragment.FILL_WINDOW);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder4);
//            if (f != null) f.setScaleType(PlayFragment.FILL_WINDOW);
        } else {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.dimensionRatio = "1:1";
            findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
//            PlayFragment f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder1);
//            if (f != null) f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder2);
//            if (f != null) f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder3);
//            if (f != null) f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
//            f = (PlayFragment) getSupportFragmentManager().findFragmentById(R.id.play_fragment_holder4);
//            if (f != null) f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
        }
        container.requestLayout();
    }

    @Override
    public void onDoubleTab(PlayFragment f) {
        GridLayout grid = findViewById(R.id.fragment_container_grid);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View view = grid.getChildAt(i);
            if (view.getId() == f.getId()) {
                view.setVisibility(View.VISIBLE);
                continue;
            } else {
                if (view.getVisibility() == View.VISIBLE) {
                    view.setVisibility(View.GONE);
                    f.setScaleType(PlayFragment.FILL_WINDOW);
                } else {
                    view.setVisibility(View.VISIBLE);
                    f.setScaleType(PlayFragment.ASPACT_RATIO_CENTER_CROPE);
                }
            }
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
}
