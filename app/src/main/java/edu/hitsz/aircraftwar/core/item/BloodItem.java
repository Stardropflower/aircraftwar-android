package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;

/** 血包：回复 100 生命值 */
public class BloodItem extends AbstractItem {

    public static final int HEAL = 100;

    public BloodItem(int locationX, int locationY, int speedX, int speedY) {
        super(locationX, locationY, speedX, speedY);
    }

    public static BloodItem create(int x, int y) {
        return new BloodItem(x, y, 0, 5);
    }

    @Override
    public void effect(HeroAircraft heroAircraft) {
        heroAircraft.increaseHp(HEAL);
    }
}
