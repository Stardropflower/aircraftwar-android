package edu.hitsz.aircraftwar.core.aircraft.enemy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;

/**
 * 普通敌机（Mob）：不射击、不掉落道具、只有本体撞击伤害。
 */
public class MobEnemy extends AbstractEnemy {

    public MobEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
    }

    public static MobEnemy create() {
        int locationX = (int) (Math.random() * (PlayField.width() - 60));
        int locationY = (int) (Math.random() * PlayField.height() * 0.05);
        return new MobEnemy(locationX, locationY, 0, 10, 20);
    }

    @Override
    protected List<BaseBullet> doShoot() {
        return Collections.emptyList();
    }

    @Override
    public int getPower() {
        return 0;
    }
}
