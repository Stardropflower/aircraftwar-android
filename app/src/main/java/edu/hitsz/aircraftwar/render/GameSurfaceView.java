package edu.hitsz.aircraftwar.render;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import edu.hitsz.aircraftwar.core.GameWorld;
import edu.hitsz.aircraftwar.core.world.PlayField;

/**
 * 游戏画面：{@link SurfaceView} + 独立渲染线程。
 * <p>
 * 这是 Android 上替代 Windows 版 {@code javax.swing.Timer} 的标准做法：
 * 游戏循环绝不能跑在主线程（会 ANR），也不能用 {@code View.postInvalidate} 驱动
 * （帧率不可控且受 UI 事件影响）。这里用一个专用线程：
 * <ul>
 *   <li><b>逻辑</b>固定 25Hz（40ms 一步），与 Windows 版完全一致；</li>
 *   <li><b>渲染</b>尽量贴近屏幕刷新率，用插值把逻辑帧之间的画面补顺；</li>
 *   <li>落后太多时丢弃欠账，而不是连补一堆逻辑帧（否则会出现"追帧"式连跳）。</li>
 * </ul>
 * 触屏事件在主线程产生，这里只把目标坐标写进 volatile 字段，
 * 由渲染线程在每个逻辑步开始时统一应用 —— 避免直接跨线程改游戏状态。
 */
