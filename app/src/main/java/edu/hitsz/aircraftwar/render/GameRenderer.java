package edu.hitsz.aircraftwar.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import edu.hitsz.aircraftwar.core.GameWorld;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.basic.AbstractFlyingObject;
import edu.hitsz.aircraftwar.core.item.Item;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.List;

/**
 * 把 {@link GameWorld} 画到 {@link Canvas} 上。
 * <p>
 * 所有绘制都在「逻辑坐标系」（默认宽 512）里完成，最后统一用 {@code canvas.scale}
 * 缩放到实际屏幕像素。这样游戏数值与屏幕分辨率彻底解耦 ——
 * Windows 版是写死 512x768，Android 上换成按屏幕长宽比计算战场高度即可。
 * <p>
 * 实体位置用 {@code renderX/renderY(alpha)} 做插值：逻辑 25Hz，渲染尽量 60fps，
 * 画面不会一格一格跳。碰撞仍由核心层用真实坐标判定。
 */
public class GameRenderer {

    // 配色沿用 Windows 版（深色底 + 青色强调），两端口径一致
    private static final int ACCENT = 0xFF3EC8FF;
    private static final int ACCENT_DEEP = 0xFF1B6FA8;
    private static final int TEXT = 0xFFE9EEF7;
    private static final int TEXT_MUTED = 0xFF8FA0B8;
    private static final int BORDER = 0xFF2A3442;
    private static final int SUCCESS = 0xFF46D39A;
    private static final int WARN = 0xFFFFB020;
    private static final int DANGER = 0xFFFF5C6C;
    private static final int PANEL_BG = 0xB4080C12;

    private final Assets assets;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    public GameRenderer(Assets assets) {
        this.assets = assets;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.2f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        fillPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * @param alpha 0~1，距上一次逻辑推进的比例，用于插值
     */
    public void draw(Canvas canvas, GameWorld world, double alpha, boolean paused) {
        canvas.drawColor(0xFF0E1218);

        float scale = canvas.getWidth() / (float) PlayField.width();
        int save = canvas.save();
        canvas.scale(scale, scale);

        drawBackground(canvas, world, alpha);

        drawEntities(canvas, world.getItems(), alpha);
        drawEntities(canvas, world.getEnemyBullets(), alpha);
        drawEntities(canvas, world.getHeroBullets(), alpha);
        drawEntities(canvas, world.getEnemies(), alpha);
        drawEntities(canvas, world.getHeroes(), alpha);

        drawHud(canvas, world);

        if (world.isGameOver()) {
            drawOverlay(canvas, "游戏结束", "得分 " + world.totalScore(), DANGER);
        } else if (paused) {
            drawOverlay(canvas, "已暂停", "点击继续", TEXT);
        }

        canvas.restoreToCount(save);
    }

    // ---------------- 背景 ----------------

    private void drawBackground(Canvas canvas, GameWorld world, double alpha) {
        Bitmap bg = assets.background(world.getDifficulty());
        if (bg == null) {
            return;
        }
        float fieldW = PlayField.width();
        float fieldH = PlayField.height();
        float scale = fieldW / bg.getWidth();
        float tileH = bg.getHeight() * scale;

        // 滚动量按插值回退一格，避免跨回绕点时背景倒跳
        double scroll = world.getBackgroundScroll() - (1.0 - alpha);
        float offset = (float) (scroll % tileH);
        if (offset < 0) {
            offset += tileH;
        }

        src.set(0, 0, bg.getWidth(), bg.getHeight());
        for (float y = offset - tileH; y < fieldH; y += tileH) {
            dst.set(0, y, fieldW, y + tileH);
            canvas.drawBitmap(bg, src, dst, bitmapPaint);
        }
    }

    // ---------------- 实体 ----------------

    private void drawEntities(Canvas canvas, List<? extends AbstractFlyingObject> objects, double alpha) {
        for (AbstractFlyingObject object : objects) {
            if (object.notValid()) {
                continue;
            }
            Bitmap bmp = assets.bitmapFor(object);
            if (bmp == null) {
                continue;
            }
            float x = object.renderX(alpha) - bmp.getWidth() / 2f;
            float y = object.renderY(alpha) - bmp.getHeight() / 2f;
            canvas.drawBitmap(bmp, x, y, bitmapPaint);
        }
    }

    // ---------------- HUD ----------------

    private void drawHud(Canvas canvas, GameWorld world) {
        drawStatusCard(canvas, world);
        drawPauseButton(canvas);
        drawBackpack(canvas, world);
        drawDifficultyChip(canvas, world);
    }

    /** 暂停按钮：与 {@link #pauseButtonBounds()} 的命中区域保持一致 */
    private void drawPauseButton(Canvas canvas) {
        RectF r = pauseButtonBounds();
        drawPanel(canvas, r.left, r.top, r.width(), r.height(), 10, PANEL_BG, BORDER);

        float cx = r.centerX();
        float cy = r.centerY();
        strokePaint.setColor(TEXT);
        strokePaint.setStrokeWidth(3f);
        canvas.drawLine(cx - 5, cy - 7, cx - 5, cy + 7, strokePaint);
        canvas.drawLine(cx + 5, cy - 7, cx + 5, cy + 7, strokePaint);
        strokePaint.setStrokeWidth(1.2f);
    }

    private void drawStatusCard(Canvas canvas, GameWorld world) {
        float x = 12, y = 12, w = 200, h = 76;
        drawPanel(canvas, x, y, w, h, 14, PANEL_BG, 0x413EC8FF);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(TEXT_MUTED);
        textPaint.setTextSize(11);
        canvas.drawText("得分", x + 14, y + 25, textPaint);

        textPaint.setColor(TEXT);
        textPaint.setTextSize(22);
        canvas.drawText(String.valueOf(world.totalScore()), x + 50, y + 28, textPaint);

        textPaint.setColor(TEXT_MUTED);
        textPaint.setTextSize(11);
        canvas.drawText("生命", x + 14, y + 62, textPaint);

        HeroAircraft hero = world.getHero(0);
        int maxHp = hero.getMaxHp();
        int hp = hero.getHp();
        float percent = maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / (float) maxHp));

