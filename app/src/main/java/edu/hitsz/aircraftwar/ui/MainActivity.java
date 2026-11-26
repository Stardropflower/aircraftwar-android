package edu.hitsz.aircraftwar.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.BuildConfig;
import edu.hitsz.aircraftwar.R;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;
import edu.hitsz.aircraftwar.ui.visual.IntroOverlayView;

/**
 * 入口页：选择难度、查看本机最高分、进入皮肤选择、切换音乐开关。
 * <p>
 * 布局走 XML（{@code res/layout/activity_main.xml}），这是实验三讲的「静态布局」路线：
 * 界面结构在 XML 里声明，Java 只负责找控件（{@code findViewById}）和绑事件（{@code setOnClickListener}）。
 * <p>
 * 视觉部分有两层动态内容，都由代码驱动、不依赖任何图片资源：
 * <ol>
 *   <li>{@link edu.hitsz.aircraftwar.ui.visual.StarfieldView} —— 常驻的动态星空背景；</li>
 *   <li>{@link IntroOverlayView} —— 冷启动时的开场动画，播完把自己摘掉。</li>
 * </ol>
 * 开场动画只在「这个 Activity 是新建的」时候播（{@code savedInstanceState == null}）：
 * 从游戏页退回来、或者转屏重建时不重播，否则每打一局都要等一次片头。
 */
public class MainActivity extends AppCompatActivity {

    private LocalStore store;
    private TextView bestScoreLabel;
    private TextView skinLabel;
    private TextView titleLabel;
    private TextView subtitleLabel;
    private Button musicButton;
    private EditText nameInput;

    /** 开场结束后依次淡入的控件，顺序即出场顺序 */
    private View[] cascadeViews;

    /**
     * 调试开关：启动时带上 {@code --ez debug_grant true} 可以让下一局开局自带两个道具，
     * 方便验证背包 UI 与「点击使用」。只在 DEBUG 包里生效，正式包忽略。
     */
    private boolean debugGrant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        debugGrant = getIntent() != null && getIntent().getBooleanExtra("debug_grant", false);

        // ① 加载 XML 布局（实验三第 8 页：Activity 启动时系统调用 onCreate，
        //    通过 setContentView() 加载布局资源）
        setContentView(R.layout.activity_main);
        UiInsets.applySystemBars(findViewById(R.id.menu_scroll));

        // ② 找到布局里的控件
        bestScoreLabel = findViewById(R.id.tv_best);
        skinLabel = findViewById(R.id.tv_skin);
        titleLabel = findViewById(R.id.tv_title);
        subtitleLabel = findViewById(R.id.tv_subtitle);
        musicButton = findViewById(R.id.btn_music);
        nameInput = findViewById(R.id.et_name);
        nameInput.setText(store.getPlayerName());

        // ③ 基于监听的事件处理（实验三第 11 页）：给控件绑监听器
        findViewById(R.id.btn_easy).setOnClickListener(v -> startGame(Difficulty.EASY));
        findViewById(R.id.btn_normal).setOnClickListener(v -> startGame(Difficulty.NORMAL));
        findViewById(R.id.btn_hard).setOnClickListener(v -> startGame(Difficulty.HARD));
        findViewById(R.id.btn_skin).setOnClickListener(v -> openSkinPage());
        findViewById(R.id.btn_rank).setOnClickListener(v -> openRankPage());
        musicButton.setOnClickListener(v -> toggleMusic());

        cascadeViews = new View[]{
                bestScoreLabel,
                findViewById(R.id.row_name),
                findViewById(R.id.btn_easy),
                findViewById(R.id.btn_normal),
                findViewById(R.id.btn_hard),
                skinLabel,
                findViewById(R.id.btn_skin),
                findViewById(R.id.btn_rank),
                musicButton,
        };

        refreshLabels();
        startEntrance(savedInstanceState == null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从皮肤页返回后要刷新「当前皮肤」和最高分
        if (bestScoreLabel != null) {
            refreshLabels();
        }
    }

    // ---------------- 入场动画 ----------------

    /**
     * @param playIntro 是否播开场动画。只有冷启动才播
     */
    private void startEntrance(boolean playIntro) {
        for (View view : cascadeViews) {
            view.setAlpha(0f);
            view.setTranslationY(dp(14f));
        }
        titleLabel.setAlpha(0f);
        subtitleLabel.setAlpha(0f);

        if (BuildConfig.DEBUG && getIntent() != null
                && getIntent().getBooleanExtra("replay_intro", false)) {
            playIntro = true;
        }

        if (!playIntro) {
            playCascadeIn();
            return;
        }

        IntroOverlayView overlay = new IntroOverlayView(this);
        overlay.setTargets(titleLabel, subtitleLabel);
        overlay.setListener(this::playCascadeIn);

        // 调试：把动画冻结在指定毫秒，方便逐帧截图检查（只对 DEBUG 包生效）
        if (BuildConfig.DEBUG && getIntent() != null) {
            float freeze = getIntent().getFloatExtra("intro_at", -1f);
            if (freeze >= 0f) {
                overlay.setFreezeTimeMs(freeze);
            }
        }

        FrameLayout root = findViewById(R.id.root_main);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** 菜单项依次淡入：同一段位移（14dp → 0）+ 递增延迟，比整块一起出现更"有节奏" */
    private void playCascadeIn() {
        titleLabel.setAlpha(1f);
        subtitleLabel.setAlpha(1f);
        for (int i = 0; i < cascadeViews.length; i++) {
            cascadeViews[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 45L)
                    .setDuration(320L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ---------------- 页面跳转 ----------------

    /** 页面跳转 + 数据传递（实验三第 17 页：Intent + putExtra + startActivity） */
    private void startGame(Difficulty difficulty) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name());
        if (debugGrant) {
            intent.putExtra(GameActivity.EXTRA_DEBUG_GRANT, true);
        }
        startActivity(intent);
    }

    private void openSkinPage() {
        startActivity(new Intent(this, SkinActivity.class));
    }

    private void openRankPage() {
        startActivity(new Intent(this, RankActivity.class));
    }

    /** 离开页面时保存昵称，避免为了存名字而多弹一次确认框 */
    @Override
    protected void onPause() {
        if (nameInput != null) {
            store.setPlayerName(nameInput.getText().toString());
        }
        super.onPause();
    }

    /** 音乐开关（实验三实验内容第 3 条）：状态存进 SharedPreferences，进游戏时读取 */
    private void toggleMusic() {
        store.setMuted(!store.isMuted());
        refreshLabels();
    }

    private void refreshLabels() {
        int easy = store.getBestScore(Difficulty.EASY);
        int normal = store.getBestScore(Difficulty.NORMAL);
        int hard = store.getBestScore(Difficulty.HARD);
        bestScoreLabel.setText("本机最高分　简单 " + easy + " · 普通 " + normal + " · 困难 " + hard);

        HeroAircraft.Skin skin = store.getSelectedSkin();
        skinLabel.setText("当前皮肤：" + skin.getDisplayName());

        boolean muted = store.isMuted();
        musicButton.setText(muted ? "音乐音效：关闭" : "音乐音效：开启");
    }
}
