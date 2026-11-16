package edu.hitsz.aircraftwar.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 音频管理：短音效走 {@link SoundPool}，背景音乐走 {@link MediaPlayer}。
 * <p>
 * 为什么分开？
 * <ul>
 *   <li>音效短、触发密集（一局要触发几百次），要求低延迟、可叠加 —— 这是 SoundPool 的强项；</li>
 *   <li>BGM 长（几 MB）、同一时刻只放一首、需要 segue —— 这是 MediaPlayer 的强项。</li>
 * </ul>
 * 顺带说明：.wav 在 AAPT 的默认「不压缩」名单里，所以可以直接用
 * {@code AssetManager.openFd()} 拿到文件描述符；如果哪天有人把音频换成别的格式，
 * 这里会加载失败并打日志。
 */
public class SoundManager {

    private static final String TAG = "AircraftWarAudio";

    /** 音效名 → 资源路径 */
    private static final Map<String, String> EFFECT_FILES = new HashMap<>();
    /** BGM 名 → 资源路径 */
    public static final String BGM_NORMAL = "bgm_normal";
    public static final String BGM_BOSS = "bgm_boss";

    static {
        EFFECT_FILES.put("hit", "videos/bullet_hit.wav");
        EFFECT_FILES.put("item", "videos/get_supply.wav");
        EFFECT_FILES.put("bomb", "videos/bomb_explosion.wav");
        // 用较短的那版结束音，避免 SoundPool 解码出过大的 PCM
        EFFECT_FILES.put("game_over", "videos/game_over_ori.wav");
    }

    private static final String BGM_FILE_NORMAL = "videos/bgm.wav";
    private static final String BGM_FILE_BOSS = "videos/bgm_boss.wav";

    private final AssetManager assetManager;
    private final Map<String, Integer> soundIds = new HashMap<>();
    private final Map<String, Boolean> loaded = new HashMap<>();

    private SoundPool soundPool;
    private MediaPlayer bgmPlayer;
    private String currentBgm;

    private boolean muted = false;
    private float effectVolume = 0.8f;
    private float bgmVolume = 0.45f;

    public SoundManager(Context context) {
        this.assetManager = context.getApplicationContext().getAssets();
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

    private void loadEffects() {
        for (Map.Entry<String, String> entry : EFFECT_FILES.entrySet()) {
            String name = entry.getKey();
            String path = entry.getValue();
            try (AssetFileDescriptor fd = assetManager.openFd(path)) {
                int id = soundPool.load(fd, 1);
                soundIds.put(name, id);
                loaded.put(name, true);
            } catch (IOException e) {
                // 常见原因：资源被压缩（openFd 只支持未压缩的 asset），或文件名写错
                Log.e(TAG, "音效加载失败: " + path + " -> " + e.getMessage());
                loaded.put(name, false);
            }
        }
    }

    /** 播放一次性音效；未加载成功或静音时静默跳过 */
    public void playEffect(String name) {
        if (muted) {
            return;
        }
        Integer id = soundIds.get(name);
        if (id == null || !Boolean.TRUE.equals(loaded.get(name))) {
            return;
        }
        soundPool.play(id, effectVolume, effectVolume, 1, 0, 1f);
    }

    /**
     * 切换背景音乐。同一首正在播时不重启，避免每帧调用导致音乐卡顿。
     */
    public synchronized void playBgm(String name) {
        if (name == null || name.equals(currentBgm)) {
            return;
        }
        stopBgm();
        String path = BGM_BOSS.equals(name) ? BGM_FILE_BOSS : BGM_FILE_NORMAL;
        try (AssetFileDescriptor fd = assetManager.openFd(path)) {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            player.setLooping(true);
            player.setVolume(bgmVolume, bgmVolume);
            player.prepare();
            player.start();
            bgmPlayer = player;
            currentBgm = name;
        } catch (IOException e) {
            Log.e(TAG, "BGM 加载失败: " + path + " -> " + e.getMessage());
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

    /** 暂停 BGM（切后台时用；游戏动作音效也一并静音） */
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
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            pauseBgm();
        } else {
            resumeBgm();
        }
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
