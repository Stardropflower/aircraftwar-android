package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.ElitePlusEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.MobEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.bullet.EnemyBullet;
import edu.hitsz.aircraftwar.core.bullet.HeroBullet;

import java.util.LinkedList;
import java.util.List;

/**
 * 直射：英雄机打竖直平行的多发子弹，敌机打竖直的单/双发。
 */
public class StraightShootStrategy implements ShootStrategy {

    @Override
    public List<BaseBullet> shoot(AbstractAircraft aircraft) {
        List<BaseBullet> res = new LinkedList<>();
        int x = aircraft.getLocationX();
        int y = aircraft.getLocationY();
        int power = aircraft.getPower();

        if (aircraft instanceof HeroAircraft) {
            HeroAircraft hero = (HeroAircraft) aircraft;
            int bulletCount = hero.getCurrentBulletCount();   // 2、3 或 5
            y -= 2;
            int speedY = -20;
            for (int i = 0; i < bulletCount; i++) {
                int offsetX = (i * 2 - bulletCount + 1) * 10;
                res.add(new HeroBullet(x + offsetX, y, 0, speedY, power));
            }
        } else {
            y += 2;
            int speedY = 8;
            int bulletCount = enemyBulletCount(aircraft);
            for (int i = 0; i < bulletCount; i++) {
                int offsetX = (i * 2 - bulletCount + 1) * 10;
                res.add(new EnemyBullet(x + offsetX, y, 0, speedY, power));
            }
        }
        return res;
    }

    /**
     * 敌机直射的子弹数。
     * <p>
     * 这里用 {@code instanceof} 而不是原版的 {@code getClass().getSimpleName()} 字符串比较：
     * Android 上 R8 可能混淆类名，靠类名字符串判断会在 release 包里静默失效。
     * 行为与原来一致：Mob 0 发、ElitePlus 2 发、其余 1 发。
     */
    private static int enemyBulletCount(AbstractAircraft aircraft) {
        if (aircraft instanceof MobEnemy) {
            return 0;
        }
        if (aircraft instanceof ElitePlusEnemy) {
            return 2;
        }
        return 1;
    }
}
