package edu.hitsz.aircraftwar.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
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
import edu.hitsz.aircraftwar.db.ScoreDao;
import edu.hitsz.aircraftwar.db.ScoreRecord;
import edu.hitsz.aircraftwar.render.Assets;
import edu.hitsz.aircraftwar.render.GameSurfaceView;

import java.lang.ref.WeakReference;

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

    /** 主线程 Handler 的消息类型（实验三第 31 页：用 msg.what 区分消息） */
    private static final int MSG_GAME_OVER = 1;

    /** 主线程 Handler：渲染线程结束游戏时用它把消息发回主线程 */
    private final GameHandler handler = new GameHandler(this);

    private GameSurfaceView gameView;
    private GameWorld world;
    private SoundManager sound;
    private LocalStore store;
    private Difficulty difficulty;
    /** 排行榜数据访问（SQLite） */
    private ScoreDao scoreDao;

    /** 本局成绩是否已经结算过：防止「游戏结束」和「主动返回」重复写入 */
    private boolean scoreSaved = false;
    /** 本局是否刷新了本机最高分（用于结算提示） */
    private boolean newRecord = false;

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

        // 3) 音频与本地存档
        store = new LocalStore(this);
        scoreDao = new ScoreDao(this);
        sound = new SoundManager(this);
        // 音乐与音效是两个独立开关，分别套用
        sound.setMusicMuted(store.isMusicMuted());
        sound.setSoundMuted(store.isSoundMuted());

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
        gameView.setCallback(new GameSurfaceView.Callback() {
            @Override
            public void onGameOver(int score) {
                GameActivity.this.onGameOver(score);
            }

            @Override
            public void onExitRequested() {
                // 点返回键 / 暂停菜单里选返回：立刻结算本局再回主菜单
                GameActivity.this.settleAndFinish();
            }
        });
        setContentView(gameView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 系统返回键也走「结算后返回」，避免直接把一局成绩丢掉
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                settleAndFinish();
            }
        });
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

    /**
     * 游戏结束：把分数装进 Message 发到主线程。
     * <p>
     * 这个回调来自渲染线程（{@code GameSurfaceView} 的 {@code run()}），
     * 不能直接在这里弹对话框 —— 子线程操作 UI 会抛 {@code CalledFromWrongThreadException}。
     * 所以按实验三第 28–32 页讲的 Handler 机制，把消息发到主线程的 Handler，
     * 由 {@link GameHandler#handleMessage} 在主线程执行界面更新。
     */
    private void onGameOver(int score) {
        Message message = Message.obtain();
        message.what = MSG_GAME_OVER;
        message.arg1 = score;          // 用 arg1 携带分数，省去额外对象
        handler.sendMessage(message);
    }

    /** 在主线程执行：写存档 + 弹结算框 */
    private void showGameOverDialog(int score) {
        saveScore(score);
        String message = "本局得分：" + score
                + (newRecord ? "\n\n刷新了本机最高分！" : "");
        new AlertDialog.Builder(this)
                .setTitle("游戏结束")
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton("返回主菜单", (dialog, which) -> finish())
                .setPositiveButton("查看排行榜", (dialog, which) -> openRankAndFinish())
                .show();
    }

    /**
     * 结算本局并返回主菜单（返回键 / 暂停菜单里选「返回主菜单」时调用）。
     * <p>
     * 「立刻结算」意味着不等飞机死掉，当前分数直接入库，
     * 用 {@link #scoreSaved} 保证不会重复写入。
     */
    private void settleAndFinish() {
        if (!isFinishing()) {
            saveScore(world == null ? 0 : world.totalScore());
        }
        finish();
    }

    /**
     * 把本局成绩写入两处：
     * <ol>
     *   <li>{@link LocalStore}：本机最高分（用于皮肤解锁与「是否刷新纪录」提示）；</li>
     *   <li>SQLite 排行榜：{@link ScoreDao#insert} 写一条记录，供排行榜页面展示。</li>
     * </ol>
     * 重复调用只会生效一次。
     */
    private void saveScore(int score) {
        if (scoreSaved) {
            return;
        }
        scoreSaved = true;
        newRecord = store.submitScore(difficulty, score);

        ScoreRecord record = new ScoreRecord(
                store.getPlayerName(), score, difficulty.name(), System.currentTimeMillis());
        long id = scoreDao.insert(record);
        if (id == -1) {
            Log.e("GameActivity", "排行榜写入失败: " + score);
        }
    }

    /** 打开排行榜页（带上本局难度），同时结束本页，避免返回时又回到结算框 */
    private void openRankAndFinish() {
        Intent intent = new Intent(this, RankActivity.class);
        intent.putExtra(RankActivity.EXTRA_DIFFICULTY, difficulty.name());
        startActivity(intent);
        finish();
    }

    /**
     * 主线程 Handler（实验三第 28–32 页）。
     * <p>
     * 两个细节：
     * <ol>
     *   <li>{@code super(Looper.getMainLooper())} —— 绑定到主线程的消息队列，
     *       这样 handleMessage 一定在主线程执行；</li>
     *   <li>写成静态内部类 + {@link WeakReference} —— 非静态内部类会隐式持有外部 Activity，
     *       一旦消息队列里还有未处理的消息，Activity 就无法被回收（内存泄漏）。</li>
     * </ol>
     */
    private static class GameHandler extends Handler {
        private final WeakReference<GameActivity> activityRef;

        GameHandler(GameActivity activity) {
            super(Looper.getMainLooper());
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            GameActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            switch (msg.what) {
                case MSG_GAME_OVER:
                    activity.showGameOverDialog(msg.arg1);
                    break;
                default:
                    break;
            }
        }
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
        // 清掉还没处理的消息，避免 Handler 继续持有已销毁的 Activity
        handler.removeCallbacksAndMessages(null);
        if (gameView != null) {
            gameView.shutdown();
        }
        if (world != null) {
            world.stop();
        }
        if (sound != null) {
            sound.release();
        }
        if (scoreDao != null) {
            scoreDao.close();
        }
        super.onDestroy();
    }
}
