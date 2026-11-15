package edu.hitsz.aircraftwar.core.bullet;

import edu.hitsz.aircraftwar.core.basic.AbstractFlyingObject;
import edu.hitsz.aircraftwar.core.world.PlayField;

/**
 * 子弹基类：越界即消失（注意与飞行物不同，子弹不反弹）。
 */
public abstract class BaseBullet extends AbstractFlyingObject {

    private final int power;

    /**
     * 发射者编号（双人模式下 0/1）。
     * <p>
     * 子弹由策略统一创建，策略并不知道是谁开的枪，所以在 {@code GameWorld} 里
     * 发射后回填这个编号，用于把击杀分数记到正确的玩家头上。
     */
    private int ownerIndex = 0;

    protected BaseBullet(int locationX, int locationY, int speedX, int speedY, int power) {
        super(locationX, locationY, speedX, speedY);
        this.power = power;
    }

    public int getOwnerIndex() {
        return ownerIndex;
    }

    public void setOwnerIndex(int ownerIndex) {
        this.ownerIndex = ownerIndex;
    }

    @Override
    public void forward() {
        super.forward();

        // 横向出界
        if (locationX <= 0 || locationX >= PlayField.width()) {
            vanish();
            return;
        }

        // 纵向出界：向下飞的判断下边界，向上的判断上边界
        if (speedY > 0 && locationY >= PlayField.height()) {
            vanish();
        } else if (locationY <= 0) {
            vanish();
        }
    }

    public int getPower() {
        return power;
    }
}
