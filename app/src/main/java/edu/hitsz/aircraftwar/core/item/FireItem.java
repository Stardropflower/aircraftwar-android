package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;

/** 火力：6 秒内 3 发散射（入背包，按空格使用） */
public class FireItem extends AbstractItem {

    public FireItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    public static FireItem create(int x, int y) {
        return new FireItem(x, y, 0, 5);
    }

    @Override
    public void effect(HeroAircraft heroAircraft) {
        heroAircraft.setFireBoost(HeroAircraft.FireType.FIRE);
    }
}
