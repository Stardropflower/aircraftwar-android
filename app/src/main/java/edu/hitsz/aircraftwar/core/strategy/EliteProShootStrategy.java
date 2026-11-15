package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.bullet.EnemyBullet;

import java.util.LinkedList;
import java.util.List;

/**
 * 精英 Pro 敌机的两段式弹幕：一次三向散弹，下一次竖直四连发，交替进行。
 */
public class EliteProShootStrategy implements ShootStrategy {

    private int shootPattern = 0;

    @Override
    public List<BaseBullet> shoot(AbstractAircraft aircraft) {
        List<BaseBullet> res = new LinkedList<>();
        int x = aircraft.getLocationX();
        int yBase = aircraft.getLocationY() + 2;
        int bulletSpeedY = aircraft.getSpeedY() + 8;
        int power = aircraft.getPower();

        if (shootPattern == 0) {
            res.add(new EnemyBullet(x - 12, yBase, -5, bulletSpeedY, power));
            res.add(new EnemyBullet(x, yBase, 0, bulletSpeedY, power));
            res.add(new EnemyBullet(x + 12, yBase, 5, bulletSpeedY, power));
        } else {
            for (int i = 0; i < 4; i++) {
                res.add(new EnemyBullet(x, yBase + i * 8, 0, bulletSpeedY, power));
            }
        }
        shootPattern = (shootPattern + 1) % 2;
        return res;
    }
}
