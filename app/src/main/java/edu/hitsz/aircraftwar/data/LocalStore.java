package edu.hitsz.aircraftwar.data;

import android.content.Context;
import android.content.SharedPreferences;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 本地存档：各难度的历史最高分 + 当前选中的皮肤。
 * <p>
 * 用 {@link SharedPreferences} 而不是 Windows 版那种 Java 对象序列化的 .dat 文件：
 * 序列化对版本变化很脆弱（改一个字段就可能读不出来），而这里的数据结构简单，
 * 键值存储足够，还天然支持跨进程读取。
 * <p>
 * 最高分同时承担两个职责：
 * <ol>
 *   <li>排行榜的「只上传新纪录」判定（与 Windows 版口径一致）；</li>
 *   <li>皮肤解锁判定 —— 依据的是**本机**历史最高分，与云端榜单无关，
 *       否则榜上出现高手就会让所有人瞬间全解锁。</li>
 * </ol>
 */
public final class LocalStore {

    private static final String PREF_NAME = "aircraftwar";
    private static final String KEY_BEST_PREFIX = "best_";
    private static final String KEY_SKIN = "selected_skin";
    private static final String KEY_MUTED = "muted";

    private final SharedPreferences prefs;

    public LocalStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------- 最高分 ----------------

    public int getBestScore(Difficulty difficulty) {
        return prefs.getInt(KEY_BEST_PREFIX + difficulty.name(), 0);
    }

    /**
     * 提交成绩，只有超过本机最高分才会保存。
     *
     * @return 是否刷新了纪录
     */
    public boolean submitScore(Difficulty difficulty, int score) {
        if (score <= getBestScore(difficulty)) {
            return false;
        }
        prefs.edit().putInt(KEY_BEST_PREFIX + difficulty.name(), score).apply();
        return true;
    }

    /** 三种难度的最高分之和，用于菜单上展示「总战绩」 */
    public int getTotalBest() {
        int sum = 0;
        for (Difficulty d : Difficulty.values()) {
            sum += getBestScore(d);
        }
        return sum;
    }

    // ---------------- 皮肤 ----------------

    /** 皮肤是否解锁：默认皮肤恒解锁，其余看本机对应难度的历史最高分 */
    public boolean isSkinUnlocked(HeroAircraft.Skin skin) {
        if (skin.isDefaultUnlocked()) {
            return true;
        }
        Difficulty difficulty = skin.getUnlockDifficulty();
        return difficulty != null && getBestScore(difficulty) >= skin.getUnlockScore();
    }

    public HeroAircraft.Skin getSelectedSkin() {
        String name = prefs.getString(KEY_SKIN, HeroAircraft.Skin.HERO.name());
        try {
            return HeroAircraft.Skin.valueOf(name);
        } catch (IllegalArgumentException e) {
            return HeroAircraft.Skin.HERO;
        }
    }

    public void setSelectedSkin(HeroAircraft.Skin skin) {
        prefs.edit().putString(KEY_SKIN, skin.name()).apply();
    }

    // ---------------- 静音开关 ----------------

    public boolean isMuted() {
        return prefs.getBoolean(KEY_MUTED, false);
    }

    public void setMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply();
    }

    /** 清空全部存档（调试用） */
    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
