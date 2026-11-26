package edu.hitsz.aircraftwar.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import edu.hitsz.aircraftwar.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 音频管理：短音效走 {@link SoundPool}，背景音乐走 {@link MediaPlayer}。
 * <p>
 * 为什么分开？（对应课件第 18 页）
 * <ul>
 *   <li>音效短、触发密集（一局要触发几百次），要求低延迟、可叠加 —— 这是 SoundPool 的强项；</li>
 *   <li>BGM 长（几 MB）、同一时刻只放一首 —— 这是 MediaPlayer 的强项。</li>
 * </ul>
 * <p>
 * 音频资源按课件要求放在 <b>res/raw</b>，通过 {@code R.raw.xxx} 引用。
 * 注意 {@code .wav} 在 AAPT 的默认「不压缩」名单里，所以
 * {@code Resources.openRawResourceFd()} 也能拿到文件描述符；
 * 这里统一用 {@code SoundPool.load(Context, int, int)} 和 {@code MediaPlayer.create()}，
 * 不需要自己处理 fd。
 * <p>
 * 这个类的职责就是课件第 21 页说的「封装音频管理类」：音频逻辑与 Activity 解耦、
 * 资源统一管理、生命周期集中在一处（{@link #release()}）。
 */
public class SoundManager {

    private static final String TAG = "AircraftWarAudio";

    /** 音效名 → R.raw 资源 id（课件第 23 页点名的四个音效） */
    private static final Map<String, Integer> EFFECT_RES = new HashMap<>();

    public static final String BGM_NORMAL = "bgm_normal";
    public static final String BGM_BOSS = "bgm_boss";

    static {
        // bullet_hit：英雄机子弹击中敌机，或敌机子弹击中英雄机
        EFFECT_RES.put("hit", R.raw.bullet_hit);
        // bomb_explosion：英雄机与炸弹道具碰撞（课件标注为可选，我们实现了）
        EFFECT_RES.put("bomb", R.raw.bomb_explosion);
        // get_supply：英雄机与非炸弹道具碰撞
        EFFECT_RES.put("item", R.raw.get_supply);
        // game_over：游戏结束（课件指定的文件名）
        EFFECT_RES.put("game_over", R.raw.game_over);
    }

    private static final int BGM_RES_NORMAL = R.raw.bgm;
    private static final int BGM_RES_BOSS = R.raw.bgm_boss;

    private final Context appContext;
    private final Map<String, Integer> soundIds = new HashMap<>();
    private final Map<String, Boolean> loaded = new HashMap<>();

    private SoundPool soundPool;
    private MediaPlayer bgmPlayer;
    private String currentBgm;

    private boolean musicMuted = false;
    private boolean soundMuted = false;
    /** 音乐被关掉时收到的「想播放哪首」，等重新打开时补上 */
    private String pendingBgm;
    private float effectVolume = 0.8f;
    private float bgmVolume = 0.45f;

    public SoundManager(Context context) {
        // 用 ApplicationContext：音频管理器活得比 Activity 久，
        // 传 Activity 会导致页面销毁后无法回收（对应课件第 24-25 页 Context 的三种获取方式）
        this.appContext = context.getApplicationContext();
        initSoundPool();
        loadEffects();
    }

    private void initSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status != 0) {
                Log.w(TAG, "音效加载失败 sampleId=" + sampleId + " status=" + status);
            }
        });
    }

    /** 预加载所有音效（课件第 20 页：SoundPool 的核心优势是预加载解码、延迟低） */
    private void loadEffects() {
        for (Map.Entry<String, Integer> entry : EFFECT_RES.entrySet()) {
            String name = entry.getKey();
            int resId = entry.getValue();
            try {
                int id = soundPool.load(appContext, resId, 1);
                soundIds.put(name, id);
                loaded.put(name, true);
            } catch (RuntimeException e) {
                Log.e(TAG, "音效加载失败: " + name + " -> " + e.getMessage());
                loaded.put(name, false);
            }
        }
    }

    /** 播放一次性音效；未加载成功或静音时静默跳过 */
    public void playEffect(String name) {
        if (soundMuted) {
            return;
        }
        Integer id = soundIds.get(name);
        if (id == null || !Boolean.TRUE.equals(loaded.get(name))) {
            return;
        }
        try {
            soundPool.play(id, effectVolume, effectVolume, 1, 0, 1f);
        } catch (RuntimeException e) {
            Log.w(TAG, "音效播放异常: " + name + " -> " + e.getMessage());
        }
    }

    /**
     * 切换背景音乐。同一首正在播时不重启，避免每帧调用导致音乐卡顿。
     * <p>
     * Boss 曲与普通曲的切换由核心层的 onBossSpawned / onBossDefeated 事件驱动
     * （课件第 22 页：Boss 背景音乐出现时要暂停游戏背景音乐）。
     */
    public synchronized void playBgm(String name) {
        if (name == null || name.equals(currentBgm)) {
            return;
        }
        if (musicMuted) {
            // 关着音乐时记下来，用户一开就能接上，而不是要等下一次切曲
            pendingBgm = name;
            return;
        }
        stopBgm();

        int resId = BGM_BOSS.equals(name) ? BGM_RES_BOSS : BGM_RES_NORMAL;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        MediaPlayer player = null;
        try {
            // 四参 create：构造 + setDataSource + prepare 一步完成，直接返回可播放实例
            player = MediaPlayer.create(appContext, resId, attributes, 0);
            if (player == null) {
                Log.e(TAG, "BGM 创建失败: " + name);
                return;
            }
            player.setLooping(true);
            player.setVolume(bgmVolume, bgmVolume);
            player.start();
            bgmPlayer = player;
            currentBgm = name;
        } catch (RuntimeException e) {
            Log.e(TAG, "BGM 播放失败: " + name + " -> " + e.getMessage());
            if (player != null) {
                player.release();
            }
        }
    }

    public synchronized void stopBgm() {
        MediaPlayer player = bgmPlayer;
        bgmPlayer = null;
        currentBgm = null;
        if (player == null) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (IllegalStateException ignored) {
            // 已经处于停止/错误状态，直接释放即可
        }
        player.release();
    }

    /** 暂停 BGM（切后台时用） */
    public synchronized void pauseBgm() {
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.isPlaying()) {
                    bgmPlayer.pause();
                }
            } catch (IllegalStateException ignored) {
                // ignore
            }
        }
    }

    /** 恢复 BGM */
    public synchronized void resumeBgm() {
        if (bgmPlayer != null) {
            try {
                if (!bgmPlayer.isPlaying()) {
                    bgmPlayer.start();
                }
            } catch (IllegalStateException ignored) {
                // ignore
            }
        }
    }

    public boolean isMuted() {
        return musicMuted && soundMuted;
    }

    /** 音乐开关：关掉就暂停 BGM，打开时接着放（或补上被关掉期间想播的那首） */
    public synchronized void setMusicMuted(boolean muted) {
        this.musicMuted = muted;
        if (muted) {
            pendingBgm = currentBgm;
            pauseBgm();
            return;
        }
        String pending = pendingBgm;
        pendingBgm = null;
        if (pending != null && !pending.equals(currentBgm)) {
            currentBgm = null;
            playBgm(pending);
        } else {
            resumeBgm();
        }
    }

    public boolean isMusicMuted() {
        return musicMuted;
    }

    /** 音效开关：只管 SoundPool，不碰 BGM */
    public void setSoundMuted(boolean muted) {
        this.soundMuted = muted;
    }

    public boolean isSoundMuted() {
        return soundMuted;
    }

    /** 两个通道一起切（保留给「一键静音」这类场景） */
    public synchronized void setMuted(boolean muted) {
        setSoundMuted(muted);
        setMusicMuted(muted);
    }

    public void setVolumes(float effect, float bgm) {
        this.effectVolume = clamp(effect);
        this.bgmVolume = clamp(bgm);
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(this.bgmVolume, this.bgmVolume);
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** 释放全部资源。必须在 Activity onDestroy 调用，否则会泄漏音频焦点。 */
    public synchronized void release() {
        stopBgm();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundIds.clear();
        loaded.clear();
    }
}
