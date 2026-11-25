package edu.hitsz.aircraftwar.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜数据访问对象（对应课件第 16 页的 CRUD 表）。
 * <p>
 * 课件强调的三点都在这里体现：
 * <ol>
 *   <li>增：用 {@link ContentValues} 封装一行，{@code insert} 返回新行 id，失败返回 -1；</li>
 *   <li>查：{@code query} 的 orderBy 直接写 <b>"score DESC"</b>，一条查询就是排行榜顺序；</li>
 *   <li>删：whereClause 用 {@code ?} 占位，实参放 whereArgs，避免 SQL 拼接注入；</li>
 *   <li>Cursor 遍历完<b>必须 close()</b>，否则会泄漏，累积多了报 CursorWindow 错误。</li>
 * </ol>
 * <p>
 * 注意：数据库操作是同步 IO。这里的数据量很小（一局一条、几十条），
 * 直接在调用线程执行没问题；如果以后要存大量数据，应该挪到子线程。
 */
public class ScoreDao {

    private static final String TAG = "ScoreDao";
    /** 排行榜只展示前 100 名（与任务书里「云端按难度分别展示前 100 名」的口径一致） */
    public static final int TOP_LIMIT = 100;

    private final ScoreDbHelper helper;
    private SQLiteDatabase db;

    public ScoreDao(Context context) {
        this.helper = ScoreDbHelper.getInstance(context);
    }

    private SQLiteDatabase writable() {
        if (db == null || !db.isOpen()) {
            db = helper.getWritableDatabase();
        }
        return db;
    }

    private SQLiteDatabase readable() {
        if (db == null || !db.isOpen()) {
            db = helper.getReadableDatabase();
        }
        return db;
    }

    public void close() {
        if (db != null && db.isOpen()) {
            db.close();
        }
        db = null;
    }

    /**
     * 插入一条成绩。
     *
     * @return 新行的 id，失败返回 -1
     */
    public long insert(ScoreRecord record) {
        ContentValues values = new ContentValues();
        values.put(ScoreDbHelper.COL_NAME, record.name == null ? "" : record.name);
        values.put(ScoreDbHelper.COL_SCORE, record.score);
        values.put(ScoreDbHelper.COL_DIFFICULTY, record.difficulty);
        values.put(ScoreDbHelper.COL_CREATED_AT, record.createdAt);
        values.put(ScoreDbHelper.COL_SYNCED, record.synced);
        long id = writable().insert(ScoreDbHelper.TABLE_SCORE, null, values);
        if (id == -1) {
            Log.e(TAG, "插入失败: " + record.name + " / " + record.score);
        }
        return id;
    }

    /**
     * 查某个难度的排行榜，按分数从高到低。
     *
     * @param difficulty 难度名，传 null 表示不区分难度
     */
    public List<ScoreRecord> queryByDifficulty(String difficulty) {
        String selection = null;
        String[] args = null;
        if (difficulty != null) {
            selection = ScoreDbHelper.COL_DIFFICULTY + " = ?";
            args = new String[]{difficulty};
        }

        // orderBy 直接写 "score DESC" 就是排行榜顺序（课件第 16 页原话）
        Cursor cursor = readable().query(
                ScoreDbHelper.TABLE_SCORE,
                null,
                selection,
                args,
                null,
                null,
                ScoreDbHelper.COL_SCORE + " DESC, " + ScoreDbHelper.COL_CREATED_AT + " ASC",
                String.valueOf(TOP_LIMIT));

        List<ScoreRecord> result = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                result.add(readRow(cursor));
            }
        } finally {
            // 课件明确要求：遍历取值后必须 close()
            cursor.close();
        }
        return result;
    }

    private ScoreRecord readRow(Cursor cursor) {
        ScoreRecord record = new ScoreRecord();
        record.id = cursor.getLong(cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_ID));
        record.name = cursor.getString(cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_NAME));
        record.score = cursor.getInt(cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_SCORE));
        record.difficulty = cursor.getString(
                cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_DIFFICULTY));
        record.createdAt = cursor.getLong(
                cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_CREATED_AT));
        record.synced = cursor.getInt(cursor.getColumnIndexOrThrow(ScoreDbHelper.COL_SYNCED));
        return record;
    }

    /**
     * 按 id 删除一条记录。
     *
     * @return 实际删除的行数
     */
    public int deleteById(long id) {
        int count = writable().delete(
                ScoreDbHelper.TABLE_SCORE,
                ScoreDbHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)});
        Log.d(TAG, "deleteById id=" + id + " rows=" + count);
        return count;
    }

    /** 清空某个难度的榜单 */
    public int clearByDifficulty(String difficulty) {
        return writable().delete(
                ScoreDbHelper.TABLE_SCORE,
                ScoreDbHelper.COL_DIFFICULTY + " = ?",
                new String[]{difficulty});
    }

    /** 待同步到云端的记录（本期只写入不消费，留给后续实验） */
    public List<ScoreRecord> queryUnsynced() {
        Cursor cursor = readable().query(
                ScoreDbHelper.TABLE_SCORE,
                null,
                ScoreDbHelper.COL_SYNCED + " = ?",
                new String[]{"0"},
                null, null,
                ScoreDbHelper.COL_CREATED_AT + " ASC");
        List<ScoreRecord> result = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                result.add(readRow(cursor));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /** 标记某条记录已同步到云端 */
    public int markSynced(long id) {
        ContentValues values = new ContentValues();
        values.put(ScoreDbHelper.COL_SYNCED, 1);
        return writable().update(
                ScoreDbHelper.TABLE_SCORE,
                values,
                ScoreDbHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }
}
