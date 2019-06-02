package org.easydarwin.easyplayer.data;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

/**
 * 保存视频源，及相关信息
 */
public class VideoSource implements BaseColumns {
    public static final String URL = "url";                         // 流地址
    public static final String TABLE_NAME = "video_source";

    /**
     * -1 refers to manual added, otherwise pulled from server.
     */
    public static final String INDEX = "_index";
    public static final String NAME = "name";

    public static final String AUDIENCE_NUMBER = "audience_number"; // 当前观看人数

    public static void createTable(SQLiteDatabase db) {
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s (" +
                        "%s integer primary key autoincrement, " +
                        "%s integer default -1, " +
                        "%s VARCHAR(256) NOT NULL DEFAULT '', " +
                        "%s VARCHAR(256) NOT NULL DEFAULT '', " +
                        "%s integer DEFAULT 0 " +
                        ")",
                TABLE_NAME,
                _ID,
                INDEX,
                URL,
                NAME,
                AUDIENCE_NUMBER));
    }
}
