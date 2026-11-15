package edu.hitsz.aircraftwar.core.aircraft.enemy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.strategy.EliteProShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 精英 Pro 敌机：血量厚、两段式弹幕、掉率最高；冰冻只减速不冻结。
 */
public class EliteProEnemy extends AbstractEnemy {

    private static final Random RANDOM = new Random();
    private static final double DROP_PROBABILITY = 0.6;

    private boolean hasDropped = false;

    public EliteProEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
        setShootStrategy(new EliteProShootStrategy());
    }

    public static EliteProEnemy create() {
        int locationX = (int) (Math.random() * (PlayField.width() - 80));
        int locationY = (int) (Math.random() * PlayField.height() * 0.05);
        int speedX = (Math.random() < 0.5) ? 4 : -4;
        return new EliteProEnemy(locationX, locationY, speedX, 4, 200);
    }

    @Override
    protected int getShootInterval() {
        return 30;
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

    @Override
    public int getScore() {
        return 50;
    }

    @Override
    public int getPower() {
        return 60;
    }
}
