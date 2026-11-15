package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.bullet.EnemyBullet;
import edu.hitsz.aircraftwar.core.bullet.HeroBullet;

import java.util.LinkedList;
import java.util.List;

/**
 * 散弹：多条带横向速度的子弹呈扇形展开。
 * <p>
 * 子弹数量按「英雄机当前火力档位」决定：2 发基准、3 发火力、5 发超火力。
 */
public class SpreadShootStrategy implements ShootStrategy {

    private static final int ENEMY_BULLET_COUNT = 3;

    @Override
    public List<BaseBullet> shoot(AbstractAircraft aircraft) {
        List<BaseBullet> res = new LinkedList<>();
        int x = aircraft.getLocationX();
        int y = aircraft.getLocationY();
        int power = aircraft.getPower();
        boolean isHero = aircraft instanceof HeroAircraft;

        int bulletCount;
        if (isHero) {
            bulletCount = ((HeroAircraft) aircraft).getCurrentBulletCount();
            y -= 2;
        } else {
            bulletCount = ENEMY_BULLET_COUNT;
            y += 2;
        }

        int speedY = isHero ? -20 : 8;

        if (bulletCount >= 5) {
            res.add(bullet(isHero, x - 20, y, -8, speedY, power));
            res.add(bullet(isHero, x - 10, y, -4, speedY, power));
            res.add(bullet(isHero, x, y, 0, speedY, power));
            res.add(bullet(isHero, x + 10, y, 4, speedY, power));
            res.add(bullet(isHero, x + 20, y, 8, speedY, power));
        } else if (bulletCount == 3) {
            res.add(bullet(isHero, x - 12, y, -5, speedY, power));
            res.add(bullet(isHero, x, y, 0, speedY, power));
            res.add(bullet(isHero, x + 12, y, 5, speedY, power));
        } else {
            res.add(bullet(isHero, x - 12, y, -5, speedY, power));
            res.add(bullet(isHero, x + 12, y, 5, speedY, power));
        }
        return res;
    }

    private static BaseBullet bullet(boolean hero, int x, int y, int speedX, int speedY, int power) {
        return hero
                ? new HeroBullet(x, y, speedX, speedY, power)
                : new EnemyBullet(x, y, speedX, speedY, power);
    }
}
