package edu.hitsz.aircraftwar.core;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 普通模式：每 1000 分出现 Boss（固定血量），敌机随时间逐步加速加血。
 */
public class NormalGameWorld extends GameWorld {

    public NormalGameWorld() {
        this(1);
    }

    public NormalGameWorld(int playerCount) {
        super(Difficulty.NORMAL, playerCount);
    }

    @Override
    protected void initDifficultyParams() {
        enemyMaxNumber = 5;
        enemySpawnCycle = 20;
        heroShootCycle = 10;
        bossSpawnThreshold = 1000;
        mobProbability = 0.60;
        eliteProbability = 0.20;
        elitePlusProbability = 0.10;
        enemySpeedMultiplier = 1.0;
        enemyHpMultiplier = 1.0;
        enemyShootIntervalMultiplier = 1.0;
    }

    @Override
    protected void applyDifficultyProgression(int playerIndex) {
        enemySpawnCycle = Math.max(5, enemySpawnCycle - 0.4);
        enemySpeedMultiplier += 0.02;
        enemyHpMultiplier += 0.02;
        eliteProbability = Math.min(0.35, eliteProbability + 0.01);
        mobProbability = Math.max(0.40, mobProbability - 0.01);
        heroShootCycle = Math.max(6, heroShootCycle - 0.15);
    }

    @Override
    protected int getBossHp() {
        return 600;
    }
}
