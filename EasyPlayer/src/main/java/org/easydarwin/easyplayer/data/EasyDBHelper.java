package org.easydarwin.easyplayer.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 视频源的数据库
 */
public class EasyDBHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "easydb.db";

    public EasyDBHelper(Context context) {
        super(context, DB_NAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        VideoSource.createTable(sqLiteDatabase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("drop table if exists video_source");
        VideoSource.createTable(sqLiteDatabase);
    }
}
