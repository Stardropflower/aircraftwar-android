package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.item.event.BombSubject;

/** 炸弹：碰撞后立即生效，清屏 + 清空敌弹 */
public class BombItem extends AbstractItem {

    public BombItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    public static BombItem create(int x, int y) {
        return new BombItem(x, y, 0, 5);
    }

    @Override
    public void effect(HeroAircraft heroAircraft) {
        BombSubject.getInstance().trigger();
    }
}
