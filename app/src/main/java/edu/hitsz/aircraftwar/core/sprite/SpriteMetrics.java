package edu.hitsz.aircraftwar.core.sprite;

/**
 * 精灵尺寸提供者。
 * <p>
 * 核心逻辑层需要宽高来做碰撞检测，但它<b>不应该依赖任何图像类型</b>
 * （Windows 版用的是 {@code BufferedImage}，Android 用的是 {@code Bitmap}）。
 * 所以把「取宽高」抽成一个接口，由平台层在启动时注入实现：
 * 桌面端从图片读，Android 端从解码后的 Bitmap 读，单元测试注入固定值。
 */
public interface SpriteMetrics {

    int widthOf(Class<?> type);

    int heightOf(Class<?> type);
}
