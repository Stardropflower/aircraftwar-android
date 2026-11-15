package edu.hitsz.aircraftwar.core.aircraft.factory;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy;

public class EliteProEnemyFactory implements EnemyFactory {

    @Override
    public AbstractAircraft createEnemy() {
        return EliteProEnemy.create();
    }
}
