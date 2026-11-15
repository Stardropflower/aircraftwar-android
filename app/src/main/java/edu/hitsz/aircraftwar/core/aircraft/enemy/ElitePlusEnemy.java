package edu.hitsz.aircraftwar.core.aircraft.enemy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.strategy.StraightShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 精锐敌机：左右移动、一次发射两发子弹、掉率更高、分数略高。
 */
public class ElitePlusEnemy extends AbstractEnemy {

    private static final Random RANDOM = new Random();
    private static final double DROP_PROBABILITY = 0.45;

    private boolean hasDropped = false;

    public ElitePlusEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
        setShootStrategy(new StraightShootStrategy());
    }

    public static ElitePlusEnemy create() {
        int locationX = (int) (Math.random() * (PlayField.width() - 80));
        int locationY = (int) (Math.random() * PlayField.height() * 0.05);
        int speedX = (Math.random() < 0.5) ? 3 : -3;
        return new ElitePlusEnemy(locationX, locationY, speedX, 7, 120);
    }

    @Override
    protected int getShootInterval() {
        return 40;   // 1.6 秒一次
    }

    @Override
    protected List<BaseBullet> doShoot() {
        return Collections.emptyList();
    }

    @Override
    public int getPower() {
        return 20;
    }

    @Override
    public boolean shouldDropItem() {
        return !hasDropped && RANDOM.nextDouble() < DROP_PROBABILITY;
    }

    @Override
    public void markDropped() {
        hasDropped = true;
    }

    @Override
    public int getScore() {
        return 20;
    }
}
