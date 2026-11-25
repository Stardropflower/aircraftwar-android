package edu.hitsz.aircraftwar.db;

/**
 * 一条排行榜记录（对应数据库 score_list 表的一行）。
 * <p>
 * 用公开字段 + 无参构造，是 Android 里 bean 的常见写法（课件 SQLiteDemo 的 UserInfo 也是这样），
 * 好处是 Adapter 与 DAO 之间传递简单，不需要写一堆 getter/setter。
 */
public class ScoreRecord {

    /** 主键，插入前为 0，插入后由数据库分配 */
    public long id;
    /** 玩家昵称 */
    public String name;
    /** 本局得分 */
    public int score;
    /** 难度：EASY / NORMAL / HARD */
    public String difficulty;
    /** 创建时间（毫秒时间戳） */
    public long createdAt;
    /**
     * 是否已同步到云端：0 = 待同步，1 = 已同步。
     * <p>
     * 这一列是给「断网时成绩写入本地待同步队列、联网后自动补传」用的
     * （任务书里写的方案）。本期实验四只做本地存储，云端同步由后续实验接上，
     * 但表结构先留好，避免以后改表。
     */
    public int synced;

    public ScoreRecord() {
        name = "";
        difficulty = "EASY";
    }

    public ScoreRecord(String name, int score, String difficulty, long createdAt) {
        this.name = name;
        this.score = score;
        this.difficulty = difficulty;
        this.createdAt = createdAt;
        this.synced = 0;
    }
}