public class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    /** 逻辑步长：40ms = 25Hz，改它会直接改掉手感与平衡 */
    private static final long LOGIC_INTERVAL_NS = 40_000_000L;
    /** 渲染线程的最小休眠，避免空转吃满 CPU */
    private static final long IDLE_SLEEP_MS = 4L;
    /** 手指上方偏移（逻辑单位）：避免手指本身挡住飞机 */
    private static final float TOUCH_OFFSET_Y = 70f;
    /**
     * 拖动阈值（逻辑单位）：手指移动超过这个距离，飞机才开始跟随。
     * <p>
     * 为什么要这个阈值：小屏手机上想点右上角的道具格，稍微点偏一点就会落到战场上，
     * 原来是「按下即跟随」，飞机瞬间跳去顶部 UI 区，看起来像是误判成移动。
     * 加了阈值之后，单纯点一下不会让飞机动，必须真的拖动才行。
     */
    private static final float DRAG_THRESHOLD = 10f;

    public interface Callback {
        void onGameOver(int score);

        /** 玩家主动要求退出本局（点返回键 / 暂停菜单里选返回主菜单），需要立刻结算 */
        void onExitRequested();
    }

    private final GameWorld world;
    private final GameRenderer renderer;
    private Callback callback;

    private Thread loopThread;
    private volatile boolean running;
    private volatile boolean paused;

    private final float[] targetX = new float[2];
    private final float[] targetY = new float[2];
    private final boolean[] hasTarget = new boolean[2];
    private final int[] pointerOwner = {-1, -1};
    /** 按下点（用于判断是否超过拖动阈值） */
    private final float[] downX = new float[2];
    private final float[] downY = new float[2];
    /** 该玩家当前是否已经进入「拖动」状态 */
    private final boolean[] dragging = new boolean[2];

    private long lastTouchDownMs = 0;
    /** 主线程投递、由渲染线程在下一个逻辑步消费的「使用背包道具」请求 */
    private volatile boolean pendingBackpackUse = false;
    /** 本次触摸是否落在 UI 元素上（背包、暂停按钮），用于区分「点 UI」和「点战场」 */
    private boolean uiTouch = false;

    public GameSurfaceView(Context context, GameWorld world, Assets assets) {
        super(context);
        this.world = world;
        this.renderer = new GameRenderer(assets);
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    // ---------------- Surface 生命周期 ----------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        loopThread = new Thread(this, "aircraftwar-loop");
        loopThread.setPriority(Thread.NORM_PRIORITY + 1);
        loopThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 尺寸变化时无需重建，渲染器每次按 Canvas 尺寸重新计算缩放
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopLoop();
    }

    private void stopLoop() {
        running = false;
        Thread t = loopThread;
        loopThread = null;
        if (t != null) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Activity 的 onPause/onResume 调用 */
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            // 暂停时丢弃未消费的道具使用请求，避免恢复后突然用掉一个道具
            pendingBackpackUse = false;
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void togglePause() {
        paused = !paused;
    }

    public void shutdown() {
        stopLoop();
    }

    // ---------------- 主循环 ----------------

    @Override
    public void run() {
        long lastLogic = System.nanoTime();

        while (running) {
            long now = System.nanoTime();

            if (!paused) {
                if (now - lastLogic >= LOGIC_INTERVAL_NS) {
                    // 按固定步长累加，而不是对齐到当前帧；否则实际步长会被渲染节拍撑长
                    lastLogic += LOGIC_INTERVAL_NS;
                    if (now - lastLogic > 5 * LOGIC_INTERVAL_NS) {
                        lastLogic = now;   // 落后太多（切后台/卡顿）就丢弃欠账
                    }
                    applyInput();
                    world.update();
                    if (world.isGameOver() && callback != null) {
                        Callback cb = callback;
                        callback = null;      // 只回调一次
                        cb.onGameOver(world.totalScore());
                    }
                }
            } else {
                lastLogic = now;
            }

            double alpha = paused ? 1.0
                    : Math.min(1.0, (System.nanoTime() - lastLogic) / (double) LOGIC_INTERVAL_NS);

            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                if (canvas != null) {
                    renderer.draw(canvas, world, alpha, paused);
                }
            } catch (Throwable t) {
                // Surface 被销毁时 lockCanvas 会抛异常，属于正常流程，不刷屏
            } finally {
                if (canvas != null) {
                    try {
                        getHolder().unlockCanvasAndPost(canvas);
                    } catch (Throwable ignored) {
                        // 同上
                    }
                }
            }

            try {
                Thread.sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** 把主线程记录的目标坐标应用到英雄机 */
    private void applyInput() {
        // 背包使用请求也在这里消费：背包是核心层的列表，渲染线程正在遍历它，
        // 如果在主线程直接调用 useBackpackItem 会一边遍历一边改，抛 ConcurrentModificationException
        if (pendingBackpackUse) {
            pendingBackpackUse = false;
            world.useBackpackItem(0);
        }

        for (int p = 0; p < world.getPlayerCount(); p++) {
            if (hasTarget[p]) {
                world.getHero(p).moveTo(targetX[p], targetY[p]);
            }
        }
    }

    // ---------------- 触屏输入 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float scale = getWidth() / (float) PlayField.width();
        if (scale <= 0) {
            return false;
        }

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            uiTouch = false;
            float lx = event.getX() / scale;
            float ly = event.getY() / scale;

            // 返回键：请求离开本局（结算由 Activity 负责），不把飞机带过去
            if (GameRenderer.backButtonBounds().contains(lx, ly)) {
                uiTouch = true;
                if (callback != null && !world.isGameOver()) {
                    paused = true;      // 先停下来，避免结算过程中还在跑
                    callback.onExitRequested();
                }
                return true;
            }

            // 暂停按钮
            if (GameRenderer.pauseButtonBounds().contains(lx, ly)) {
                uiTouch = true;
                togglePause();
                return true;
            }

            // 暂停遮罩上的「返回主菜单」按钮
            if (paused && GameRenderer.pauseExitBounds().contains(lx, ly)) {
                uiTouch = true;
                paused = true;
                if (callback != null && !world.isGameOver()) {
                    callback.onExitRequested();
                }
                return true;
            }

            // 背包区域：点一下使用最顶上那个道具（栈顶 = 最后捡到的）
            // 注意只是「投递请求」，真正的使用发生在渲染线程的逻辑步里
            if (!paused && GameRenderer.backpackArea().contains(lx, ly)) {
                uiTouch = true;
                pendingBackpackUse = true;
                return true;
            }

            // 顶部 HUD 条带：既不是按钮也不该移动飞机，直接忽略，
            // 避免「想点道具却点偏，飞机一下窜到顶部」这种误判
            if (ly < PlayField.topInset()) {
                uiTouch = true;
                return true;
            }

            lastTouchDownMs = System.currentTimeMillis();
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 按下时只记录起点，不立刻移动飞机（这就是「拖动」而不是「点哪飞哪」）
                int index = event.getActionIndex();
                int id = event.getPointerId(index);
                float lx = event.getX(index) / scale;
                float ly = event.getY(index) / scale;
                int player = playerForPointer(id, lx);
                pointerOwner[player] = id;
                downX[player] = lx;
                downY[player] = ly;
                dragging[player] = false;
                hasTarget[player] = false;
                break;
            }

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    int player = -1;
                    for (int p = 0; p < pointerOwner.length; p++) {
                        if (pointerOwner[p] == id) {
                            player = p;
                            break;
                        }
                    }
                    if (player < 0) {
                        continue;
                    }
                    float lx = event.getX(i) / scale;
                    float ly = event.getY(i) / scale;

                    if (!dragging[player]) {
                        // 移动距离超过阈值才算真的在拖，否则视为点击
                        float dx = lx - downX[player];
                        float dy = ly - downY[player];
                        if (Math.hypot(dx, dy) < DRAG_THRESHOLD) {
                            continue;
                        }
                        dragging[player] = true;
                    }

                    targetX[player] = lx;
                    // 飞机放在手指上方一段距离，否则手指会遮住飞机
                    targetY[player] = ly - TOUCH_OFFSET_Y;
                    hasTarget[player] = true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = event.getPointerId(event.getActionIndex());
                for (int p = 0; p < pointerOwner.length; p++) {
                    if (pointerOwner[p] == id) {
                        pointerOwner[p] = -1;
                        hasTarget[p] = false;
                        dragging[p] = false;
                    }
                }
                // 暂停状态下点一下空白画面继续（点在 UI 上不算）
                if (action == MotionEvent.ACTION_UP && !uiTouch && paused
                        && !world.isGameOver()
                        && System.currentTimeMillis() - lastTouchDownMs < 300) {
                    paused = false;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                java.util.Arrays.fill(pointerOwner, -1);
                java.util.Arrays.fill(hasTarget, false);
                java.util.Arrays.fill(dragging, false);
                break;

            default:
                break;
        }
        return true;
    }

    /**
     * 双人同屏：屏幕左半边归 P1，右半边归 P2。
     * 单人模式恒为 P1。
     */
    private int playerForPointer(int pointerId, float logicalX) {
        if (world.getPlayerCount() == 1) {
            return 0;
        }
        for (int p = 0; p < pointerOwner.length; p++) {
            if (pointerOwner[p] == pointerId) {
                return p;   // 已经归属过的指针保持不变，避免手指划过中线时"换人"
            }
        }
        return logicalX < PlayField.width() / 2f ? 0 : 1;
    }
}
