package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * 主菜单背景：深空渐变 + 两团星云 + 三层视差星点 + 暗角。
 * <p>
 * 为什么不用一张静态背景图（{@code bg.jpg} 那套）：
 * <ul>
 *   <li>静态图只能拉伸，荣耀这类窄长屏上会被压扁；这里全用矢量/程序化绘制，任何比例都自然；</li>
 *   <li>一张 1080x2400 的背景图约 1~2 MB，这里代码不到 10 KB；</li>
 *   <li>游戏页背景要跟着滚动，菜单页则应该静止——分开实现互不干扰。</li>
 * </ul>
 * 帧循环用 {@link Choreographer} 而不是 Timer/Thread：它把回调对齐到屏幕的 VSYNC，
 * 只在真正要刷新时触发，比固定间隔的 Timer 更省电也更不容易掉帧。
 */
public class StarfieldView extends View implements Choreographer.FrameCallback {

    /** 三层视差：远处小而慢，近处大而快，制造纵深 */
    private static final int LAYER_COUNT = 3;
    private static final int[] STAR_COUNT = {44, 26, 12};
    private static final float[] STAR_RADIUS_DP = {0.65f, 1.05f, 1.70f};
    private static final float[] STAR_ALPHA = {0.30f, 0.55f, 0.88f};
    private static final float[] STAR_SPEED_DP = {4f, 11f, 22f};

    /** 背景水印文字，被裁剪的话会像渲染错误，所以按屏宽等比缩放 */
    private static final String WATERMARK = "AIRCRAFT WAR";

    private final Paint bgPaint = new Paint();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Random random = new Random(20241126L);

    private final float[][] starX = new float[LAYER_COUNT][];
    private final float[][] starY = new float[LAYER_COUNT][];
    private final float[][] starPhase = new float[LAYER_COUNT][];

    private float density;
    private Shader bgShader;
    private Shader nebulaShaderA;
    private Shader nebulaShaderB;
    private Shader vignetteShader;

    private boolean running;
    private long lastFrameNanos;
    private float timeSec;
    private boolean starsReady;

    public StarfieldView(Context context) {
        this(context, null);
    }

    public StarfieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            starX[layer] = new float[STAR_COUNT[layer]];
            starY[layer] = new float[STAR_COUNT[layer]];
            starPhase[layer] = new float[STAR_COUNT[layer]];
        }
        watermarkPaint.setTextAlign(Paint.Align.CENTER);
        watermarkPaint.setFakeBoldText(true);
    }

    // ---------------- 帧循环 ----------------

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos;
        }
        // dt 封顶 50ms：从后台切回来时不会因为时间跳跃让星点瞬移一大段
        float dt = Math.min((frameTimeNanos - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = frameTimeNanos;
        timeSec += dt;

        stepStars(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stepStars(float dt) {
        int height = getHeight();
        if (height <= 0) {
            return;
        }
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            float speed = STAR_SPEED_DP[layer] * density * dt;
            for (int i = 0; i < STAR_COUNT[layer]; i++) {
                starY[layer][i] += speed;
                if (starY[layer][i] > height + 8) {
                    starY[layer][i] = -8;
                    starX[layer][i] = random.nextFloat() * getWidth();
                }
            }
        }
    }

    private void start() {
        if (running || !starsReady) {
            return;
        }
        running = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    // 页面不可见时立刻停帧，别在后台白白烧电
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            start();
        } else {
            stop();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    // ---------------- 尺寸与着色器 ----------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }

        bgShader = new LinearGradient(0, 0, 0, h,
                0xFF0C1119, 0xFF05080D, Shader.TileMode.CLAMP);

        // 星云：中心亮、边缘全透明，用 CLAMP 铺满即可
        nebulaShaderA = new RadialGradient(w * 0.26f, h * 0.18f, w * 0.82f,
                new int[]{0x2A3EC8FF, 0x000E1218}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        nebulaShaderB = new RadialGradient(w * 0.80f, h * 0.70f, w * 0.92f,
                new int[]{0x1F2E6E9E, 0x000E1218}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);

        // 暗角：中心透明、四周压暗，让视线落在中间内容上
        vignetteShader = new RadialGradient(w * 0.5f, h * 0.44f, Math.max(w, h) * 0.78f,
                new int[]{0x00000000, 0x00000000, 0x59000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

        if (!starsReady) {
            for (int layer = 0; layer < LAYER_COUNT; layer++) {
                for (int i = 0; i < STAR_COUNT[layer]; i++) {
                    starX[layer][i] = random.nextFloat() * w;
                    starY[layer][i] = random.nextFloat() * h;
                    starPhase[layer][i] = random.nextFloat() * 6.283f;
                }
            }
            starsReady = true;
            start();
        }

        // 底部小字按屏宽取字号。
        // 之前做过「超大号水印铺在中间」的版本，结果菜单按钮正好压在上面，
        // 只在左右两侧露出半截字，看着像渲染出错——装饰文字不能和内容抢位置。
        watermarkPaint.setTextSize(w * 0.036f);
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        bgPaint.setShader(bgShader);
        canvas.drawRect(0, 0, w, h, bgPaint);

        bgPaint.setShader(nebulaShaderA);
        canvas.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(nebulaShaderB);
        canvas.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(null);

        drawStars(canvas);
        drawWatermark(canvas, w, h);

        bgPaint.setShader(vignetteShader);
        canvas.drawRect(0, 0, w, h, bgPaint);
        bgPaint.setShader(null);
    }

    private void drawStars(Canvas canvas) {
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            float radius = STAR_RADIUS_DP[layer] * density;
            int baseAlpha = (int) (STAR_ALPHA[layer] * 255);
            for (int i = 0; i < STAR_COUNT[layer]; i++) {
                // 闪烁：每颗星自己的相位，避免整片一起呼吸显得假
                float twinkle = 0.72f + 0.28f * (float) Math.sin(timeSec * 1.1f + starPhase[layer][i]);
                starPaint.setColor(0xFFCFE8FF);
                starPaint.setAlpha((int) (baseAlpha * twinkle));
                canvas.drawCircle(starX[layer][i], starY[layer][i], radius, starPaint);
            }
        }
    }

    /**
     * 底部装饰小字：压在画面最下缘、透明度很低，只负责让底部不至于空成一块黑。
     * 它属于背景层，所以永远在内容下层，不会挡住任何可点区域。
     */
    private void drawWatermark(Canvas canvas, int w, int h) {
        watermarkPaint.setColor(0xFF8FA0B8);
        watermarkPaint.setAlpha(0x4A);
        canvas.drawText(WATERMARK, w * 0.5f, h - 40f * density, watermarkPaint);
    }
}
