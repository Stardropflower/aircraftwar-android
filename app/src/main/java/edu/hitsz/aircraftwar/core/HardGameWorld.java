package edu.hitsz.aircraftwar.core;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 困难模式：每 800 分出现 Boss（血量递增），全方位难度递增，
 * 并且有 Elo 机制——英雄血量越低，射击越快。
 */
public class HardGameWorld extends GameWorld {

    private static final int BOSS_HP_INCREMENT = 100;

    private int currentBossHp = 600;

    public HardGameWorld() {
        this(1);
    }

    public HardGameWorld(int playerCount) {
        super(Difficulty.HARD, playerCount);
    }

    @Override
    protected void initDifficultyParams() {
        enemyMaxNumber = 7;
        enemySpawnCycle = 16;
        heroShootCycle = 12;
        bossSpawnThreshold = 800;
        mobProbability = 0.50;
        eliteProbability = 0.25;
        elitePlusProbability = 0.15;
        enemySpeedMultiplier = 1.0;
        enemyHpMultiplier = 1.0;
        enemyShootIntervalMultiplier = 1.0;
    }

    /**
     * Elo 机制：血量比例越低，射击周期越短（射得越快）。
     * 倍率 0.4 ~ 1.0，即残血时射速最高约为满血的 2.5 倍，但周期不低于 3 帧。
     */
    @Override
    protected double getEffectiveHeroShootCycle(HeroAircraft hero) {
        double hpRatio = (double) hero.getHp() / hero.getMaxHp();
        double eloMultiplier = 0.4 + hpRatio * 0.6;
        return Math.max(3, heroShootCycle * eloMultiplier);
    }

    @Override
    protected int getBossHp() {
        return currentBossHp;
    }

    @Override
    protected void onBossSpawned() {
        currentBossHp += BOSS_HP_INCREMENT;
    }

    @Override
    protected void applyDifficultyProgression(int playerIndex) {
        enemySpawnCycle = Math.max(3, enemySpawnCycle - 0.6);
        enemySpeedMultiplier += 0.03;
        enemyHpMultiplier += 0.03;
        enemyShootIntervalMultiplier = Math.max(0.5, enemyShootIntervalMultiplier - 0.02);
        heroShootCycle = Math.max(5, heroShootCycle - 0.2);
        eliteProbability = Math.min(0.40, eliteProbability + 0.015);
        elitePlusProbability = Math.min(0.25, elitePlusProbability + 0.01);
        mobProbability = Math.max(0.25, 1.0 - eliteProbability - elitePlusProbability - 0.10);

        // 困难模式随时间给玩家加火力，否则跟不上敌机强度
        getHero(playerIndex).increasePower(2);
    }
}
