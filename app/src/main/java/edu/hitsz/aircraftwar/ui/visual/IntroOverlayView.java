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
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import edu.hitsz.aircraftwar.R;

/**
 * 开场动画：
 * 黑场亮起 → 扫描线展开 → 星点迸发 → 蓝色飞机浮现（下方英文名）→ 高光扫过
 * → 飞机带着英文名飞向页头 → 横向翻转成「飞机大战」→ 交还菜单。
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
    private static final float T_EN = 620f;             // 英文名淡入
    private static final float T_EN_END = 1120f;
    private static final float T_SWEEP = 1120f;         // 高光扫过机身
    private static final float T_SWEEP_END = 1640f;
    private static final float T_EN_OUT = 1700f;        // 英文名随起飞淡出
    private static final float T_EN_OUT_END = 1960f;
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

    private static final String EN_NAME = "AIRCRAFT WAR";

    private static final int ACCENT = 0xFF3EC8FF;

    private final Paint coverPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint planeWhitePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint enPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

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

        enPaint.setTextSize(13f * density);
        enPaint.setColor(ACCENT);
        enPaint.setFakeBoldText(true);

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

        drawCover(canvas, t, w, h);
        drawScanLine(canvas, t);
        drawBurst(canvas, t);
        drawHeroSequence(canvas, t);
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

        drawEnglishName(canvas, t, cx, cy, scale);
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

    /** 机身下方的英文名：跟着飞机一起飞、一起缩小，起飞后淡出 */
    private void drawEnglishName(Canvas canvas, float t, float cx, float cy, float scale) {
        float pin = clamp01((t - T_EN) / (T_EN_END - T_EN));
        float pout = clamp01((t - T_EN_OUT) / (T_EN_OUT_END - T_EN_OUT));
        float alpha = easeOutCubic(pin) * (1f - pout);
        if (alpha <= 0f || planeBitmap == null) {
            return;
        }

        float baseline = cy + planeBitmap.getHeight() * scale * 0.5f + 34f * density;
        float tracking = 10f * density * (1f - easeOutCubic(pin));

        enPaint.setAlpha((int) (255 * alpha));
        drawTracked(canvas, EN_NAME, cx, baseline, tracking, enPaint);
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