        float barX = x + 50;
        float barY = y + 52;
        float barW = w - 50 - 14;
        float barH = 11;

        barPaint.setColor(0x22FFFFFF);
        canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, barH / 2, barH / 2, barPaint);

        int barColor = percent >= 0.8 ? SUCCESS : (percent >= 0.2 ? WARN : DANGER);
        barPaint.setColor(barColor);
        canvas.drawRoundRect(barX, barY, barX + barW * percent, barY + barH, barH / 2, barH / 2, barPaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(9);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hp + " / " + maxHp, barX + barW / 2, barY + barH - 2.5f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBackpack(Canvas canvas, GameWorld world) {
        float slot = 46, gap = 8, top = 12;
        float right = PlayField.width() - 12;

        for (int k = 0; k < GameWorld.BACKPACK_CAPACITY; k++) {
            float x = right - slot - k * (slot + gap);
            List<Item> items = world.getBackpack(0);
            // 栈顶（最后拾取）会先被使用 —— 它在最右格（k=0）
            int index = items.size() - 1 - k;
            boolean nextToUse = index >= 0 && index == items.size() - 1;

            drawPanel(canvas, x, top, slot, slot, 11,
                    nextToUse ? 0xA51B6FA8 : 0x96080C12,
                    nextToUse ? ACCENT : BORDER);

            if (index >= 0 && index < items.size()) {
                Bitmap bmp = assets.bitmapFor(items.get(index));
                if (bmp != null) {
                    dst.set(x + 7, top + 7, x + slot - 7, top + slot - 7);
                    src.set(0, 0, bmp.getWidth(), bmp.getHeight());
                    canvas.drawBitmap(bmp, src, dst, bitmapPaint);
                }
            }
        }
    }

    private void drawDifficultyChip(Canvas canvas, GameWorld world) {
        String name;
        int color;
        switch (world.getDifficulty()) {
            case EASY:
                name = "简单";
                color = SUCCESS;
                break;
            case HARD:
                name = "困难";
                color = DANGER;
                break;
            default:
                name = "普通";
                color = ACCENT;
                break;
        }

        textPaint.setTextSize(12);
        float textW = textPaint.measureText(name);
        float w = textW + 26;
        float h = 24;
        float x = (PlayField.width() - w) / 2f;
        float y = 14;

        drawPanel(canvas, x, y, w, h, h, 0xBE080C12, (color & 0x00FFFFFF) | 0xCD000000);
        textPaint.setColor(color);
        canvas.drawText(name, x + 13, y + h - 8, textPaint);
    }

    // ---------------- 通用绘制 ----------------

    /** 圆角面板：填充 + 细描边 */
    private void drawPanel(Canvas canvas, float x, float y, float w, float h,
                           float arc, int fillColor, int borderColor) {
        fillPaint.setColor(fillColor);
        canvas.drawRoundRect(x, y, x + w, y + h, arc, arc, fillPaint);
        strokePaint.setColor(borderColor);
        canvas.drawRoundRect(x, y, x + w, y + h, arc, arc, strokePaint);
    }

    private void drawOverlay(Canvas canvas, String title, String subtitle, int titleColor) {
        fillPaint.setColor(0xAF060A10);
        canvas.drawRect(0, 0, PlayField.width(), PlayField.height(), fillPaint);

        float cx = PlayField.width() / 2f;
        float cy = PlayField.height() / 2f;

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(titleColor);
        textPaint.setTextSize(36);
        canvas.drawText(title, cx, cy, textPaint);

        textPaint.setColor(TEXT_MUTED);
        textPaint.setTextSize(15);
        canvas.drawText(subtitle, cx, cy + 34, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** 供宿主判断点击是否落在暂停按钮上（暂用右上角留白区域） */
    public static RectF pauseButtonBounds() {
        float size = 34;
        return new RectF(PlayField.width() - 12 - 100 - size - 10, 12,
                PlayField.width() - 12 - 100 - 10, 12 + size);
    }
}
