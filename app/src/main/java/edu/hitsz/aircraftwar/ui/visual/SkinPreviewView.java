package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

/**
 * 皮肤页顶部的「当前皮肤」展示位。
 * <p>
 * 静态截一张图也能看，但机型放在这里轻轻上下浮动 + 呼吸光晕，
 * 页面的「活」感就出来了 —— 而且代价只是几行三角函数，不需要 GIF 或 Lottie。
 * 换皮肤时用一次缩放回弹（1.18 → 1.0）给个反馈，比直接换图更容易被眼睛捕捉。
 */
public class SkinPreviewView extends View implements Choreographer.FrameCallback {

    private static final int ACCENT = 0xFF3EC8FF;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private Bitmap bitmap;
    private Shader glowShader;

    private boolean running;
    private long lastFrameNanos;
    private float timeSec;
    private long popStartNanos;

    public SkinPreviewView(Context context) {
        this(context, null);
    }

    public SkinPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
    }

    public void setSkin(Bitmap skinBitmap) {
        this.bitmap = skinBitmap;
        popStartNanos = System.nanoTime();
        invalidate();
    }

    // ---------------- 帧循环 ----------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!running) {
            running = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) {
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nanos;
        }
        timeSec += Math.min((nanos - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = nanos;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        glowShader = new RadialGradient(w / 2f, h * 0.52f, Math.max(w, h) * 0.42f,
                new int[]{0x333EC8FF, 0x000E1218}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        glowPaint.setShader(glowShader);
        canvas.drawRect(0f, 0f, w, h, glowPaint);
        glowPaint.setShader(null);

        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        float centerX = w / 2f;
        float centerY = h * 0.5f;
        float target = h * 0.62f;
        float scale = Math.min(target / bitmap.getHeight(), (w * 0.55f) / bitmap.getWidth());

        // 换皮肤后的回弹：0.3 秒内从 1.18 收回到 1.0
        float pop = 1f;
        if (popStartNanos > 0L) {
            float t = (System.nanoTime() - popStartNanos) / 1e9f;
            if (t < 0.3f) {
                pop = 1f + 0.18f * (1f - t / 0.3f) * (1f - t / 0.3f);
            } else {
                popStartNanos = 0L;
            }
        }

        float bob = (float) Math.sin(timeSec * 2.0) * 5f * density;
        float tilt = (float) Math.sin(timeSec * 1.4) * 2.5f;

        // 地面阴影：跟着浮动的幅度轻微缩放，浮得越高影子越小
        float shadowScale = 1f - bob / (60f * density);
        shadowPaint.setColor(0x66000000);
        canvas.drawOval(centerX - bitmap.getWidth() * scale * 0.42f * shadowScale,
                centerY + bitmap.getHeight() * scale * 0.52f - 2f * density,
                centerX + bitmap.getWidth() * scale * 0.42f * shadowScale,
                centerY + bitmap.getHeight() * scale * 0.52f + 8f * density,
                shadowPaint);

        canvas.save();
        canvas.translate(centerX, centerY + bob);
        canvas.rotate(tilt);
        canvas.scale(scale * pop, scale * pop);
        canvas.drawBitmap(bitmap, -bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f, bitmapPaint);
        canvas.restore();
    }
}
