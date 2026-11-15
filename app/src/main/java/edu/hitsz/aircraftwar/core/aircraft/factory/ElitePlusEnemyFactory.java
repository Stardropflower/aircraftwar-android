package edu.hitsz.aircraftwar.core.aircraft.factory;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.ElitePlusEnemy;

public class ElitePlusEnemyFactory implements EnemyFactory {

    @Override
    public AbstractAircraft createEnemy() {
        return ElitePlusEnemy.create();
    }
}
