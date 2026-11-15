package edu.hitsz.aircraftwar.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import edu.hitsz.aircraftwar.core.EasyGameWorld;
import edu.hitsz.aircraftwar.core.GameWorld;
import edu.hitsz.aircraftwar.core.HardGameWorld;
import edu.hitsz.aircraftwar.core.NormalGameWorld;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.sprite.SpriteMetricsHolder;
import edu.hitsz.aircraftwar.core.world.PlayField;
import edu.hitsz.aircraftwar.render.Assets;
import edu.hitsz.aircraftwar.render.GameSurfaceView;

/**
 * 游戏页：承载 {@link GameSurfaceView}，负责资源装载、战场尺寸计算与生命周期。
 * <p>
 * 战场尺寸按屏幕长宽比动态计算：固定逻辑宽度 512，高度随屏幕比例伸缩。
 * Windows 版写死 512x768，直接搬到 20:9 手机上会上下留黑边；
 * 按比例伸缩可以铺满屏幕，代价是纵向更"深"、难度略降，后续可通过刷怪节奏调回。
 */
public class GameActivity extends AppCompatActivity {

    public static final String EXTRA_DIFFICULTY = "difficulty";

    private GameSurfaceView gameView;
    private GameWorld world;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterFullscreen();

        // 1) 资源与精灵尺寸必须先装好，核心层创建实体时要向它问宽高
        Assets assets = Assets.load(this);
        SpriteMetricsHolder.install(assets);

        // 2) 按屏幕比例确定战场尺寸，再创建世界（英雄机初始位置依赖战场高度）
        configurePlayField();

        // 3) 创建游戏世界
        world = createWorld(readDifficulty());
        world.start();

        // 4) 挂载渲染视图
        gameView = new GameSurfaceView(this, world, assets);
        gameView.setCallback(this::onGameOver);
        setContentView(gameView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        // 兜底：极端比例下保证至少为 512x768 的正常形变范围
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
        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("游戏结束")
                    .setMessage("本局得分：" + score)
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) {
            gameView.setPaused(false);
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
        super.onDestroy();
    }
}
