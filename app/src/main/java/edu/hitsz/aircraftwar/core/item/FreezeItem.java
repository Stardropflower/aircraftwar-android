package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.item.event.FreezeSubject;

/** 冰冻：碰撞后立即生效，按敌机类型冻结或减速 */
public class FreezeItem extends AbstractItem {

    public FreezeItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    public static FreezeItem create(int x, int y) {
        return new FreezeItem(x, y, 0, 5);
    }

    @Override
    public void effect(HeroAircraft heroAircraft) {
        FreezeSubject.getInstance().trigger();
    }
}
