package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 开场动画：黑场亮起 → 扫描线展开 → 星点迸发 → 标题逐字浮出 → 高光扫过 → 标题飞入页头 → 交还菜单。
 * <p>
 * 设计上有三件事值得说：
 * <ol>
 *   <li><b>时间轴是一张常量表</b>（下面的 {@code T_*} 字段），所有节拍都从这一处读。
 *       想整体快 10%、或者把某一段拖长，只改数字，不用翻逻辑——这是做动效最省事的组织方式。</li>
 *   <li><b>结束时不硬切</b>：标题缩回页头真实位置后，本层的标题与布局里真正的
 *       {@link TextView} 做 160ms 交叉淡入，再把自己从父容器摘掉。
 *       因为两边字号字重一致，交叉期间肉眼看不出差别，也就没有常见的「动画结束闪一下」。</li>
 *   <li><b>光晕走离屏位图</b>：{@link BlurMaskFilter} 在硬件加速画布上并不可靠
 *       （实测直接画不出效果），所以先用一张软件 {@link Canvas} 把模糊字画进
 *       {@link Bitmap}，运行时只做一次贴图。模糊半径还能随缩放一起放大，代价是多占约 0.6MB。</li>
 * </ol>
 * 所有缓动都是解析函数（{@code easeOutCubic} 等），不依赖属性动画框架，
 * 这样 60fps 下每帧位置都是时间的光滑函数，卡一帧也不会累积误差。
 */
public class IntroOverlayView extends View implements Choreographer.FrameCallback {

    public interface Listener {
        /** 动画结束（或被点击跳过）后回调，此时本 View 已从父容器移除 */
        void onIntroFinished();
    }

    // ---------------- 时间轴（毫秒） ----------------
    private static final float T_COVER_END = 260f;    // 黑场淡出完成
    private static final float T_LINE_IN = 90f;       // 扫描线展开
    private static final float T_LINE_END = 520f;
    private static final float T_BURST = 260f;        // 星点迸发
    private static final float T_BURST_END = 900f;
    private static final float T_TITLE = 380f;        // 标题第一个字出现
    private static final float T_CHAR_STAGGER = 55f;  // 逐字间隔
    private static final float T_CHAR_DUR = 520f;     // 单字时长
    private static final float T_SWEEP = 1080f;       // 高光扫过
    private static final float T_SWEEP_END = 1600f;
    private static final float T_TRAVEL = 1720f;      // 标题飞向页头
    private static final float T_TRAVEL_END = 2120f;
    private static final float T_SUB = 2000f;         // 副标题淡入
    private static final float T_SUB_END = 2360f;
    private static final float T_CROSS = 2120f;       // 与真实标题交叉淡入
    private static final float T_CROSS_END = 2280f;
    private static final float T_END = 2360f;

    private static final int ACCENT = 0xFF3EC8FF;

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint();

    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private final float density;

    private TextView titleView;
    private TextView subtitleView;
    private Listener listener;

    private String titleText = "";
    private int charCount;
    private float[] charLeft = new float[0];
    private float textWidth;
    private float heroScale = 1.7f;

    private Bitmap[] glowChar;
    private float glowPad;

    private float heroX;
    private float heroY;
    private float finalCx;
    private float finalCy;
    private boolean targetsReady;

    private long startNanos;
    private long frameNanos;
    private boolean running;
    private boolean finished;
    private boolean skipping;
    private float freezeMs = -1f;

    /** 迸发用的星点：角度 + 距离 + 半径，建一次用一辈子 */
    private static final int DOT_COUNT = 26;
    private final float[] dotAngle = new float[DOT_COUNT];
    private final float[] dotDistance = new float[DOT_COUNT];
    private final float[] dotRadius = new float[DOT_COUNT];

    public IntroOverlayView(Context context) {
        this(context, null);
    }

    public IntroOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(false);

