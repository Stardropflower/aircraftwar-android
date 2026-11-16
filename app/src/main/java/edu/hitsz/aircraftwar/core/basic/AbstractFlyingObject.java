package edu.hitsz.aircraftwar.core.basic;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.sprite.SpriteMetricsHolder;
import edu.hitsz.aircraftwar.core.world.PlayField;

/**
 * 所有飞行物的抽象基类：位置、速度、边界反弹、碰撞判定、渲染插值。
 * <p>
 * 与 Windows 版相比只有两处差别：
 * <ol>
 *   <li>去掉了 {@code BufferedImage} 字段 —— 图像归渲染层管，核心层只关心几何；</li>
 *   <li>宽高改为向 {@link SpriteMetricsHolder} 查询，而不是从图片对象上读。</li>
 * </ol>
 * 其余逻辑（含碰撞的 factor 规则、插值方式）与原版逐行一致，便于对照验证。
 */
public abstract class AbstractFlyingObject {

    protected int locationX;
    protected int locationY;
    protected int speedX;
    protected int speedY;

    protected int width = -1;
    protected int height = -1;

    protected boolean isValid = true;

    /**
     * 上一次逻辑推进之前的位置，仅供渲染插值使用。
     * <p>
     * 逻辑仍然是每 40ms 推进一步（改这个会直接改变手感与平衡），
     * 但渲染跑在约 60fps：用 {@link #renderX}/{@link #renderY} 在 prev 与当前位置之间插值。
     * 碰撞、计分等全部逻辑仍然读真实位置 {@code locationX/Y}。
     */
    protected int prevLocationX;
    protected int prevLocationY;

    protected AbstractFlyingObject() {
    }

    protected AbstractFlyingObject(int locationX, int locationY, int speedX, int speedY) {
        this.locationX = locationX;
        this.locationY = locationY;
        this.speedX = speedX;
        this.speedY = speedY;
        this.prevLocationX = locationX;
        this.prevLocationY = locationY;
    }

    /** 在每个逻辑步开始前记下当前位置，作为这一帧插值的起点 */
    public void snapshotPosition() {
        prevLocationX = locationX;
        prevLocationY = locationY;
    }

    /** 插值后的绘制坐标，alpha 为「距上一次逻辑推进过去了多少比例」 */
    public int renderX(double alpha) {
        return (int) Math.round(prevLocationX + (locationX - prevLocationX) * alpha);
    }

    public int renderY(double alpha) {
        return (int) Math.round(prevLocationY + (locationY - prevLocationY) * alpha);
    }

    /** 按当前速度推进一帧；碰到左右边界则反弹 */
    public void forward() {
        locationX += speedX;
        locationY += speedY;
        if (locationX <= 0 || locationX >= PlayField.width()) {
            speedX = -speedX;
        }
    }

    /**
     * 碰撞判定。
     * <p>
     * 注意 factor 规则：飞行器（{@link AbstractAircraft}）的判定框取图片的一半高度，
     * 子弹与道具取全高。这是原版刻意为之的手感调校，迁移时原样保留。
     */
    public boolean crash(AbstractFlyingObject flyingObject) {
        int factor = this instanceof AbstractAircraft ? 2 : 1;
        int fFactor = flyingObject instanceof AbstractAircraft ? 2 : 1;
        int x = flyingObject.getLocationX();
        int y = flyingObject.getLocationY();
        int fWidth = flyingObject.getWidth();
        int fHeight = flyingObject.getHeight();
        return x + (fWidth + this.getWidth()) / 2 > locationX
                && x - (fWidth + this.getWidth()) / 2 < locationX
                && y + (fHeight / fFactor + this.getHeight() / factor) / 2 > locationY
                && y - (fHeight / fFactor + this.getHeight() / factor) / 2 < locationY;
    }

    public int getLocationX() {
        return locationX;
    }

    public int getLocationY() {
        return locationY;
    }

    public void setLocation(double locationX, double locationY) {
        this.locationX = (int) locationX;
        this.locationY = (int) locationY;
    }

    /** 触屏输入：直接把飞机放到指定坐标（会做战场范围钳制） */
    public void moveTo(double x, double y) {
        setLocation(
                Math.max(0, Math.min(PlayField.width(), x)),
                Math.max(PlayField.topInset(), Math.min(PlayField.height(), y)));
    }

    public int getSpeedX() {
        return speedX;
    }

    public int getSpeedY() {
        return speedY;
    }

    public int getWidth() {
        if (width == -1) {
            width = SpriteMetricsHolder.width(getClass());
        }
        return width;
    }

    public int getHeight() {
        if (height == -1) {
            height = SpriteMetricsHolder.height(getClass());
        }
        return height;
    }

    public void vanish() {
        isValid = false;
    }

    public boolean notValid() {
        return !isValid;
    }
}
