package edu.hitsz.aircraftwar.core.world;

/**
 * 战场尺寸。
 * <p>
 * Windows 版把它写死成常量（{@code Main.WINDOW_WIDTH = 512}、{@code WINDOW_HEIGHT = 768}），
 * 因为窗口大小固定。Android 设备分辨率各异，所以改成运行期注入：
 * 渲染层按屏幕宽高比算出一个逻辑战场尺寸后调用 {@link #configure(int, int)}。
 * <p>
 * 保持静态访问是为了让迁移过来的实体代码（边界反弹、越界消失等）一行都不用改。
 */
public final class PlayField {

    /** 默认值沿用 Windows 版的 512x768，保证迁移前后行为一致 */
    private static volatile int width = 512;
    private static volatile int height = 768;

    private PlayField() {
    }

    public static void configure(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("非法战场尺寸: " + w + "x" + h);
        }
        width = w;
        height = h;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    /** 仅用于测试：恢复成 Windows 版默认尺寸 */
    public static void resetToDefault() {
        width = 512;
        height = 768;
    }
}
