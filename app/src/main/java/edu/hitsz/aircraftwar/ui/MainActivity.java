package edu.hitsz.aircraftwar.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 入口 Activity。
 * <p>
 * 当前只是工具链验证用的占位页；后续由菜单页与游戏页替代。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setText("AircraftWar on Android\n工具链验证占位页");
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(20f);
        tv.setTextColor(0xFFE9EEF7);
        setContentView(tv);
    }
}
