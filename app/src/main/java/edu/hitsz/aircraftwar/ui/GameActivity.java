package edu.hitsz.aircraftwar.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import edu.hitsz.aircraftwar.BuildConfig;
import edu.hitsz.aircraftwar.audio.SoundManager;
import edu.hitsz.aircraftwar.core.EasyGameWorld;
import edu.hitsz.aircraftwar.core.GameWorld;
import edu.hitsz.aircraftwar.core.HardGameWorld;
import edu.hitsz.aircraftwar.core.NormalGameWorld;
import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.item.BloodItem;
import edu.hitsz.aircraftwar.core.item.BombItem;
import edu.hitsz.aircraftwar.core.item.FireItem;
import edu.hitsz.aircraftwar.core.item.FreezeItem;
import edu.hitsz.aircraftwar.core.item.Item;
import edu.hitsz.aircraftwar.core.sprite.SpriteMetricsHolder;
import edu.hitsz.aircraftwar.core.world.PlayField;
import edu.hitsz.aircraftwar.data.LocalStore;
import edu.hitsz.aircraftwar.render.Assets;
import edu.hitsz.aircraftwar.render.GameSurfaceView;

/**
 * 游戏页：承载 {@link GameSurfaceView}，负责资源装载、音频、存档与生命周期。
 * <p>
 * 战场尺寸按屏幕长宽比动态计算：固定逻辑宽度 512，高度随屏幕比例伸缩。
 * Windows 版写死 512x768，直接搬到 20:9 手机上会上下留黑边；
 * 按比例伸缩可以铺满屏幕，代价是纵向更"深"、难度略降，后续可通过刷怪节奏调回。
 */
public class GameActivity extends AppCompatActivity {

    public static final String EXTRA_DIFFICULTY = "difficulty";
    /** 调试用：开局直接在英雄机位置放两个道具，便于验证背包。仅 DEBUG 包生效。 */
    public static final String EXTRA_DEBUG_GRANT = "debug_grant";

    /**
     * 顶部 UI 占用高度（逻辑单位）：得分卡 12~88、难度胶囊 14~38、背包格 12~78。
     * 英雄机不允许进这一条带，否则会挡住 UI，手指也会和「点背包格」冲突。
     */
    private static final int PLAY_TOP_INSET = 135;

    private GameSurfaceView gameView;
    private GameWorld world;
    private SoundManager sound;
    private LocalStore store;
    private Difficulty difficulty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterFullscreen();

        difficulty = readDifficulty();

        // 1) 资源与精灵尺寸必须先装好，核心层创建实体时要向它问宽高
        Assets assets = Assets.load(this);
        SpriteMetricsHolder.install(assets);

        // 2) 按屏幕比例确定战场尺寸，并留出顶部 UI 条带
        configurePlayField();
        PlayField.setTopInset(PLAY_TOP_INSET);

        // 3) 音频
        store = new LocalStore(this);
        sound = new SoundManager(this);
        sound.setMuted(store.isMuted());

        // 4) 创建游戏世界，套上玩家选中的皮肤
        world = createWorld(difficulty);
        HeroAircraft.Skin skin = store.getSelectedSkin();
        for (HeroAircraft hero : world.getHeroes()) {
            hero.setSkin(skin);
        }
        world.setListener(createListener());

        // 调试注入：在渲染线程启动之前往战场放道具，第一帧就会被英雄机拾取
        if (BuildConfig.DEBUG && getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_DEBUG_GRANT, false)) {
            HeroAircraft debugHero = world.getHero(0);
            world.getItems().add(BloodItem.create(debugHero.getLocationX(), debugHero.getLocationY()));
            world.getItems().add(FireItem.create(debugHero.getLocationX(), debugHero.getLocationY()));
        }

        world.start();
        sound.playBgm(SoundManager.BGM_NORMAL);

        // 5) 挂载渲染视图
        gameView = new GameSurfaceView(this, world, assets);
        gameView.setCallback(this::onGameOver);
        setContentView(gameView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * 把核心层的事件翻译成音效。
     * <p>
     * 这一层是「逻辑」和「表现」的接缝：核心层只管发出事件，
     * 播什么声音、要不要震动，完全由这里决定，核心层不需要知道 SoundPool 的存在。
     */
    private GameWorld.Listener createListener() {
        return new GameWorld.Listener() {
            @Override
            public void onEnemyKilled(AbstractEnemy enemy, HeroAircraft killer) {
                sound.playEffect("hit");
            }

            @Override
            public void onItemPicked(Item item, HeroAircraft hero) {
                sound.playEffect("item");
            }

            @Override
            public void onBombDetonated() {
                sound.playEffect("bomb");
            }

            @Override
            public void onFreezeActivated() {
                sound.playEffect("item");
            }

            @Override
            public void onBackpackItemUsed(Item item, HeroAircraft hero) {
                // 炸弹 / 冰冻在生效时已经通过上面的事件播过音，这里不重复
                if (!(item instanceof BombItem) && !(item instanceof FreezeItem)) {
                    sound.playEffect("item");
                }
            }

            @Override
            public void onBossSpawned(BossEnemy boss) {
                sound.playBgm(SoundManager.BGM_BOSS);
            }

            @Override
            public void onBossDefeated() {
                sound.playBgm(SoundManager.BGM_NORMAL);
            }

            @Override
            public void onGameOver() {
                sound.stopBgm();
                sound.playEffect("game_over");
            }
        };
    }

    private Difficulty readDifficulty() {
        String extra = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_DIFFICULTY);
        if (extra == null) {
            return Difficulty.EASY;
        }
        try {
            return Difficulty.valueOf(extra);
        } catch (IllegalArgumentException e) {
            return Difficulty.EASY;
        }
    }

    private GameWorld createWorld(Difficulty difficulty) {
        switch (difficulty) {
            case HARD:
                return new HardGameWorld();
            case NORMAL:
                return new NormalGameWorld();
            default:
                return new EasyGameWorld();
        }
    }

    /**
     * 逻辑宽度固定 512（沿用 Windows 版），高度按屏幕长宽比换算。
     * 用真实分辨率而不是「可用区域」，因为我们是全屏沉浸式绘制。
     */
    private void configurePlayField() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int logicalWidth = 512;
        int logicalHeight = Math.round(logicalWidth * (dm.heightPixels / (float) dm.widthPixels));
        // 兜底：极端比例下也不能算出离谱的高度
        logicalHeight = Math.max(768, Math.min(logicalHeight, 1600));
        PlayField.configure(logicalWidth, logicalHeight);
    }

    private void enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void onGameOver(int score) {
        // 回调来自渲染线程，必须切回 UI 线程再做界面和存档
        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            boolean newRecord = store.submitScore(difficulty, score);
            String message = "本局得分：" + score
                    + (newRecord ? "\n\n刷新了本机最高分！" : "");
            new AlertDialog.Builder(this)
                    .setTitle("游戏结束")
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("返回", (dialog, which) -> finish())
                    .show();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.setPaused(true);
        }
        if (sound != null) {
            sound.pauseBgm();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.setPaused(false);
        }
        if (sound != null && !world.isGameOver()) {
            sound.resumeBgm();
        }
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) {
            gameView.shutdown();
        }
        if (world != null) {
            world.stop();
        }
        if (sound != null) {
            sound.release();
        }
        super.onDestroy();
    }
}
