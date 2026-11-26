package edu.hitsz.aircraftwar.ui.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import edu.hitsz.aircraftwar.R;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;

/**
 * 二级面板：「开始游戏 → 模式 / 难度」与「设置」共用同一套壳子。
 * <p>
 * 为什么不用 {@code Dialog}：一是对话框的圆角、阴影、进场曲线都由系统决定，
 * 和这套自绘菜单的观感对不上；二是这里要的「面板从 92% 放大到 100%、背景同时压暗」
 * 用属性动画框架反而更难对齐。整块用 Canvas 画，反而代码更短、行为更确定。
 * <p>
 * 结构上只有一件事要小心：<b>命中区域和绘制必须用同一套矩形</b>。
 * 所以 {@link #computeLayout()} 先把面板和每个条目的 {@link RectF} 算好存起来，
 * 绘制和 {@code onTouchEvent} 都读它 —— 不会出现「画在这里、点在那里」的老问题。
 */
public class MenuOverlayView extends View implements Choreographer.FrameCallback {

    public enum Mode {
        /** 开始游戏：模式选择 */
        START,
        /** 开始游戏：难度选择 */
        DIFFICULTY,
        /** 设置 */
        SETTINGS
    }

    public interface Listener {
        void onStartGame(Difficulty difficulty);

        void onDismiss();
    }

    private static final int ACCENT = 0xFF3EC8FF;
    private static final int TEXT = 0xFFE9EEF7;
    private static final int TEXT_MUTED = 0xFF8FA0B8;
    private static final int BORDER = 0xFF2A3442;
    private static final int PANEL = 0xFF101822;
    private static final int CARD = 0xFF1A2431;
    private static final int SUCCESS = 0xFF46D39A;
    private static final int DANGER = 0xFFFF5C6C;

    private static final float DIM_MAX = 0.62f;

    private final float density;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint.FontMetrics fontMetrics = new Paint.FontMetrics();

    private final RectF panelRect = new RectF();
    private final RectF headerRect = new RectF();
    private final RectF closeRect = new RectF();
    private final RectF backRect = new RectF();
    private final RectF[] itemRects = {new RectF(), new RectF(), new RectF()};
    private int itemCount;

    private LocalStore store;
    private Listener listener;
    private Mode mode = Mode.START;

    private Bitmap heroBitmap;
    private Bitmap bossBitmap;

    private float progress;
    private long lastFrameNanos;
    private boolean running;
    private boolean dismissing;
    private boolean finished;
    private long shakeStartNanos;
    private Difficulty pendingStart;

    public MenuOverlayView(Context context) {
        this(context, null);
    }

