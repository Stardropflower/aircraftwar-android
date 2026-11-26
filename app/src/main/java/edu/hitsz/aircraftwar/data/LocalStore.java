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
    private static final String KEY_MUSIC_MUTED = "music_muted";
    private static final String KEY_SOUND_MUTED = "sound_muted";
    private static final String KEY_PLAYER_NAME = "player_name";

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

    // ---------------- 玩家昵称 ----------------

    /** 默认昵称；排行榜的「姓名」列用它 */
    public static final String DEFAULT_PLAYER_NAME = "玩家";

    public String getPlayerName() {
        String name = prefs.getString(KEY_PLAYER_NAME, null);
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_PLAYER_NAME;
        }
        return name;
    }

    public void setPlayerName(String name) {
        String trimmed = name == null ? "" : name.trim();
        // 昵称过长会让排行榜行布局挤爆，限制 8 个字
        if (trimmed.length() > 8) {
            trimmed = trimmed.substring(0, 8);
        }
        prefs.edit().putString(KEY_PLAYER_NAME, trimmed).apply();
    }

    // ---------------- 音频开关 ----------------
    // 音乐和音效分开存：BGM 是一整首循环，音效是几百次短促触发，
    // 想关哪个是独立需求（比如只想关 BGM 听音效）。

    public boolean isMusicMuted() {
        migrateLegacyMute();
        return prefs.getBoolean(KEY_MUSIC_MUTED, false);
    }

    public void setMusicMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MUSIC_MUTED, muted).apply();
    }

    public boolean isSoundMuted() {
        migrateLegacyMute();
        return prefs.getBoolean(KEY_SOUND_MUTED, false);
    }

    public void setSoundMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_SOUND_MUTED, muted).apply();
    }

    /**
     * 旧版本只有一个总开关 {@code muted}，拆成两个之后要把它迁移过来，
     * 否则老用户升级完会突然开始有声音。只在两个新键都不存在时做一次。
     */
    private void migrateLegacyMute() {
        if (prefs.contains(KEY_MUSIC_MUTED) || prefs.contains(KEY_SOUND_MUTED)) {
            return;
        }
        if (!prefs.contains(KEY_MUTED)) {
            return;
        }
        boolean legacy = prefs.getBoolean(KEY_MUTED, false);
        prefs.edit()
                .putBoolean(KEY_MUSIC_MUTED, legacy)
                .putBoolean(KEY_SOUND_MUTED, legacy)
                .remove(KEY_MUTED)
                .apply();
    }

    /** 两个通道都关了才算整体静音 */
    public boolean isMuted() {
        return isMusicMuted() && isSoundMuted();
    }

    public void setMuted(boolean muted) {
        setMusicMuted(muted);
        setSoundMuted(muted);
    }

    /** 清空全部存档（调试用） */
    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
