package edu.hitsz.aircraftwar.core;

import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 简单模式：无 Boss，难度不递增，供上手练习。数值与 Windows 版一致。
 */
public class EasyGameWorld extends GameWorld {

    public EasyGameWorld() {
        this(1);
    }

    public EasyGameWorld(int playerCount) {
        super(Difficulty.EASY, playerCount);
    }

    @Override
    protected void initDifficultyParams() {
        enemyMaxNumber = 5;
        enemySpawnCycle = 25;
        heroShootCycle = 8;
        // 用 MAX_VALUE 让 checkBossSpawn 里的除法阈值永远为 0，即不刷 Boss
        bossSpawnThreshold = Integer.MAX_VALUE;
        mobProbability = 0.65;
        eliteProbability = 0.20;
        elitePlusProbability = 0.10;
        enemySpeedMultiplier = 1.0;
        enemyHpMultiplier = 1.0;
        enemyShootIntervalMultiplier = 1.0;
    }

    @Override
    protected void applyDifficultyProgression(int playerIndex) {
        // 简单模式不递增
    }
}
