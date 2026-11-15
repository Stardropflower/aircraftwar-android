package edu.hitsz.aircraftwar.core.aircraft.factory;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.MobEnemy;

public class MobEnemyFactory implements EnemyFactory {

    @Override
    public AbstractAircraft createEnemy() {
        return MobEnemy.create();
    }
}
