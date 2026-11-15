package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;

/** 超级火力：6 秒内 20 发环射（入背包，按空格使用） */
public class SuperFireItem extends AbstractItem {

    public SuperFireItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    public static SuperFireItem create(int x, int y) {
        return new SuperFireItem(x, y, 0, 5);
    }

    @Override
    public void effect(HeroAircraft heroAircraft) {
        heroAircraft.setFireBoost(HeroAircraft.FireType.SUPER_FIRE);
    }
}