    public MenuOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        loadBitmaps();
    }

    private void loadBitmaps() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        heroBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hero, options);
        bossBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.boss, options);
    }

    public void bind(LocalStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    /** 打开面板；{@code mode} 决定首屏内容 */
    public void open(Mode mode) {
        this.mode = mode;
        progress = 0f;
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    public boolean isShowing() {
        return !dismissing && !finished;
    }

    public Mode getMode() {
        return mode;
    }

    /** 返回上一层：难度页退回模式页，模式页/设置页直接关掉 */
    public void goBack() {
        if (mode == Mode.DIFFICULTY) {
            mode = Mode.START;
            invalidate();
        } else {
            dismiss();
        }
    }

    public void dismiss() {
        if (dismissing || finished) {
            return;
        }
        dismissing = true;
        if (!running) {
            running = true;
            lastFrameNanos = 0L;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    // ---------------- 帧循环 ----------------

    @Override
    public void doFrame(long nanos) {
        if (!running) {
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nanos;
        }
        float dt = Math.min((nanos - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = nanos;

        float speed = 1f / 0.24f;
        if (dismissing) {
            progress -= dt * speed * 1.5f;
            if (progress <= 0f) {
                progress = 0f;
                finish();
                return;
            }
        } else {
            progress = Math.min(1f, progress + dt * speed);
        }
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
        if (listener == null) {
            return;
        }
        // 选了难度就先等面板收完再开打，直接切走会让退场动画看不见
        if (pendingStart != null) {
            Difficulty target = pendingStart;
            pendingStart = null;
            listener.onStartGame(target);
        } else {
            listener.onDismiss();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    // ---------------- 布局：绘制与命中共用 ----------------

    private void computeLayout() {
        float w = getWidth();
        float h = getHeight();
        float panelW = Math.min(w * 0.88f, 400f * density);

        float headerH = 52f * density;
        float contentH;
        switch (mode) {
            case DIFFICULTY:
                contentH = 3 * 64f * density + 2 * 10f * density;
                break;
            case SETTINGS:
                contentH = 2 * 68f * density + 10f * density;
                break;
            default:
                contentH = 2 * 92f * density + 12f * density;
                break;
        }
        float footnoteH = 42f * density;
        float padV = 22f * density;
        float panelH = padV * 2f + headerH + contentH + footnoteH;

        float left = (w - panelW) / 2f;
        float top = (h - panelH) / 2f;
        panelRect.set(left, top, left + panelW, top + panelH);
        headerRect.set(left, top + padV, left + panelW, top + padV + headerH);
        closeRect.set(panelRect.right - 56f * density, top + padV,
                panelRect.right - 12f * density, top + padV + headerH);
        // 返回箭头的命中区固定和绘制位置对齐（左上角，箭头本身画在 headerRect.left+26dp 处）
        backRect.set(left + 6f * density, top + padV,
                left + 48f * density, top + padV + headerH);

        float cursor = top + padV + headerH;
        float inner = 18f * density;
        itemCount = 0;
        if (mode == Mode.START) {
            for (int i = 0; i < 2; i++) {
                itemRects[i].set(left + inner, cursor, panelRect.right - inner, cursor + 92f * density);
                cursor += (92f + 12f) * density;
                itemCount++;
            }
        } else if (mode == Mode.DIFFICULTY) {
            for (int i = 0; i < 3; i++) {
                itemRects[i].set(left + inner, cursor, panelRect.right - inner, cursor + 64f * density);
                cursor += 74f * density;
                itemCount++;
            }
        } else {
            for (int i = 0; i < 2; i++) {
                itemRects[i].set(left + inner, cursor, panelRect.right - inner, cursor + 68f * density);
                cursor += 78f * density;
                itemCount++;
            }
        }
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        if (progress <= 0f) {
            return;
        }
        computeLayout();
        float ease = easeOutCubic(progress);

        // 背景压暗：面板打开时让下面的星空退到后面去
        fillPaint.setColor(0xFF000000);
        fillPaint.setAlpha((int) (255 * DIM_MAX * ease));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), fillPaint);

        // 面板：92% → 100% 放大 + 淡入
        float scale = 0.92f + 0.08f * ease;
        canvas.save();
        canvas.scale(scale, scale, panelRect.centerX(), panelRect.centerY());

        fillPaint.setColor(PANEL);
        fillPaint.setAlpha((int) (255 * ease));
        canvas.drawRoundRect(panelRect, 22f * density, 22f * density, fillPaint);
        strokePaint.setColor(BORDER);
        strokePaint.setAlpha((int) (255 * ease));
        strokePaint.setStrokeWidth(1f * density);
        canvas.drawRoundRect(panelRect, 22f * density, 22f * density, strokePaint);
        // 顶部一道细高光，暗示面板是被「推」出来的
        strokePaint.setColor(ACCENT);
        strokePaint.setAlpha((int) (90 * ease));
        canvas.drawLine(panelRect.left + 28f * density, panelRect.top + 1f * density,
                panelRect.right - 28f * density, panelRect.top + 1f * density, strokePaint);

        int alpha = (int) (255 * ease);
        drawHeader(canvas, alpha);
        switch (mode) {
            case START:
                drawStartContent(canvas, alpha, ease);
                break;
            case DIFFICULTY:
                drawDifficultyContent(canvas, alpha);
                break;
            default:
                drawSettingsContent(canvas, alpha);
                break;
        }
        drawFootnote(canvas, alpha);
        canvas.restore();
    }

    private void drawHeader(Canvas canvas, int alpha) {
        String title;
        switch (mode) {
            case DIFFICULTY:
                title = "选择难度";
                break;
            case SETTINGS:
                title = "设置";
                break;
            default:
                title = "开始游戏";
                break;
        }

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(18f * density);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(TEXT);
        textPaint.setAlpha(alpha);
        textPaint.getFontMetrics(fontMetrics);
        float baseline = headerRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        // 左对齐：标题是「读完就走」的信息，居中反而会和返回箭头抢视线
        canvas.drawText(title, headerRect.left + (mode == Mode.DIFFICULTY ? 52f : 20f) * density,
                baseline, textPaint);

        if (mode == Mode.DIFFICULTY) {
            strokePaint.setColor(TEXT);
            strokePaint.setAlpha(alpha);
            strokePaint.setStrokeWidth(2.4f * density);
            float bx = headerRect.left + 26f * density;
            float by = headerRect.centerY();
            canvas.drawLine(bx + 5f * density, by - 8f * density, bx - 4f * density, by, strokePaint);
            canvas.drawLine(bx - 4f * density, by, bx + 5f * density, by + 8f * density, strokePaint);
        }

        strokePaint.setColor(TEXT_MUTED);
        strokePaint.setAlpha(alpha);
        strokePaint.setStrokeWidth(2f * density);
        float cx = closeRect.centerX();
        float cy = closeRect.centerY();
        canvas.drawLine(cx - 7f * density, cy - 7f * density, cx + 7f * density, cy + 7f * density, strokePaint);
        canvas.drawLine(cx + 7f * density, cy - 7f * density, cx - 7f * density, cy + 7f * density, strokePaint);
    }

    private void drawStartContent(Canvas canvas, int alpha, float ease) {
        drawModeCard(canvas, 0, "单机模式", "三档难度 · 本地最高分", heroBitmap, ACCENT, alpha, ease, false);
        drawModeCard(canvas, 1, "联机对战", "双人房间 · 实时比分", bossBitmap, TEXT_MUTED, alpha, ease, true);
    }

    private void drawModeCard(Canvas canvas, int index, String title, String subtitle,
                              Bitmap icon, int tint, int alpha, float ease, boolean locked) {
        RectF r = new RectF(itemRects[index]);
        if (locked && shakeStartNanos > 0L) {
            float t = (System.nanoTime() - shakeStartNanos) / 1e9f;
            if (t < 0.4f) {
                r.offset((float) Math.sin(t * 42f) * (1f - t / 0.4f) * 9f * density, 0f);
            } else {
                shakeStartNanos = 0L;
            }
        }

        int contentAlpha = locked ? (int) (alpha * 0.55f) : alpha;
        fillPaint.setColor(locked ? 0xFF141B24 : CARD);
        fillPaint.setAlpha(contentAlpha);
        canvas.drawRoundRect(r, 16f * density, 16f * density, fillPaint);
        strokePaint.setColor(locked ? BORDER : (ACCENT & 0x00FFFFFF) | 0x66000000);
        strokePaint.setAlpha(contentAlpha);
        strokePaint.setStrokeWidth(1f * density);
        canvas.drawRoundRect(r, 16f * density, 16f * density, strokePaint);

        // 左侧图标：机体/敌机贴图放进一个圆角小方块里，比纯文字更有游戏感
        float tileSize = 56f * density;
        RectF tile = new RectF(r.left + 16f * density, r.centerY() - tileSize / 2f,
                r.left + 16f * density + tileSize, r.centerY() + tileSize / 2f);
        fillPaint.setColor(0x33FFFFFF);
        fillPaint.setAlpha((int) (contentAlpha * 0.16f));
        canvas.drawRoundRect(tile, 14f * density, 14f * density, fillPaint);
        if (icon != null) {
            float pad = 8f * density;
            RectF dst = new RectF(tile.left + pad, tile.top + pad, tile.right - pad, tile.bottom - pad);
            // 按比例内接，别把敌机拉变形
            float scale = Math.min(dst.width() / icon.getWidth(), dst.height() / icon.getHeight());
            float dw = icon.getWidth() * scale;
            float dh = icon.getHeight() * scale;
            RectF fitted = new RectF(tile.centerX() - dw / 2f, tile.centerY() - dh / 2f,
                    tile.centerX() + dw / 2f, tile.centerY() + dh / 2f);
            iconPaint.setAlpha(contentAlpha);
            canvas.drawBitmap(icon, null, fitted, iconPaint);
        }

        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(17f * density);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(locked ? TEXT_MUTED : TEXT);
        textPaint.setAlpha(contentAlpha);
        float textX = tile.right + 16f * density;
        canvas.drawText(title, textX, r.centerY() - 4f * density, textPaint);

        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(12f * density);
        textPaint.setColor(TEXT_MUTED);
        textPaint.setAlpha(contentAlpha);
        canvas.drawText(subtitle, textX, r.centerY() + 16f * density, textPaint);

        if (locked) {
            // 「开发中」小标签
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(10f * density);
            float tw = textPaint.measureText("开发中");
            RectF badge = new RectF(r.right - tw - 34f * density, r.centerY() - 11f * density,
                    r.right - 14f * density, r.centerY() + 11f * density);
            fillPaint.setColor(0xFFFFB020);
            fillPaint.setAlpha((int) (contentAlpha * 0.16f));
            canvas.drawRoundRect(badge, 11f * density, 11f * density, fillPaint);
            textPaint.setColor(0xFFFFB020);
            textPaint.setAlpha(contentAlpha);
            drawTextCentered(canvas, "开发中", badge.centerX(), badge.centerY(), textPaint);
        } else {
            // 右箭头
            strokePaint.setColor(tint);
            strokePaint.setAlpha((int) (contentAlpha * 0.8f));
            strokePaint.setStrokeWidth(2f * density);
            float cx = r.right - 26f * density;
            float cy = r.centerY();
            canvas.drawLine(cx - 4f * density, cy - 7f * density, cx + 4f * density, cy, strokePaint);
            canvas.drawLine(cx + 4f * density, cy, cx - 4f * density, cy + 7f * density, strokePaint);
        }
    }

    private void drawDifficultyContent(Canvas canvas, int alpha) {
        Difficulty[] values = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        String[] names = {"简单", "普通", "困难"};
        String[] latin = {"EASY", "NORMAL", "HARD"};
        int[] colors = {SUCCESS, ACCENT, DANGER};

        for (int i = 0; i < values.length; i++) {
            RectF r = itemRects[i];
            fillPaint.setColor(CARD);
            fillPaint.setAlpha(alpha);
            canvas.drawRoundRect(r, 14f * density, 14f * density, fillPaint);
            strokePaint.setColor(BORDER);
            strokePaint.setAlpha(alpha);
            strokePaint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(r, 14f * density, 14f * density, strokePaint);

            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(16f * density);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(colors[i]);
            textPaint.setAlpha(alpha);
            canvas.drawText(names[i], r.left + 18f * density, r.centerY() + 5f * density, textPaint);

            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(10f * density);
            textPaint.setColor(TEXT_MUTED);
            textPaint.setAlpha((int) (alpha * 0.8f));
            canvas.drawText(latin[i], r.left + 18f * density + 44f * density,
                    r.centerY() + 5f * density, textPaint);

            int best = store == null ? 0 : store.getBestScore(values[i]);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(12f * density);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setColor(TEXT_MUTED);
            textPaint.setAlpha(alpha);
            canvas.drawText(best > 0 ? "最高 " + best : "暂无纪录",
                    r.right - 18f * density, r.centerY() + 5f * density, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawSettingsContent(Canvas canvas, int alpha) {
        String[] names = {"背景音乐", "音效"};
        String[] notes = {"循环播放的 BGM", "击毁、拾取、爆炸"};
        boolean[] on = {
                store != null && !store.isMusicMuted(),
                store != null && !store.isSoundMuted()
        };

        for (int i = 0; i < 2; i++) {
            RectF r = itemRects[i];
            fillPaint.setColor(CARD);
            fillPaint.setAlpha(alpha);
            canvas.drawRoundRect(r, 14f * density, 14f * density, fillPaint);
            strokePaint.setColor(BORDER);
            strokePaint.setAlpha(alpha);
            strokePaint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(r, 14f * density, 14f * density, strokePaint);

            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(16f * density);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(TEXT);
            textPaint.setAlpha(alpha);
            canvas.drawText(names[i], r.left + 18f * density, r.centerY() - 2f * density, textPaint);

            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(11f * density);
            textPaint.setColor(TEXT_MUTED);
            textPaint.setAlpha((int) (alpha * 0.8f));
            canvas.drawText(notes[i], r.left + 18f * density, r.centerY() + 18f * density, textPaint);

            drawSwitch(canvas, r.right - 78f * density, r.centerY(), on[i], alpha);
        }
    }

    private void drawSwitch(Canvas canvas, float right, float centerY, boolean on, int alpha) {
        float w = 56f * density;
        float h = 30f * density;
        RectF track = new RectF(right - w, centerY - h / 2f, right, centerY + h / 2f);

        fillPaint.setColor(on ? ACCENT : 0xFF2A3442);
        fillPaint.setAlpha(on ? (int) (alpha * 0.85f) : alpha);
        canvas.drawRoundRect(track, h / 2f, h / 2f, fillPaint);

        float knobR = h / 2f - 4f * density;
        float knobX = on ? track.right - h / 2f : track.left + h / 2f;
        fillPaint.setColor(on ? 0xFFF2F8FF : 0xFF8FA0B8);
        fillPaint.setAlpha(alpha);
        canvas.drawCircle(knobX, centerY, knobR, fillPaint);
    }

    private void drawFootnote(Canvas canvas, int alpha) {
        String note;
        switch (mode) {
            case DIFFICULTY:
                note = "点难度立即开局";
                break;
            case SETTINGS:
                note = "开关立即生效并保存";
                break;
            default:
                note = "点空白处关闭";
                break;
        }
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setTextSize(11f * density);
        textPaint.setColor(TEXT_MUTED);
        textPaint.setAlpha((int) (alpha * 0.6f));
        drawTextCentered(canvas, note, panelRect.centerX(), panelRect.bottom - 26f * density, textPaint);
    }

    private void drawTextCentered(Canvas canvas, String text, float cx, float cy, Paint paint) {
        Paint.Align old = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.getFontMetrics(fontMetrics);
        canvas.drawText(text, cx, cy - (fontMetrics.ascent + fontMetrics.descent) / 2f, paint);
        paint.setTextAlign(old);
    }

    // ---------------- 触摸 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return true;
        }
        // 进场动画没走完先不响应，避免误触
        if (progress < 0.85f || dismissing) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();

        if (closeRect.contains(x, y)) {
            dismiss();
            return true;
        }
        if (mode == Mode.DIFFICULTY && backRect.contains(x, y)) {
            goBack();
            return true;
        }
        if (!panelRect.contains(x, y)) {
            dismiss();
            return true;
        }

        for (int i = 0; i < itemCount; i++) {
            if (!itemRects[i].contains(x, y)) {
                continue;
            }
            handleItemTap(i);
            return true;
        }
        return true;
    }

    private void handleItemTap(int index) {
        if (mode == Mode.START) {
            if (index == 0) {
                mode = Mode.DIFFICULTY;
                invalidate();
            } else {
                // 对战还没做：抖一下告诉用户点了但不可用，比弹 Toast 轻
                shakeStartNanos = System.nanoTime();
                invalidate();
            }
        } else if (mode == Mode.DIFFICULTY) {
            Difficulty[] values = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
            pendingStart = values[index];
            dismiss();
        } else {
            if (store == null) {
                return;
            }
            if (index == 0) {
                store.setMusicMuted(!store.isMusicMuted());
            } else {
                store.setSoundMuted(!store.isSoundMuted());
            }
            invalidate();
        }
    }

    private static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }
}
