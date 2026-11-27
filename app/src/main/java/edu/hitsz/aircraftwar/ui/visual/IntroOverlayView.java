package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import edu.hitsz.aircraftwar.R;

/**
 * 开场动画分两幕：
 * <ol>
 *   <li><b>加载幕</b>（黑屏）：同一架飞机和英文名用射线扫出来。
 *       一道扫描光从左往右、从下往上走过，字形和机身先是一缕缕射线，再合成实体。
 *       这一段的做法对齐瓦罗兰特进游戏时的加载：黑底、几何射线、扫描光把标志「印」出来。</li>
 *   <li><b>衔接幕</b>：黑场褪掉，露出底下的星空；英文名消失；
 *       飞机停在原来那一幕的起点，接着向上滑、翻转成「飞机大战」。</li>
 * </ol>
 * <p>
 * 设计上四件事值得说：
 * <ol>
 *   <li><b>飞机是「同色填充」的剪影</b>：拿游戏里的 hero.png 当遮罩，
 *       用白→青的竖向渐变填满，得到一枚和整体配色一致的蓝色飞机图标；
 *       光晕再用同一张剪影模糊一次，所以两者形状严丝合缝。</li>
 *   <li><b>翻转是横向压扁</b>：{@code scaleX = cos(90°·进度)} 从 1 收到 0，
 *       到 0 的那一帧立刻换成中文标题从 0 展开。<b>这一帧两边都是 0 宽，所以看不见接缝</b>，
 *       这是做「翻面」最省事也最稳的写法，不需要 3D 相机。</li>
 *   <li><b>时间轴是一张常量表</b>（下面的 {@code T_*} 字段），所有节拍都从这一处读。</li>
 *   <li><b>结束时不硬切</b>：标题落在页头真实位置后，本层与布局里真正的 {@link TextView}
 *       做 140ms 交叉淡入，再把自己摘掉，没有常见的「动画结束闪一下」。</li>
 * </ol>
 * 缓动全部是解析函数，60fps 下每帧位置都是时间的光滑函数，卡一帧也不会累积误差。
 */
public class IntroOverlayView extends View implements Choreographer.FrameCallback {

    public interface Listener {
        /** 动画结束（或被点击跳过）后回调，此时本 View 已从父容器移除 */
        void onIntroFinished();
    }

    // ---------------- 时间轴（毫秒） ----------------
    private static final float T_COVER_END = 260f;      // 黑场淡出完成
    private static final float T_LINE_IN = 90f;         // 扫描线展开
    private static final float T_LINE_END = 520f;
    private static final float T_BURST = 260f;          // 星点迸发
    private static final float T_BURST_END = 900f;
    private static final float T_PLANE = 380f;          // 飞机浮现
    private static final float T_PLANE_END = 1020f;
    private static final float T_SWEEP = 1120f;         // 高光扫过机身
    private static final float T_SWEEP_END = 1640f;
    private static final float T_TRAVEL = 1720f;        // 飞向页头
    private static final float T_TRAVEL_END = 2120f;
    private static final float T_FLIP_OUT = 2120f;      // 翻转：飞机压扁
    private static final float T_FLIP_MID = 2230f;
    private static final float T_FLIP_IN = 2230f;       // 翻转：中文展开
    private static final float T_FLIP_IN_END = 2360f;
    private static final float T_SUB = 2160f;           // 页头副标题淡入
    private static final float T_SUB_END = 2480f;
    private static final float T_CROSS = 2360f;         // 与真实标题交叉淡入
    private static final float T_CROSS_END = 2500f;
    private static final float T_END = 2520f;

    /**
     * 加载幕的时间点。到 {@link #T_PRELUDE} 时飞机已经完整停在画面中央、英文已消失、
     * 黑场已褪完，主时间轴从 {@link #T_TRAVEL} 接着走，所以不会再把飞机淡入一次。
     */
    private static final float T_SCAN_IN = 180f;
    private static final float T_SCAN_END = 1560f;
    private static final float T_HOLD_END = 1960f;
    private static final float T_BG_IN = 1960f;
    private static final float T_BG_END = 2420f;
    private static final float T_WORD_OUT = 2420f;
    private static final float T_WORD_OUT_END = 2860f;
    private static final float T_PRELUDE = 3020f;

