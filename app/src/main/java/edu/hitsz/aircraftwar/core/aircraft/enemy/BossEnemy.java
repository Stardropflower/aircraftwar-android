package edu.hitsz.aircraftwar.core.aircraft.enemy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.strategy.RoundShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;

/**
 * Boss：横向移动、环形弹幕、免疫冰冻、死亡掉落 3 个道具。
 */
public class BossEnemy extends AbstractEnemy {

    /** Boss 死亡时掉落的道具数 */
    public static final int DROP_COUNT = 3;

    private int power = 30;

    public BossEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
        this.speedY = 0;
        this.originalSpeedY = 0;
        setShootStrategy(new RoundShootStrategy());
    }

    public static BossEnemy create() {
        return create(600);
    }

    public static BossEnemy create(int hp) {
        return new BossEnemy(PlayField.width() / 2, 100, 3, 0, hp);
    }

    @Override
    protected int getShootInterval() {
        return 20;
    }

    @Override
    protected List<BaseBullet> doShoot() {
        return Collections.emptyList();
    }

    @Override
    public boolean shouldDropItem() {
        return false;
    }

    /** Boss 固定掉 3 个；被炸弹炸死则不掉 */
    @Override
    public int dropCount() {
        return killedByBomb ? 0 : DROP_COUNT;
    }

    @Override
    public int getScore() {
        return 300;
    }

    @Override
    public int getPower() {
        return power;
    }
}
