package edu.hitsz.aircraftwar.core.aircraft;

import edu.hitsz.aircraftwar.core.basic.AbstractFlyingObject;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;

import java.util.List;

/**
 * 所有种类飞机的抽象父类：血量与射击能力。
 */
public abstract class AbstractAircraft extends AbstractFlyingObject {

    protected int maxHp;
    protected int hp;

    protected AbstractAircraft(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY);
        this.hp = hp;
        this.maxHp = hp;
    }

    public void decreaseHp(int decrease) {
        hp -= decrease;
        if (hp <= 0) {
            hp = 0;
            vanish();
        }
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
        this.maxHp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /** 子弹威力 / 撞击伤害 */
    public abstract int getPower();

    /** 本帧要发射的子弹（可能为空列表，但不应返回 null） */
    public abstract List<BaseBullet> shoot();
}