    private static final String EN_NAME = "AIRCRAFT WAR";

    private static final int ACCENT = 0xFF3EC8FF;

    private final Paint coverPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint planeWhitePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint loadTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();
    private final Paint.FontMetrics loadMetrics = new Paint.FontMetrics();

    private final float density;

    private TextView titleView;
    private TextView subtitleView;
    private Listener listener;

    private String titleText = "";
    private float textWidth;
    private float textHeight;

    /** 机身剪影（蓝色渐变填充）与它的模糊版（光晕） */
    private Bitmap planeBitmap;
    private Bitmap planeGlow;

    /** 主角态的机身缩放、页头态的机身缩放 */
    private float heroPlaneScale = 3.5f;
    private float headerPlaneScale = 1.9f;

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

        // 翻面用的「白色版」机身：把剪影整体染白，用来画扫光
        planeWhitePaint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

        beamPaint.setStyle(Paint.Style.STROKE);
        beamPaint.setStrokeCap(Paint.Cap.SQUARE);
        beamGlowPaint.setStyle(Paint.Style.STROKE);
        beamGlowPaint.setStrokeCap(Paint.Cap.SQUARE);
        beamGlowPaint.setMaskFilter(new BlurMaskFilter(7f * density, BlurMaskFilter.Blur.NORMAL));

