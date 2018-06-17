package org.easydarwin.easyplayer;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.tencent.bugly.crashreport.CrashReport;

import org.easydarwin.easyplayer.data.EasyDBHelper;
/**
 * Created by afd on 8/13/16.
 */
public class TheApp extends Application {

    public static final String DEFAULT_SERVER_IP = "cloud.easydarwin.org";
    public static SQLiteDatabase sDB;
    public static String sPicturePath;
    public static String sMoviePath;

    @Override
    public void onCreate() {
        super.onCreate();
        sPicturePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/EasyPlayer";
        sMoviePath = getExternalFilesDir(Environment.DIRECTORY_MOVIES) + "/EasyPlayer";
        sDB = new EasyDBHelper(this).getWritableDatabase();
        resetServer();
        CrashReport.initCrashReport(getApplicationContext(), "045f78d6f0", BuildConfig.DEBUG);
    }

    public void resetServer(){
        String ip = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.key_ip), DEFAULT_SERVER_IP);
        if ("114.55.107.180".equals(ip)) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(getString(R.string.key_ip), DEFAULT_SERVER_IP).apply();
        }
    }
}
