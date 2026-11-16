package edu.hitsz.aircraftwar.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;

/**
 * 入口页：选择难度、查看本机最高分、进入皮肤选择。
 */
public class MainActivity extends AppCompatActivity {

    private LocalStore store;
    private TextView bestScoreLabel;
    private TextView skinLabel;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF0E1218);
        root.setPadding(0, dp(90), 0, 0);

        TextView title = new TextView(this);
        title.setText("飞机大战");
        title.setTextSize(42);
        title.setTextColor(0xFFE9EEF7);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("AIRCRAFT WAR · Android");
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFF3EC8FF);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(28));
        root.addView(subtitle);

        bestScoreLabel = new TextView(this);
        bestScoreLabel.setTextColor(0xFF8FA0B8);
        bestScoreLabel.setTextSize(13);
        bestScoreLabel.setGravity(Gravity.CENTER);
        bestScoreLabel.setPadding(0, 0, 0, dp(24));
        root.addView(bestScoreLabel);

        root.addView(menuButton("简单 EASY", 0xFF46D39A, Difficulty.EASY));
        root.addView(menuButton("普通 NORMAL", 0xFF3EC8FF, Difficulty.NORMAL));
        root.addView(menuButton("困难 HARD", 0xFFFF5C6C, Difficulty.HARD));

        skinLabel = new TextView(this);
        skinLabel.setTextSize(13);
        skinLabel.setTextColor(0xFF8FA0B8);
        skinLabel.setGravity(Gravity.CENTER);
        skinLabel.setPadding(0, dp(28), 0, dp(10));
        root.addView(skinLabel);

        Button skinButton = new Button(this);
        skinButton.setText("皮肤选择");
        skinButton.setTextSize(15);
        skinButton.setAllCaps(false);
        skinButton.setTextColor(0xFFE9EEF7);
        skinButton.setBackgroundColor(Color.rgb(0x1F, 0x27, 0x34));
        LinearLayout.LayoutParams skinParams = new LinearLayout.LayoutParams(dp(240), dp(48));
        skinButton.setLayoutParams(skinParams);
        skinButton.setOnClickListener(v -> startActivity(new Intent(this, SkinActivity.class)));
        root.addView(skinButton);

        setContentView(root);
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

    private void refreshLabels() {
        int easy = store.getBestScore(Difficulty.EASY);
        int normal = store.getBestScore(Difficulty.NORMAL);
        int hard = store.getBestScore(Difficulty.HARD);
        bestScoreLabel.setText("本机最高分　简单 " + easy + " · 普通 " + normal + " · 困难 " + hard);

        HeroAircraft.Skin skin = store.getSelectedSkin();
        skinLabel.setText("当前皮肤：" + skin.getDisplayName());
    }

    private Button menuButton(String label, int accentColor, Difficulty difficulty) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);
        // 文字用难度主题色，底色统一用深色面板
        button.setTextColor(accentColor);
        button.setBackgroundColor(Color.rgb(0x1F, 0x27, 0x34));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(260), dp(54));
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name());
            if (debugGrant) {
                intent.putExtra(GameActivity.EXTRA_DEBUG_GRANT, true);
            }
            startActivity(intent);
        });
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
