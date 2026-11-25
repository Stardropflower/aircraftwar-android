package edu.hitsz.aircraftwar.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * 数据库帮助类（对应课件第 13–15 页）。
 * <p>
 * 两个要点：
 * <ul>
 *   <li>{@link #onCreate} 只在<b>第一次</b>创建数据库时调用一次，用来建表；</li>
 *   <li>用单例模式，避免多个 Helper 实例各自持有连接。</li>
 * </ul>
 * 表结构调整时：把 {@link #DB_VERSION} 加 1，并在 {@link #onUpgrade} 里写迁移逻辑。
 */
public class ScoreDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "ScoreDbHelper";

    public static final String DB_NAME = "aircraftwar.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_SCORE = "score_list";
    public static final String COL_ID = "_id";
    public static final String COL_NAME = "name";
    public static final String COL_SCORE = "score";
    public static final String COL_DIFFICULTY = "difficulty";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_SYNCED = "synced";

    private static volatile ScoreDbHelper instance;

    private ScoreDbHelper(Context context) {
        // 传 ApplicationContext：Helper 的生命周期跟进程一样长，传 Activity 会泄漏
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    public static ScoreDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (ScoreDbHelper.class) {
                if (instance == null) {
                    instance = new ScoreDbHelper(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_SCORE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_SCORE + " INTEGER NOT NULL, "
                + COL_DIFFICULTY + " TEXT NOT NULL, "
                + COL_CREATED_AT + " INTEGER NOT NULL, "
                + COL_SYNCED + " INTEGER NOT NULL DEFAULT 0"
                + ");";
        Log.d(TAG, "onCreate: " + sql);
        db.execSQL(sql);

        // 排行榜要按「难度 + 分数倒序」查，建个索引让排序走索引而不是全表扫描
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_score_difficulty ON "
                + TABLE_SCORE + " (" + COL_DIFFICULTY + ", " + COL_SCORE + " DESC);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade: " + oldVersion + " -> " + newVersion);
        // 课程项目的数据不重要，简单起见直接重建表。
        // 真实项目应写 ALTER TABLE 做无损迁移。
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCORE);
        onCreate(db);
    }
}
