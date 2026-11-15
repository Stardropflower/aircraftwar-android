package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;

/** 射击上下文：持有可切换的策略 */
public class ShootContext {

    private ShootStrategy strategy;

    public ShootContext(ShootStrategy strategy) {
        this.strategy = strategy;
    }

    public void setStrategy(ShootStrategy strategy) {
        this.strategy = strategy;
    }

    public ShootStrategy getStrategy() {
        return strategy;
    }

    public java.util.List<edu.hitsz.aircraftwar.core.bullet.BaseBullet> executeShoot(AbstractAircraft aircraft) {
        return strategy.shoot(aircraft);
    }
}
