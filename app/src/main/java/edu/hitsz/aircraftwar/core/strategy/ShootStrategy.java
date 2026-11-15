package edu.hitsz.aircraftwar.core.strategy;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;

import java.util.List;

/** 射击策略（策略模式）：把「怎么打」从「谁来打」里解耦 */
public interface ShootStrategy {

    List<BaseBullet> shoot(AbstractAircraft aircraft);
}
