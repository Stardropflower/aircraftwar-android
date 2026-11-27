package edu.hitsz.aircraftwar.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.R;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;
import edu.hitsz.aircraftwar.net.BattleRoomActivity;

/**
 * 入口页：选择难度、查看本机最高分、进入皮肤选择、切换音乐开关。
 * <p>
 * 布局走 XML（{@code res/layout/activity_main.xml}），这是实验三讲的「静态布局」路线：
 * 界面结构在 XML 里声明，Java 只负责找控件（{@code findViewById}）和绑事件（{@code setOnClickListener}）。
 */
public class MainActivity extends AppCompatActivity {

    private LocalStore store;
    private TextView bestScoreLabel;
    private TextView skinLabel;
    private Button musicButton;
    private EditText nameInput;
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
        UiInsets.applySystemBars(findViewById(R.id.root_main));

        // ② 找到布局里的控件
        bestScoreLabel = findViewById(R.id.tv_best);
        skinLabel = findViewById(R.id.tv_skin);
        musicButton = findViewById(R.id.btn_music);
        nameInput = findViewById(R.id.et_name);
        nameInput.setText(store.getPlayerName());

        // ③ 基于监听的事件处理（实验三第 11 页）：给控件绑监听器
        findViewById(R.id.btn_easy).setOnClickListener(v -> startGame(Difficulty.EASY));
        findViewById(R.id.btn_normal).setOnClickListener(v -> startGame(Difficulty.NORMAL));
        findViewById(R.id.btn_hard).setOnClickListener(v -> startGame(Difficulty.HARD));
        findViewById(R.id.btn_skin).setOnClickListener(v -> openSkinPage());
        findViewById(R.id.btn_rank).setOnClickListener(v -> openRankPage());
        findViewById(R.id.btn_battle).setOnClickListener(v -> openBattlePage());
        musicButton.setOnClickListener(v -> toggleMusic());

        refreshLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从皮肤页返回后要刷新「当前皮肤」和最高分
        if (bestScoreLabel != null) {
            refreshLabels();
        }
    }

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

    private void openBattlePage() {
        startActivity(new Intent(this, BattleRoomActivity.class));
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
