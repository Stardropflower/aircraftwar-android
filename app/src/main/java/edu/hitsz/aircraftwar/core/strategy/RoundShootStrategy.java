package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.bullet.EnemyBullet;
import edu.hitsz.aircraftwar.core.bullet.HeroBullet;

import java.util.LinkedList;
import java.util.List;

/** 环形弹幕：20 发子弹均匀铺满一圈（超级火力的英雄机与 Boss 都用它） */
public class RoundShootStrategy implements ShootStrategy {

    private static final int BULLET_COUNT = 20;
    private static final double ANGLE_STEP = 2 * Math.PI / BULLET_COUNT;
    private static final int SPEED = 10;

    @Override
    public List<BaseBullet> shoot(AbstractAircraft aircraft) {
        List<BaseBullet> res = new LinkedList<>();
        int x = aircraft.getLocationX();
        int y = aircraft.getLocationY();
        int power = aircraft.getPower();
        boolean isHero = aircraft instanceof HeroAircraft;

        int originY = isHero ? y - 2 : y + 2;

        for (int i = 0; i < BULLET_COUNT; i++) {
            double angle = i * ANGLE_STEP;
            int speedX = (int) (SPEED * Math.cos(angle));
            int speedY = (int) (SPEED * Math.sin(angle));
            if (isHero) {
                res.add(new HeroBullet(x, originY, speedX, speedY - 6, power));
            } else {
                res.add(new EnemyBullet(x, originY, speedX, speedY + 5, power));
            }
        }
        return res;
    }
}
