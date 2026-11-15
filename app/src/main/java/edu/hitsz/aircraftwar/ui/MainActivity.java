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

import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 入口页（临时版）：先放三个难度按钮，等 M3 再换成完整菜单。
 * 现在的作用是把 GameActivity 接进流程，便于在真机/模拟器上验证渲染与手感。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0E1218);

        TextView title = new TextView(this);
        title.setText("飞机大战");
        title.setTextSize(40);
        title.setTextColor(0xFFE9EEF7);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("AIRCRAFT WAR · Android");
        subtitle.setTextSize(12);
        subtitle.setTextColor(0xFF3EC8FF);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 48);
        root.addView(subtitle);

        root.addView(difficultyButton("简单 EASY", Difficulty.EASY));
        root.addView(difficultyButton("普通 NORMAL", Difficulty.NORMAL));
        root.addView(difficultyButton("困难 HARD", Difficulty.HARD));

        setContentView(root);
    }

    private Button difficultyButton(String label, Difficulty difficulty) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTextColor(0xFFE9EEF7);
        button.setBackgroundColor(Color.rgb(0x1F, 0x27, 0x34));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(240), dp(52));
        params.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name());
            startActivity(intent);
        });
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
