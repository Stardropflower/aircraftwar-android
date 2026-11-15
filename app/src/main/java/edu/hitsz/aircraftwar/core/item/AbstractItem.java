package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.basic.AbstractFlyingObject;
import edu.hitsz.aircraftwar.core.world.PlayField;

/**
 * 道具基类：向下飘落，飘出屏幕底部即失效。
 */
public abstract class AbstractItem extends AbstractFlyingObject implements Item {

    protected AbstractItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    @Override
    public void forward() {
        super.forward();
        if (locationY >= PlayField.height()) {
            vanish();
        }
    }

    @Override
    public abstract void effect(HeroAircraft heroAircraft);
}
