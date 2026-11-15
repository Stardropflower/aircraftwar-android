package edu.hitsz.aircraftwar.core.aircraft.factory;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;

/** 敌机工厂接口（工厂模式） */
public interface EnemyFactory {

    AbstractAircraft createEnemy();
}
