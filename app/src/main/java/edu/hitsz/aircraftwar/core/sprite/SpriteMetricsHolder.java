package edu.hitsz.aircraftwar.core.sprite;

/**
 * {@link SpriteMetrics} 的全局持有者。
 * <p>
 * 实体是在工厂里零散创建的，逐个传尺寸参数会污染所有构造函数，
 * 因此这里用一个进程级单例。默认实现返回固定尺寸，保证**没有平台层注入时也能跑**——
 * 这样纯逻辑层可以在 JVM 单元测试里直接使用，不需要加载任何图片。
 */
public final class SpriteMetricsHolder {

    /** 默认尺寸：够大，保证碰撞检测不会因为 0 宽高而失效 */
    private static final int DEFAULT_SIZE = 32;

    private static volatile SpriteMetrics metrics = new SpriteMetrics() {
        @Override
        public int widthOf(Class<?> type) {
            return DEFAULT_SIZE;
        }

        @Override
        public int heightOf(Class<?> type) {
            return DEFAULT_SIZE;
        }
    };

    private SpriteMetricsHolder() {
    }

    public static void install(SpriteMetrics provider) {
        if (provider != null) {
            metrics = provider;
        }
    }

    public static int width(Class<?> type) {
        return metrics.widthOf(type);
    }

    public static int height(Class<?> type) {
        return metrics.heightOf(type);
    }
}