        loadTextPaint.setColor(Color.WHITE);
        loadTextPaint.setFakeBoldText(true);
        loadTextPaint.setTextSize(15f * density);
        loadTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        for (int i = 0; i < DOT_COUNT; i++) {
            // 黄金角散开，比纯随机更均匀，不会扎堆
            dotAngle[i] = (float) (i * 2.39996) % 6.283f;
            dotDistance[i] = (0.55f + 0.45f * (i % 5) / 4f) * 150f * density;
            dotRadius[i] = (1.0f + 0.9f * (i % 3) / 2f) * density;
        }
    }

    /** 传入布局里真实的标题/副标题，用于计算落点与最后交接 */
    public void setTargets(TextView title, TextView subtitle) {
        this.titleView = title;
        this.subtitleView = subtitle;
        if (title == null) {
            return;
        }
        titleText = title.getText().toString();
        titlePaint.setTextSize(title.getTextSize());
        titlePaint.setTypeface(title.getTypeface());
        titlePaint.setFakeBoldText(true);
        titlePaint.getFontMetrics(fontMetrics);
        textWidth = titlePaint.measureText(titleText);
        textHeight = fontMetrics.descent - fontMetrics.ascent;

        // 纵向渐变：上白下青，和 GradientTextView 保持一致，交接时才看不出接缝
        titlePaint.setShader(new LinearGradient(0f, -title.getTextSize() * 0.85f,
                0f, title.getTextSize() * 0.30f,
                0xFFFFFFFF, 0xFF78C6F0, Shader.TileMode.CLAMP));

        buildPlane();
    }

    /**
     * 用游戏里的机体裁剪出蓝色剪影：
     * 先铺一层白→青渐变，再把裁好的机体透明度当作遮罩贴上去（DST_IN），
     * 留下的就是「同色填充」的飞机。光晕则是同一张剪影在软件画布上模糊一次。
     * <p>
     * 两个必须做的清理：
     * <ul>
     *   <li><b>阈值</b>：原图顶部有一小段半透明的尾焰，直接用时剪影顶上会多出一根毛刺，
     *       放大三倍后非常明显；</li>
     *   <li><b>裁边</b>：把四周全透明的区域裁掉，机身才真正居中，
     *       否则光晕和机身的圆心对不上，光会偏。</li>
     * </ul>
     */
    private void buildPlane() {
        if (planeBitmap != null) {
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap raw = BitmapFactory.decodeResource(getResources(), R.drawable.hero, options);
        if (raw == null) {
            return;
        }

        int rawW = raw.getWidth();
        int rawH = raw.getHeight();
        int[] pixels = new int[rawW * rawH];
        raw.getPixels(pixels, 0, rawW, 0, 0, rawW, rawH);
        raw.recycle();

        int minX = rawW;
        int minY = rawH;
        int maxX = -1;
        int maxY = -1;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) < 150) {
                pixels[i] = 0;
                continue;
            }
            int x = i % rawW;
            int y = i / rawW;
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }
        if (maxX < 0) {
            return;
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int[] cropped = new int[w * h];
        for (int y = 0; y < h; y++) {
            System.arraycopy(pixels, (minY + y) * rawW + minX, cropped, y * w, w);
        }
        Bitmap mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mask.setPixels(cropped, 0, w, 0, 0, w, h);

        planeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(planeBitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0f, 0f, 0f, h,
                0xFFFFFFFF, 0xFF5FC8F5, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, w, h, fill);

        Paint matte = new Paint(Paint.ANTI_ALIAS_FLAG);
        matte.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(mask, 0f, 0f, matte);
        matte.setXfermode(null);

        // 光晕：同一张剪影模糊一次，四周留 20px 给扩散，半径别太大，
        // 太大就会糊成一团光斑，机身轮廓反而看不清
        int pad = 20;
        planeGlow = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas glowCanvas = new Canvas(planeGlow);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setMaskFilter(new BlurMaskFilter(5f * density, BlurMaskFilter.Blur.NORMAL));
        glowCanvas.drawBitmap(planeBitmap, pad, pad, glow);

        mask.recycle();
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
        releaseBitmaps();
        super.onDetachedFromWindow();
    }

    private void releaseBitmaps() {
        if (planeBitmap != null && !planeBitmap.isRecycled()) {
            planeBitmap.recycle();
        }
        planeBitmap = null;
        if (planeGlow != null && !planeGlow.isRecycled()) {
            planeGlow.recycle();
        }
        planeGlow = null;
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
        if (freezeMs < 0f && mainMs() >= T_END) {
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

    /**
     * 衔接幕用的时间。加载幕还没结束时返回 -1，结束后从「飞机已经就位、正要起飞」接着走。
     */
    private float mainMs() {
        float t = timeMs();
        if (t < T_PRELUDE) {
            return -1f;
        }
        return T_TRAVEL + (t - T_PRELUDE);
    }

    // ---------------- 几何 ----------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        heroX = w / 2f;
        heroY = h * 0.47f;
        // 主角态的机身宽度控制在屏宽 30% 左右：原图只有 100px，再放就要糊边
        if (planeBitmap != null && planeBitmap.getWidth() > 0) {
            heroPlaneScale = 0.30f * w / planeBitmap.getWidth();
            if (textHeight > 0f) {
                // 页头态：机身高度略高于标题字高，翻转时两边体量接近
                headerPlaneScale = textHeight * 1.25f / planeBitmap.getHeight();
            }
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

    // ---------------- 绘制 ----------------

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
            finalCy = h * 0.19f;
        }

        float main = mainMs();
        if (main >= 0f) {
            drawCover(canvas, main, w, h);
            drawScanLine(canvas, main);
            drawBurst(canvas, main);
            drawHeroSequence(canvas, main);
            syncMenuText(main);
        }
        if (t < T_PRELUDE) {
            drawPrelude(canvas, t, w, h);
        }
    }

    // ---------------- 加载幕：黑屏，射线扫出飞机和英文 ----------------

    private void drawPrelude(Canvas canvas, float t, float w, float h) {
        float reveal = easeInOutCubic(clamp01((t - T_SCAN_IN) / (T_SCAN_END - T_SCAN_IN)));
        float merge = easeOutCubic(clamp01((t - T_SCAN_END) / (T_HOLD_END - T_SCAN_END)));
        float bg = easeInOutCubic(clamp01((t - T_BG_IN) / (T_BG_END - T_BG_IN)));
        float wordFade = 1f - easeOutCubic(clamp01((t - T_WORD_OUT) / (T_WORD_OUT_END - T_WORD_OUT)));

        if (bg < 1f) {
            coverPaint.setColor(Color.BLACK);
            coverPaint.setAlpha((int) (255 * (1f - bg)));
            canvas.drawRect(0f, 0f, w, h, coverPaint);
        }

        float bob = preludeBob(t);
        float cy = heroY + bob;
        drawLoadPlane(canvas, reveal, merge, cy);
        if (wordFade > 0f) {
            drawLoadWord(canvas, t, reveal, merge, wordFade, cy);
        }
        drawLoadFrame(canvas, t, reveal, 1f - bg, cy);
    }

    /** 起飞前的轻微浮动，最后一段收到衔接幕第一帧的位置，避免交接时跳一下 */
    private float preludeBob(float t) {
        float idle = (float) Math.sin(t * 0.0032) * 4f * density;
        float handoff = (float) Math.sin(T_TRAVEL * 0.0032) * 5f * density;
        float blend = clamp01((t - (T_PRELUDE - 280f)) / 280f);
        return idle * (1f - blend) + handoff * blend;
    }

    private void drawLoadPlane(Canvas canvas, float reveal, float merge, float cy) {
        if (planeBitmap == null || reveal <= 0f) {
            return;
        }
        float scale = heroPlaneScale;
        float pw = planeBitmap.getWidth();
        float ph = planeBitmap.getHeight();
        float shownTop = ph / 2f - ph * reveal;

        canvas.save();
        canvas.translate(heroX, cy);
        canvas.scale(scale, scale);
        canvas.clipRect(-pw / 2f - 8f, shownTop, pw / 2f + 8f, ph / 2f + 8f);

        if (planeGlow != null && merge > 0.35f) {
            glowPaint.setAlpha((int) (200 * merge));
            canvas.drawBitmap(planeGlow,
                    -planeGlow.getWidth() / 2f, -planeGlow.getHeight() / 2f, glowPaint);
        }

        planePaint.setAlpha(255);
        if (merge >= 0.98f) {
            canvas.drawBitmap(planeBitmap, -pw / 2f, -ph / 2f, planePaint);
        } else {
            float gap = 5.5f / Math.max(scale, 0.01f);
            float band = gap * (0.38f + 0.62f * merge);
            float top = -ph / 2f;
            for (float y = top; y < ph / 2f; y += gap) {
                canvas.save();
                canvas.clipRect(-pw, y, pw, y + band);
                canvas.drawBitmap(planeBitmap, -pw / 2f, -ph / 2f, planePaint);
                canvas.restore();
            }
        }
        canvas.restore();

        if (reveal < 1f) {
            float rayY = cy + scale * shownTop;
            float half = scale * pw * 0.78f;
            drawBeam(canvas, heroX - half, rayY, heroX + half, rayY, 220);
            drawWingTicks(canvas, heroX, cy, scale, pw, ph, reveal);
        }
    }

    /** 机翼两侧甩出的短射线，扫描经过时亮一下 */
    private void drawWingTicks(Canvas canvas, float cx, float cy, float scale,
                               float pw, float ph, float reveal) {
        float y = cy + scale * (ph / 2f - ph * reveal);
        float reach = 18f * density * (float) Math.sin(Math.PI * reveal);
        float wing = scale * pw * 0.46f;
        beamPaint.setColor(Color.WHITE);
        beamPaint.setAlpha(180);
        beamPaint.setStrokeWidth(1.1f * density);
        canvas.drawLine(cx - wing, y, cx - wing - reach, y - reach * 0.65f, beamPaint);
        canvas.drawLine(cx + wing, y, cx + wing + reach, y - reach * 0.65f, beamPaint);
    }

    private void drawLoadWord(Canvas canvas, float t, float reveal, float merge,
                              float wordFade, float planeCy) {
        if (planeBitmap == null) {
            return;
        }
        float tracking = 4.2f * density;
        float wordW = measureTracked(EN_NAME, tracking, loadTextPaint);
        float maxW = getWidth() * 0.86f;
        if (wordW > maxW && wordW > 0f) {
            tracking *= maxW / wordW;
            wordW = maxW;
        }

        loadTextPaint.getFontMetrics(loadMetrics);
        float textH = loadMetrics.descent - loadMetrics.ascent;
        float baseline = planeCy + planeBitmap.getHeight() * heroPlaneScale * 0.5f + 36f * density;
        float top = baseline + loadMetrics.ascent;
        float left = heroX - wordW / 2f;
        float edge = left + wordW * reveal;

        drawConstructionRules(canvas, t, left, top, wordW, textH, merge, wordFade);

        int alpha = (int) (255 * wordFade);
        if (alpha <= 0 || reveal <= 0f) {
            return;
        }
        loadTextPaint.setAlpha(alpha);

        canvas.save();
        canvas.clipRect(left - 4f, top - 6f, Math.max(edge, left), baseline + loadMetrics.descent + 6f);
        if (merge < 0.98f) {
            float gap = 3.4f * density;
            float band = gap * (0.42f + 0.58f * merge);
            for (float y = top - 2f; y < baseline + 4f; y += gap) {
                canvas.save();
                canvas.clipRect(left - 4f, y, edge + 4f, y + band);
                drawTracked(canvas, EN_NAME, heroX, baseline, tracking, loadTextPaint);
                canvas.restore();
            }
        } else {
            drawTracked(canvas, EN_NAME, heroX, baseline, tracking, loadTextPaint);
        }
        canvas.restore();

        if (reveal < 1f) {
            float beamTop = top - 10f * density;
            float beamBot = baseline + 8f * density;
            drawBeam(canvas, edge, beamTop, edge, beamBot, (int) (230 * wordFade));
            drawComposingRays(canvas, edge, top, textH, reveal);
        }

        float ruleY = baseline + 10f * density;
        beamPaint.setColor(ACCENT);
        beamPaint.setAlpha((int) (200 * wordFade * Math.min(1f, reveal * 1.4f)));
        beamPaint.setStrokeWidth(1.4f * density);
        canvas.drawLine(left, ruleY, left + wordW * reveal, ruleY, beamPaint);
    }

    /** 字形上下两条定位线，先拉开，合成后又收回 */
    private void drawConstructionRules(Canvas canvas, float t, float left, float top,
                                       float wordW, float textH, float merge, float wordFade) {
        float open = easeOutCubic(clamp01((t - 40f) / 420f));
        float retract = 1f - merge * 0.85f;
        float span = wordW * 0.5f * open * Math.max(retract, 0.15f);
        if (span <= 1f || wordFade <= 0f) {
            return;
        }
        float y0 = top - 8f * density;
        float y1 = top + textH + 8f * density;
        beamPaint.setColor(ACCENT);
        beamPaint.setAlpha((int) (90 * wordFade * open));
        beamPaint.setStrokeWidth(1f * density);
        canvas.drawLine(heroX - span, y0, heroX + span, y0, beamPaint);
        canvas.drawLine(heroX - span, y1, heroX + span, y1, beamPaint);
    }

    /** 扫描光经过时，从光边上甩出几道短射线，像在把字母刻出来 */
    private void drawComposingRays(Canvas canvas, float edge, float top, float textH, float reveal) {
        beamPaint.setColor(Color.WHITE);
        beamPaint.setStrokeWidth(1f * density);
        for (int i = 0; i < 5; i++) {
            float phase = (reveal * 3.2f + i * 0.37f) % 1f;
            float len = (10f + 16f * phase) * density;
            float y = top + textH * (0.12f + 0.18f * i);
            int alpha = (int) (160 * (1f - phase));
            beamPaint.setAlpha(alpha);
            float dir = (i % 2 == 0) ? 1f : -1f;
            canvas.drawLine(edge, y, edge + dir * len, y - len * 0.35f, beamPaint);
        }
    }

    /** 飞机四周的四角框，黑场还在时收拢，背景亮起后散掉 */
    private void drawLoadFrame(Canvas canvas, float t, float reveal, float black, float cy) {
        if (planeBitmap == null || black <= 0.02f || reveal <= 0f) {
            return;
        }
        float grow = easeOutCubic(clamp01((t - 80f) / 480f));
        float pw = planeBitmap.getWidth() * heroPlaneScale;
        float ph = planeBitmap.getHeight() * heroPlaneScale;
        float halfW = pw * 0.62f + 18f * density;
        float halfH = ph * 0.62f + 14f * density;
        float len = 14f * density * grow * black;
        if (len <= 0.5f) {
            return;
        }
        beamPaint.setColor(Color.WHITE);
        beamPaint.setAlpha((int) (150 * black));
        beamPaint.setStrokeWidth(1.3f * density);
        float l = heroX - halfW;
        float r = heroX + halfW;
        float top = cy - halfH;
        float bot = cy + halfH;
        drawCorner(canvas, l, top, len, len);
        drawCorner(canvas, r, top, -len, len);
        drawCorner(canvas, l, bot, len, -len);
        drawCorner(canvas, r, bot, -len, -len);
    }

    private void drawCorner(Canvas canvas, float x, float y, float dx, float dy) {
        canvas.drawLine(x, y, x + dx, y, beamPaint);
        canvas.drawLine(x, y, x, y + dy, beamPaint);
    }

    private void drawBeam(Canvas canvas, float x0, float y0, float x1, float y1, int alpha) {
        beamGlowPaint.setColor(ACCENT);
        beamGlowPaint.setAlpha((int) (alpha * 0.55f));
        beamGlowPaint.setStrokeWidth(5f * density);
        canvas.drawLine(x0, y0, x1, y1, beamGlowPaint);

        beamPaint.setColor(Color.WHITE);
        beamPaint.setAlpha(alpha);
        beamPaint.setStrokeWidth(1.3f * density);
        canvas.drawLine(x0, y0, x1, y1, beamPaint);
    }

    private float measureTracked(String text, float tracking, Paint paint) {
        int n = text.length();
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += paint.measureText(text, i, i + 1);
        }
        if (n > 1) {
            total += tracking * (n - 1);
        }
        return total;
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

    // ---------------- ④⑤⑥ 飞机 → 翻转成中文 ----------------

    private void drawHeroSequence(Canvas canvas, float t) {
        float travel = smoothstep(clamp01((t - T_TRAVEL) / (T_TRAVEL_END - T_TRAVEL)));

        float cx = heroX + (finalCx - heroX) * travel;
        float cy = heroY + (finalCy - heroY) * travel;

        if (t >= T_FLIP_MID) {
            drawTitleFlippedIn(canvas, t);
            return;
        }

        float enter = easeOutCubic(clamp01((t - T_PLANE) / (T_PLANE_END - T_PLANE)));
        if (enter <= 0f) {
            return;
        }

        // 入场时略微放大 + 轻微倾斜，"落位"之后再回正
        float scale = (heroPlaneScale * (0.74f + 0.26f * enter)
                + (headerPlaneScale - heroPlaneScale) * travel);

        // 翻转前半程：横向压扁到 0
        float flip = 1f;
        if (t >= T_FLIP_OUT) {
            float q = clamp01((t - T_FLIP_OUT) / (T_FLIP_MID - T_FLIP_OUT));
            flip = Math.max(0f, (float) Math.cos(q * Math.PI / 2f));
        }

        float bob = (float) Math.sin(t * 0.0032) * 5f * density * (1f - travel);

        canvas.save();
        canvas.translate(cx, cy + bob);
        canvas.rotate(-7f * (1f - enter));
        canvas.scale(scale * Math.max(flip, 0.001f), scale);

        if (planeGlow != null) {
            glowPaint.setAlpha((int) (200 * enter * (1f - travel * 0.55f)));
            canvas.drawBitmap(planeGlow,
                    -planeGlow.getWidth() / 2f, -planeGlow.getHeight() / 2f, glowPaint);
        }
        if (planeBitmap != null) {
            planePaint.setAlpha((int) (255 * enter));
            canvas.drawBitmap(planeBitmap,
                    -planeBitmap.getWidth() / 2f, -planeBitmap.getHeight() / 2f, planePaint);
            drawPlaneSweep(canvas, t);
        }
        canvas.restore();
    }

    /**
     * 高光：把机身再画一次（这次染白），只露出斜带内的部分。
     * 裁切区域建立后就固定在设备坐标系里，所以转回来画的机身依然被斜带裁住，
     * 不用 {@code saveLayer} 就省掉一次离屏渲染。
     */
    private void drawPlaneSweep(Canvas canvas, float t) {
        float p = clamp01((t - T_SWEEP) / (T_SWEEP_END - T_SWEEP));
        if (p <= 0f || p >= 1f || planeBitmap == null) {
            return;
        }
        float w = planeBitmap.getWidth();
        float h = planeBitmap.getHeight();
        float band = w * 0.30f;
        float x = -w * 1.1f + (w * 2.2f) * easeInOutCubic(p);

        canvas.save();
        canvas.rotate(-18f);
        canvas.clipRect(x, -w, x + band, w);
        canvas.rotate(18f);
        planeWhitePaint.setAlpha((int) (170 * Math.sin(Math.PI * p)));
        canvas.drawBitmap(planeBitmap, -w / 2f, -h / 2f, planeWhitePaint);
        canvas.restore();
    }

    /**
     * 逐字居中绘制并追加字距。
     * 不用 {@code Paint.setLetterSpacing}：那个属性在软件画布上的表现不稳定，
     * 手算每个字的位置最可控，而且字距可以直接当动画参数用。
     */
    private void drawTracked(Canvas canvas, String text, float centerX, float baseline,
                             float tracking, Paint paint) {
        int n = text.length();
        float total = 0f;
        for (int i = 0; i < n; i++) {
            total += paint.measureText(text, i, i + 1);
        }
        total += tracking * (n - 1);

        float x = centerX - total / 2f;
        for (int i = 0; i < n; i++) {
            canvas.drawText(text, i, i + 1, x, baseline, paint);
            x += paint.measureText(text, i, i + 1) + tracking;
        }
    }

    /** 翻转后半程：中文标题从 0 宽展开到满宽，落在页头真实位置上 */
    private void drawTitleFlippedIn(Canvas canvas, float t) {
        float cross = clamp01((t - T_CROSS) / (T_CROSS_END - T_CROSS));
        float blockAlpha = 1f - cross;
        if (blockAlpha <= 0f) {
            return;
        }
        float q = clamp01((t - T_FLIP_IN) / (T_FLIP_IN_END - T_FLIP_IN));
        float open = (float) Math.sin(q * Math.PI / 2f);

        float baseline = -(fontMetrics.ascent + fontMetrics.descent) / 2f;

        canvas.save();
        canvas.translate(finalCx, finalCy);
        canvas.scale(Math.max(open, 0.001f), 1f);
        titlePaint.setAlpha((int) (255 * blockAlpha));
        canvas.drawText(titleText, -textWidth / 2f, baseline, titlePaint);
        canvas.restore();
        titlePaint.setAlpha(255);
    }

    /** 把下面真实控件的透明度跟上动画节奏，实现无缝交接 */
    private void syncMenuText(float t) {
        if (titleView != null) {
            setAlphaQuietly(titleView, clamp01((t - T_CROSS) / (T_CROSS_END - T_CROSS)));
        }
        if (subtitleView != null) {
            setAlphaQuietly(subtitleView, easeOutCubic(clamp01((t - T_SUB) / (T_SUB_END - T_SUB))));
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
