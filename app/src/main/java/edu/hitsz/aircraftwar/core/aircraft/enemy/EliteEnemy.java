package edu.hitsz.aircraftwar.core.aircraft.enemy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.strategy.StraightShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 精英敌机：会发射单发子弹，死亡按概率掉落道具。
 */
public class EliteEnemy extends AbstractEnemy {

    private static final Random RANDOM = new Random();
    private static final double DROP_PROBABILITY = 0.3;

    private boolean hasDropped = false;

    public EliteEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
        setShootStrategy(new StraightShootStrategy());
    }

    public static EliteEnemy create() {
        int locationX = (int) (Math.random() * (PlayField.width() - 80));
        int locationY = (int) (Math.random() * PlayField.height() * 0.05);
        return new EliteEnemy(locationX, locationY, 0, 5, 100);
    }

    @Override
    public int getPower() {
        return 20;
    }

    @Override
    protected int getShootInterval() {
        return 30;   // 1.2 秒一次（30 帧 × 40ms）
    }

    @Override
    protected List<BaseBullet> doShoot() {
        return Collections.emptyList();
    }

    @Override
    public boolean shouldDropItem() {
        return !hasDropped && RANDOM.nextDouble() < DROP_PROBABILITY;
    }

    @Override
    public void markDropped() {
        hasDropped = true;
    }
}
