package edu.hitsz.aircraftwar.core.aircraft.factory;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteEnemy;

public class EliteEnemyFactory implements EnemyFactory {

    @Override
    public AbstractAircraft createEnemy() {
        return EliteEnemy.create();
    }
}