        for (int i = 0; i < DOT_COUNT; i++) {
            // 用黄金角散开，比纯随机更均匀，不会扎堆
            dotAngle[i] = (float) (i * 2.39996) % 6.283f;
            dotDistance[i] = (0.55f + 0.45f * (i % 5) / 4f) * 150f * density;
            dotRadius[i] = (1.0f + 0.9f * (i % 3) / 2f) * density;
        }
    }

    /** 传入布局里真实的标题/副标题，用于计算落点、构造光晕位图与最后交接 */
    public void setTargets(TextView title, TextView subtitle) {
        this.titleView = title;
        this.subtitleView = subtitle;
        if (title == null) {
            return;
        }
        titleText = title.getText().toString();
        charCount = titleText.length();
        charLeft = new float[charCount];

        titlePaint.setTextSize(title.getTextSize());
        titlePaint.setTypeface(title.getTypeface());
        titlePaint.setFakeBoldText(true);
        titlePaint.getFontMetrics(fontMetrics);
        textWidth = titlePaint.measureText(titleText);
        for (int i = 0; i < charCount; i++) {
            charLeft[i] = titlePaint.measureText(titleText, 0, i);
        }

        // 纵向渐变：上白下青，和 GradientTextView 保持一致，交接时才看不出接缝。
        // 坐标以「文字中心」为原点，因为绘制时画布已经平移到中心。
        titlePaint.setShader(new LinearGradient(0f, -title.getTextSize() * 0.85f,
                0f, title.getTextSize() * 0.30f,
                0xFFFFFFFF, 0xFF78C6F0, Shader.TileMode.CLAMP));

        // 扫光这次是「把同一个标题用白色再画一遍，只露出斜带内的部分」，
        // 所以必须继承标题的字号与字重，否则画出来的是一行 12px 的小字
        sweepPaint.set(titlePaint);
        sweepPaint.setShader(null);
        sweepPaint.setColor(Color.WHITE);

        buildGlowBitmaps();
    }

    /**
     * 预渲染光晕：软件画布上 BlurMaskFilter 一定生效，硬件画布上不一定。
     * <p>
     * 每个字单独一张，而不是整串一张 —— 因为字幕是逐字浮出的，
     * 整串共用一张的话最后那个字会出现一团「还没亮就先有光晕」的鬼影。
     * 每张位图都以「该字中心」为原点构图，运行时逐字贴在该字的位置上。
     */
    private void buildGlowBitmaps() {
        if (textWidth <= 0f || glowChar != null) {
            return;
        }
        glowPad = 20f * density;
        int bh = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent + glowPad * 2f);
        if (bh <= 0) {
            return;
        }
        glowChar = new Bitmap[charCount];
        for (int i = 0; i < charCount; i++) {
            float start = charLeft[i];
            float end = i + 1 < charCount ? charLeft[i + 1] : textWidth;
            int bw = (int) Math.ceil(end - start + glowPad * 2f);
            if (bw <= 0) {
                continue;
            }

            Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(ACCENT);
            paint.setTextSize(titlePaint.getTextSize());
            paint.setTypeface(titlePaint.getTypeface());
            paint.setFakeBoldText(true);
            paint.setMaskFilter(new BlurMaskFilter(9f * density, BlurMaskFilter.Blur.NORMAL));

            float baseline = bh / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(titleText, i, i + 1, glowPad, baseline, paint);
            glowChar[i] = bitmap;
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 调试用：把画面冻结在某个时刻，方便一帧一帧截图检查排版 */
    public void setFreezeTimeMs(float ms) {
        this.freezeMs = ms;
        invalidate();
    }

    // ---------------- 帧循环 ----------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!running && !finished) {
            running = true;
            startNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        if (glowChar != null) {
            for (Bitmap bitmap : glowChar) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            glowChar = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) {
            return;
        }
        frameNanos = nanos;
        if (startNanos == 0L) {
            startNanos = nanos;
        }
        if (freezeMs < 0f && timeMs() >= T_END) {
            finish();
            return;
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private float timeMs() {
        if (freezeMs >= 0f) {
            return freezeMs;
        }
        if (startNanos == 0L || frameNanos == 0L) {
            return 0f;
        }
        return (frameNanos - startNanos) / 1_000_000f;
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        heroX = w / 2f;
        heroY = h * 0.46f;
        // 主角态的标题宽度控制在屏宽 84% 以内，再大的话窄屏会顶到边
        if (textWidth > 0f && w > 0) {
            heroScale = Math.max(1.25f, Math.min(1.85f, 0.84f * w / textWidth));
        }
        targetsReady = false;
    }

    private void resolveTargets() {
        if (targetsReady || titleView == null || titleView.getWidth() == 0) {
            return;
        }
        int[] self = new int[2];
        int[] target = new int[2];
        getLocationInWindow(self);
        titleView.getLocationInWindow(target);
        finalCx = target[0] - self[0] + titleView.getWidth() / 2f;
        finalCy = target[1] - self[1] + titleView.getHeight() / 2f;
        targetsReady = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = timeMs();
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        resolveTargets();
        if (!targetsReady) {
            finalCx = w / 2f;
            finalCy = h * 0.18f;
        }

        drawCover(canvas, t, w, h);
        drawScanLine(canvas, t);
        drawBurst(canvas, t);
        if (charCount > 0) {
            drawTitle(canvas, t);
        }
        syncMenuText(t);
    }

    /** ① 黑场：从全黑淡出，露出下面的星空 */
    private void drawCover(Canvas canvas, float t, float w, float h) {
        if (t >= T_COVER_END) {
            return;
        }
        coverPaint.setColor(Color.BLACK);
        coverPaint.setAlpha((int) (255 * (1f - easeOutCubic(t / T_COVER_END))));
        canvas.drawRect(0f, 0f, w, h, coverPaint);
    }

    /** ② 扫描线：从中心向两侧展开，后段慢慢收光 */
    private void drawScanLine(Canvas canvas, float t) {
        float p = clamp01((t - T_LINE_IN) / (T_LINE_END - T_LINE_IN));
        if (p <= 0f) {
            return;
        }
        float fade = 1f - clamp01((t - T_SWEEP_END) / (T_TRAVEL - T_SWEEP_END));
        if (fade <= 0f) {
            return;
        }
        float half = getWidth() * 0.31f * easeOutCubic(p);

        linePaint.setColor(ACCENT);
        linePaint.setAlpha((int) (60 * fade));
        linePaint.setStrokeWidth(6f * density);
        linePaint.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        canvas.drawLine(heroX - half, heroY, heroX + half, heroY, linePaint);

        linePaint.setMaskFilter(null);
        linePaint.setAlpha((int) (210 * fade));
        linePaint.setStrokeWidth(1.2f * density);
        canvas.drawLine(heroX - half, heroY, heroX + half, heroY, linePaint);
    }

    /** ③ 星点迸发：椭圆分布，比正圆更有速度感 */
    private void drawBurst(Canvas canvas, float t) {
        float p = clamp01((t - T_BURST) / (T_BURST_END - T_BURST));
        if (p <= 0f || p >= 1f) {
            return;
        }
        float travel = easeOutCubic(p);
        int alpha = (int) (255 * (1f - p) * (1f - p));
        dotPaint.setColor(0xFFBFE6FF);
        for (int i = 0; i < DOT_COUNT; i++) {
            float d = dotDistance[i] * travel;
            float x = heroX + (float) Math.cos(dotAngle[i]) * d;
            float y = heroY + (float) Math.sin(dotAngle[i]) * d * 0.52f;
            dotPaint.setAlpha(alpha);
            canvas.drawCircle(x, y, dotRadius[i] * (1f - p * 0.55f), dotPaint);
        }
    }

    /** ④⑤ 标题：逐字浮出 → 高光 → 飞入页头 */
    private void drawTitle(Canvas canvas, float t) {
        float travelRaw = clamp01((t - T_TRAVEL) / (T_TRAVEL_END - T_TRAVEL));
        float travel = smoothstep(travelRaw);

        float scale = heroScale + (1f - heroScale) * travel;
        float cx = heroX + (finalCx - heroX) * travel;
        float cy = heroY + (finalCy - heroY) * travel;

        // 与真实标题交叉淡入：本层淡出的同时把下面 TextView 的 alpha 拉起来
        float cross = clamp01((t - T_CROSS) / (T_CROSS_END - T_CROSS));
        float blockAlpha = 1f - cross;
        if (blockAlpha <= 0f) {
            return;
        }

        float entryEnd = T_TITLE + (charCount - 1) * T_CHAR_STAGGER + T_CHAR_DUR;
        float entryP = easeOutCubic(clamp01((t - T_TITLE) / (entryEnd - T_TITLE)));
        float trackP = entryP;
        float tracking = 10f * density * (1f - trackP);

        float baseline = -(fontMetrics.ascent + fontMetrics.descent) / 2f;
        float left = -textWidth / 2f;
        float centerIndex = (charCount - 1) / 2f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // 光晕比填充先退场：交接前就把它压到 0，
        // 这样最后停下来的一帧和页头那个没有光晕的 TextView 完全一致
        float glowFade = clamp01(1f - cross * 2f) * (1f - travel * 0.55f);

        for (int i = 0; i < charCount; i++) {
            float p = clamp01((t - (T_TITLE + i * T_CHAR_STAGGER)) / T_CHAR_DUR);
            if (p <= 0f) {
                continue;
            }
            float a = easeOutCubic(p) * blockAlpha;
            float dy = (1f - easeOutCubic(p)) * 20f * density;
            float x = left + charLeft[i] + tracking * (i - centerIndex);

            Bitmap glow = glowChar != null && i < glowChar.length ? glowChar[i] : null;
            if (glow != null) {
                int glowAlpha = (int) (170 * a * glowFade);
                if (glowAlpha > 0) {
                    glowPaint.setAlpha(glowAlpha);
                    canvas.drawBitmap(glow, x - glowPad, -glow.getHeight() / 2f + dy, glowPaint);
                }
            }

            titlePaint.setAlpha((int) (255 * a));
            canvas.drawText(titleText, i, i + 1, x, baseline + dy, titlePaint);
        }
        titlePaint.setAlpha(255);

        drawSweep(canvas, t, left, baseline);
        canvas.restore();
    }

    /**
     * 高光：先把画布转 -18°，裁出一条斜带，再转回来重绘白色文字。
     * 裁切区域建立后就固定在设备坐标系里，因此转回来画的字依然被斜带裁住 ——
     * 不用 {@code saveLayer} 就能把高光限制在字形内部，省一次离屏渲染。
     */
    private void drawSweep(Canvas canvas, float t, float left, float baseline) {
        float p = clamp01((t - T_SWEEP) / (T_SWEEP_END - T_SWEEP));
        if (p <= 0f || p >= 1f) {
            return;
        }
        float band = textWidth * 0.30f;
        float x = left - textWidth * 0.45f + (textWidth * 1.9f) * easeInOutCubic(p);

        canvas.save();
        canvas.rotate(-18f);
        canvas.clipRect(x, -textWidth, x + band, textWidth);
        canvas.rotate(18f);
        sweepPaint.setAlpha((int) (165 * Math.sin(Math.PI * p)));
        canvas.drawText(titleText, left, baseline, sweepPaint);
        canvas.restore();
    }

    /** 把下面真实控件的透明度跟上动画节奏，实现无缝交接 */
    private void syncMenuText(float t) {
        if (titleView != null) {
            float cross = clamp01((t - T_CROSS) / (T_CROSS_END - T_CROSS));
            setAlphaQuietly(titleView, cross);
        }
        if (subtitleView != null) {
            float p = clamp01((t - T_SUB) / (T_SUB_END - T_SUB));
            setAlphaQuietly(subtitleView, easeOutCubic(p));
        }
    }

    private static void setAlphaQuietly(View view, float alpha) {
        // 每帧都 setAlpha 会反复触发重绘，变化小于 2% 就跳过
        if (Math.abs(view.getAlpha() - alpha) > 0.02f) {
            view.setAlpha(alpha);
        }
    }

    // ---------------- 交互与收尾 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            skip();
        }
        return true;
    }

    /** 任意点击立即跳过：快速淡出后直接交接，不让人等 */
    private void skip() {
        if (skipping || finished) {
            return;
        }
        skipping = true;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        animate().alpha(0f).setDuration(140L).withEndAction(this::finish).start();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);

        if (titleView != null) {
            titleView.setAlpha(1f);
        }
        if (subtitleView != null) {
            subtitleView.setAlpha(1f);
        }
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
        if (listener != null) {
            listener.onIntroFinished();
        }
    }

    // ---------------- 缓动 ----------------

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static float easeInOutCubic(float p) {
        return p < 0.5f ? 4f * p * p * p : 1f - (float) Math.pow(-2f * p + 2f, 3) / 2f;
    }

    private static float smoothstep(float p) {
        return p * p * (3f - 2f * p);
    }
}
