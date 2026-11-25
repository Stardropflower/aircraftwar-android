package edu.hitsz.aircraftwar.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 系统栏内边距处理。
 * <p>
 * 从 Android 15（targetSdk 35）开始，系统对非全屏页面也会强制 edge-to-edge：
 * 内容会绘制到状态栏和导航栏底下。普通页面必须自己把系统栏高度补成内边距，
 * 否则顶部的返回按钮会被状态栏压住、底部内容会被手势条盖住。
 * <p>
 * 游戏页是全屏沉浸式，自己隐藏了系统栏，不需要这个处理。
 */
final class UiInsets {

    private UiInsets() {
    }

    /** 把根布局的内边距设为系统栏高度 */
    static void applySystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
