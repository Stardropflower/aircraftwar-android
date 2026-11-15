package edu.hitsz.aircraftwar.core;

import edu.hitsz.aircraftwar.core.aircraft.AbstractAircraft;
import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy;
import edu.hitsz.aircraftwar.core.aircraft.factory.EliteEnemyFactory;
import edu.hitsz.aircraftwar.core.aircraft.factory.ElitePlusEnemyFactory;
import edu.hitsz.aircraftwar.core.aircraft.factory.EliteProEnemyFactory;
import edu.hitsz.aircraftwar.core.aircraft.factory.EnemyFactory;
import edu.hitsz.aircraftwar.core.aircraft.factory.MobEnemyFactory;
import edu.hitsz.aircraftwar.core.basic.AbstractFlyingObject;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.item.AbstractItem;
import edu.hitsz.aircraftwar.core.item.BombItem;
import edu.hitsz.aircraftwar.core.item.FreezeItem;
import edu.hitsz.aircraftwar.core.item.Item;
import edu.hitsz.aircraftwar.core.item.ItemFactory;
import edu.hitsz.aircraftwar.core.item.event.BombSubject;
import edu.hitsz.aircraftwar.core.item.event.FreezeSubject;
import edu.hitsz.aircraftwar.core.item.event.GameEventObserver;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

/**
 * 游戏世界：全部仿真逻辑，<b>不依赖任何平台 API</b>。
 * <p>
 * 它从 Windows 版的 {@code Game}（一个继承 {@code JPanel} 的类）中把逻辑抽出来，
 * 移除了渲染、定时器、音效、弹窗与 DAO 访问，只保留「状态推进」这一件事：
 * <ul>
 *   <li>宿主按逻辑帧率（25Hz）调用 {@link #update()}；</li>
 *   <li>宿主按渲染帧率（60fps）读取实体列表做插值绘制；</li>
 *   <li>音效、震动、结算界面通过 {@link Listener} 回调给平台层。</li>
 * </ul>
 * 这样同一份逻辑可以在 JVM 上单元测试，也能被 Windows 版复用。
 *
 * <h2>双人支持</h2>
 * {@link #heroes} 是列表（1 架或 2 架），分数按玩家分别累计。
 * 这是相对 Windows 版最大的结构改动——原版英雄机是单例。
 */
public abstract class GameWorld implements GameEventObserver {

    /** 背包容量 */
    public static final int BACKPACK_CAPACITY = 2;
    /** 冰冻持续帧数（150 帧 × 40ms = 6 秒） */
    public static final int FREEZE_DURATION = 150;
    /** 减速持续帧数 */
    public static final int SLOW_DURATION = 150;
    /** 难度递增间隔（帧）：500 帧 = 20 秒 */
    public static final int PROGRESSION_INTERVAL = 500;
    /** 炸弹对 Boss / ElitePro 的固定伤害 */
    public static final int BOMB_DAMAGE = 100;

    /** 平台层监听器：音效、震动、结算等由实现方处理 */
    public interface Listener {
        default void onEnemyKilled(AbstractEnemy enemy, HeroAircraft killer) {
        }

        default void onItemPicked(Item item, HeroAircraft hero) {
        }

        default void onHeroDamaged(HeroAircraft hero, int damage) {
        }

        default void onBossSpawned(BossEnemy boss) {
        }

        default void onBossDefeated() {
        }

        default void onBombDetonated() {
        }

        default void onFreezeActivated() {
        }

        default void onGameOver() {
        }
    }

    private static final Listener NO_OP = new Listener() {
    };

    protected final Difficulty difficulty;
    private final int playerCount;
    private Listener listener = NO_OP;

    private final List<AbstractItem> items = new LinkedList<>();
    private final List<AbstractAircraft> enemies = new LinkedList<>();
    private final List<BaseBullet> heroBullets = new LinkedList<>();
    private final List<BaseBullet> enemyBullets = new LinkedList<>();
    private final List<HeroAircraft> heroes = new ArrayList<>();
    /** 每名玩家的背包（栈式：后进先出） */
    private final List<Stack<Item>> backpacks = new ArrayList<>();
    private final int[] scores;
    private final int[] progressionCounters;
    /** 每名英雄机各自的射击计数器：困难模式的 Elo 会让两架飞机的射速不同，不能共用 */
    private final double[] heroShootCounters;
    /**
     * 正在结算的道具拾取者。
     * <p>
     * 炸弹 / 冰冻的效果是通过事件主体广播出去的，主体并不知道是谁用的。
     * 而 {@code effect()} 是同步调用，所以在触发前后记录一下归属，
     * 好把炸死的敌机分数算给正确的玩家。
     */
    private HeroAircraft pendingItemOwner;

    private boolean gameOver = false;
    private boolean bossExists = false;
    private int lastScoreThreshold = 0;
    private int freezeRemainingFrames = 0;
    private double backgroundScroll = 0;
    private long frameIndex = 0;

    // ---------- 难度参数（子类在 initDifficultyParams 中设置） ----------
    protected int enemyMaxNumber = 5;
    protected double enemySpawnCycle = 20;
    protected double enemySpawnCounter = 0;
    protected double heroShootCycle = 10;
    protected double mobProbability = 0.6;
    protected double eliteProbability = 0.2;
    protected double elitePlusProbability = 0.1;
    protected double enemySpeedMultiplier = 1.0;
    protected double enemyHpMultiplier = 1.0;
    protected double enemyShootIntervalMultiplier = 1.0;
    protected int bossSpawnThreshold = 1000;

    protected GameWorld(Difficulty difficulty, int playerCount) {
        this.difficulty = difficulty;
        this.playerCount = Math.max(1, Math.min(2, playerCount));
        this.scores = new int[this.playerCount];
        this.progressionCounters = new int[this.playerCount];
        this.heroShootCounters = new double[this.playerCount];
        for (int i = 0; i < this.playerCount; i++) {
            HeroAircraft hero = new HeroAircraft(i);
            if (this.playerCount == 1) {
                hero.resetAt(0.5);
            } else {
                hero.resetAt(i == 0 ? 0.35 : 0.65);
            }
            heroes.add(hero);
            backpacks.add(new Stack<>());
        }
        initDifficultyParams();
    }

    /** 按难度初始化数值（模板方法，子类实现） */
    protected abstract void initDifficultyParams();

    /** 每 PROGRESSION_INTERVAL 帧调用一次，难度递增（模板方法，子类实现） */
    protected abstract void applyDifficultyProgression(int playerIndex);

    // ---------------- 生命周期 ----------------

    public void setListener(Listener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    /** 开始一局：注册事件观察者 */
    public void start() {
        BombSubject.getInstance().addDevice(this);
        FreezeSubject.getInstance().addDevice(this);
    }

    /** 结束一局：注销观察者 + 清空背包引用，避免跨局残留 */
    public void stop() {
        BombSubject.getInstance().removeDevice(this);
        FreezeSubject.getInstance().removeDevice(this);
    }

    // ---------------- 主循环 ----------------

    /**
     * 推进一个逻辑帧。
     * <p>
     * 顺序与 Windows 版 {@code Game.tick()} 完全一致，这是两个学期调出来的顺序，
     * 改动会导致碰撞或计分行为变化。
     */
    public void update() {
        if (gameOver) {
            return;
        }
        snapshotPositions();

        updateBoosts();
        updateFreezeFrames();
        checkBossSpawn();
        spawnEnemies();
        shootAction();
        bulletsMoveAction();
        aircraftMoveAction();
        itemsMoveAction();
        crashCheckAction();
        postProcessAction();
        scrollBackground();

        frameIndex++;
        if (isAnyHeroDead()) {
            finishGame();
        } else {
            progressionStep();
        }
    }

    private void snapshotPositions() {
        for (AbstractFlyingObject o : items) {
            o.snapshotPosition();
        }
        for (AbstractFlyingObject o : enemies) {
            o.snapshotPosition();
        }
        for (AbstractFlyingObject o : heroBullets) {
            o.snapshotPosition();
        }
        for (AbstractFlyingObject o : enemyBullets) {
            o.snapshotPosition();
        }
        for (AbstractFlyingObject o : heroes) {
            o.snapshotPosition();
        }
    }

    private void updateBoosts() {
        for (HeroAircraft hero : heroes) {
            hero.tickBoost();
        }
    }

    private void updateFreezeFrames() {
        if (freezeRemainingFrames > 0) {
            freezeRemainingFrames--;
        }
    }

    private void progressionStep() {
        for (int i = 0; i < playerCount; i++) {
            if (++progressionCounters[i] >= PROGRESSION_INTERVAL) {
                progressionCounters[i] = 0;
                applyDifficultyProgression(i);
            }
        }
    }

    // ---------------- Boss ----------------

    protected void checkBossSpawn() {
        int threshold = (totalScore() / bossSpawnThreshold) * bossSpawnThreshold;
        if (threshold > 0 && threshold != lastScoreThreshold && !bossExists) {
            BossEnemy boss = BossEnemy.create(getBossHp());
            enemies.add(boss);
            bossExists = true;
            lastScoreThreshold = threshold;
            onBossSpawned();
            listener.onBossSpawned(boss);
        }
    }

    /** 当前这一只 Boss 的血量（困难模式会递增，子类覆盖） */
    protected int getBossHp() {
        return 600;
    }

    protected void onBossSpawned() {
    }

    protected void onBossDefeated() {
    }

    // ---------------- 刷怪 ----------------

    private void spawnEnemies() {
        enemySpawnCounter++;
        if (enemySpawnCounter < enemySpawnCycle) {
            return;
        }
        enemySpawnCounter = 0;
        if (enemies.size() >= enemyMaxNumber) {
            return;
        }
        EnemyFactory factory = randomEnemyFactory();
        AbstractAircraft enemy = factory.createEnemy();
        if (freezeRemainingFrames > 0 && enemy instanceof AbstractEnemy) {
            ((AbstractEnemy) enemy).applyItemFreeze(freezeRemainingFrames, freezeRemainingFrames);
        }
        applyEnemyMultipliers(enemy);
        enemies.add(enemy);
    }

    private EnemyFactory randomEnemyFactory() {
        double rand = Math.random();
        double cumulative = mobProbability;
        if (rand < cumulative) {
            return new MobEnemyFactory();
        }
        cumulative += eliteProbability;
        if (rand < cumulative) {
            return new EliteEnemyFactory();
        }
        cumulative += elitePlusProbability;
        if (rand < cumulative) {
            return new ElitePlusEnemyFactory();
        }
        return new EliteProEnemyFactory();
    }

    private void applyEnemyMultipliers(AbstractAircraft enemy) {
        if (!(enemy instanceof AbstractEnemy)) {
            return;
        }
        AbstractEnemy e = (AbstractEnemy) enemy;
        if (!(enemy instanceof BossEnemy)) {
            e.applyDifficultyMultiplier(enemySpeedMultiplier, enemyHpMultiplier);
        }
        e.setShootIntervalMultiplier(enemyShootIntervalMultiplier);
    }

    protected double getEliteProbability() {
        return eliteProbability + elitePlusProbability;
    }

    // ---------------- 射击 ----------------

    private void shootAction() {
        // 每架英雄机用自己的射击计数器（Elo 会让两架射速不同），
        // 发射后给子弹回填 ownerIndex，用于把击杀分数记到正确的玩家头上
        for (int i = 0; i < heroes.size(); i++) {
            HeroAircraft hero = heroes.get(i);
            if (hero.notValid()) {
                continue;
            }
            heroShootCounters[i]++;
            if (heroShootCounters[i] >= getEffectiveHeroShootCycle(hero)) {
                heroShootCounters[i] = 0;
                List<BaseBullet> bullets = hero.shoot();
                for (BaseBullet bullet : bullets) {
                    bullet.setOwnerIndex(i);
                }
                heroBullets.addAll(bullets);
            }
        }
        for (AbstractAircraft enemy : enemies) {
            enemyBullets.addAll(enemy.shoot());
        }
    }

    /**
     * 英雄机当前的有效射击周期。
     * <p>
     * 困难模式的 Elo 机制会覆盖它（血量越低射得越快）。
     */
    protected double getEffectiveHeroShootCycle(HeroAircraft hero) {
        return heroShootCycle;
    }

    private void bulletsMoveAction() {
        for (BaseBullet bullet : heroBullets) {
            bullet.forward();
        }
        for (BaseBullet bullet : enemyBullets) {
            bullet.forward();
        }
    }

    private void aircraftMoveAction() {
        for (AbstractAircraft enemy : enemies) {
            enemy.forward();
        }
    }

    private void itemsMoveAction() {
        for (AbstractFlyingObject item : items) {
            item.forward();
        }
    }

    private void scrollBackground() {
        backgroundScroll += 1;
    }

    // ---------------- 碰撞 ----------------

    private void crashCheckAction() {
        // 1) 敌弹 vs 英雄机
        for (BaseBullet bullet : enemyBullets) {
            if (bullet.notValid()) {
                continue;
            }
            for (HeroAircraft hero : heroes) {
                if (hero.notValid()) {
                    continue;
                }
                if (hero.crash(bullet)) {
                    hero.decreaseHp(bullet.getPower());
                    bullet.vanish();
                    listener.onHeroDamaged(hero, bullet.getPower());
                    break;
                }
            }
        }

        // 2) 英雄弹 vs 敌机（命中即消耗，不穿透）
        for (BaseBullet bullet : heroBullets) {
            for (AbstractAircraft enemy : enemies) {
                if (bullet.notValid()) {
                    break;
                }
                if (enemy.notValid() || !(enemy instanceof AbstractEnemy)) {
                    continue;
                }
                if (enemy.crash(bullet)) {
                    enemy.decreaseHp(bullet.getPower());
                    bullet.vanish();
                    if (enemy.notValid()) {
                        HeroAircraft killer = heroOf(bullet.getOwnerIndex());
                        addScore(killer, ((AbstractEnemy) enemy).getScore());
                        listener.onEnemyKilled((AbstractEnemy) enemy, killer);
                    }
                }
            }
        }

        // 3) 英雄机撞机（独立于子弹列表）
        for (HeroAircraft hero : heroes) {
            if (hero.notValid()) {
                continue;
            }
            for (AbstractAircraft enemy : enemies) {
                if (enemy.notValid()) {
                    continue;
                }
                if (hero.crash(enemy) || enemy.crash(hero)) {
                    enemy.vanish();
                    hero.decreaseHp(Integer.MAX_VALUE);
                    listener.onHeroDamaged(hero, Integer.MAX_VALUE);
                }
            }
        }

        // 4) 英雄机拾取道具（双人时谁先碰到归谁）
        for (AbstractFlyingObject item : items) {
            if (item.notValid()) {
                continue;
            }
            for (int i = 0; i < heroes.size(); i++) {
                HeroAircraft hero = heroes.get(i);
                if (hero.notValid()) {
                    continue;
                }
                if (hero.crash(item)) {
                    Item it = (Item) item;
                    if (item instanceof BombItem || item instanceof FreezeItem) {
                        // 立即生效型道具：记录归属后再触发，全局事件里才知道分数算给谁
                        pendingItemOwner = hero;
                        try {
                            it.effect(hero);
                        } finally {
                            pendingItemOwner = null;
                        }
                    } else {
                        addToBackpack(i, it);
                    }
                    item.vanish();
                    listener.onItemPicked(it, hero);
                    break;
                }
            }
        }
    }

    /** 按玩家编号取英雄机；越界返回第一架（单人模式恒为 0） */
    private HeroAircraft heroOf(int playerIndex) {
        if (heroes.isEmpty()) {
            return null;
        }
        return heroes.get(Math.max(0, Math.min(heroes.size() - 1, playerIndex)));
    }

    // ---------------- 清理与掉落 ----------------

    private void postProcessAction() {
        enemyBullets.removeIf(AbstractFlyingObject::notValid);
        heroBullets.removeIf(AbstractFlyingObject::notValid);

        Iterator<AbstractAircraft> it = enemies.iterator();
        while (it.hasNext()) {
            AbstractAircraft aircraft = it.next();
            if (!aircraft.notValid()) {
                continue;
            }
            it.remove();
            if (!(aircraft instanceof AbstractEnemy)) {
                continue;
            }
            AbstractEnemy enemy = (AbstractEnemy) aircraft;
            if (enemy instanceof BossEnemy) {
                bossExists = false;
                onBossDefeated();
                listener.onBossDefeated();
            }
            dropItems(enemy);
        }
        items.removeIf(AbstractFlyingObject::notValid);
    }

    /** 敌机死亡掉落：数量由 {@link AbstractEnemy#dropCount()} 决定 */
    private void dropItems(AbstractEnemy enemy) {
        int count = enemy.dropCount();
        if (count <= 0) {
            return;
        }
        if (enemy instanceof BossEnemy) {
            int offset = 40;
            for (int i = 0; i < count; i++) {
                int x = enemy.getLocationX() + (i - (count - 1) / 2) * offset;
                items.add(ItemFactory.getItem(x, enemy.getLocationY(), Math.random()));
            }
        } else {
            AbstractItem item = ItemFactory.createItemForEnemy(
                    enemy, enemy.getLocationX(), enemy.getLocationY());
            if (item != null) {
                items.add(item);
            }
            enemy.markDropped();
        }
    }

    // ---------------- 事件响应（观察者） ----------------

    @Override
    public void onBombDetonated() {
        for (AbstractAircraft aircraft : enemies) {
            if (aircraft.notValid() || !(aircraft instanceof AbstractEnemy)) {
                continue;
            }
            ((AbstractEnemy) aircraft).setKilledByBomb(true);
        }

        for (AbstractAircraft aircraft : enemies) {
            if (aircraft.notValid() || !(aircraft instanceof AbstractEnemy)) {
                continue;
            }
            AbstractEnemy enemy = (AbstractEnemy) aircraft;
            if (enemy instanceof BossEnemy || enemy instanceof edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy) {
                // Boss 与 ElitePro 扛得住清屏，只吃固定伤害
                enemy.decreaseHp(BOMB_DAMAGE);
                if (enemy.notValid()) {
                    addScore(pendingItemOwner, enemy.getScore());
                }
            } else {
                addScore(pendingItemOwner, enemy.getScore());
                enemy.vanish();
            }
        }
        enemyBullets.clear();
        listener.onBombDetonated();
    }

    @Override
    public void onFreezeActivated() {
        freezeRemainingFrames = FREEZE_DURATION;
        for (AbstractAircraft aircraft : enemies) {
            if (aircraft instanceof AbstractEnemy) {
                ((AbstractEnemy) aircraft).applyItemFreeze(FREEZE_DURATION, SLOW_DURATION);
            }
        }
        listener.onFreezeActivated();
    }

    // ---------------- 背包 ----------------

    private void addToBackpack(int playerIndex, Item item) {
        Stack<Item> backpack = backpacks.get(playerIndex);
        if (backpack.size() < BACKPACK_CAPACITY) {
            backpack.push(item);
        }
        // 满了就丢弃，与原版行为一致
    }

    /**
     * 使用背包道具（栈顶 = 最后拾取的那个）。
     *
     * @return 实际使用的道具；背包为空返回 null
     */
    public Item useBackpackItem(int playerIndex) {
        Stack<Item> backpack = backpacks.get(playerIndex);
        if (backpack.isEmpty()) {
            return null;
        }
        Item item = backpack.pop();
        item.effect(heroes.get(playerIndex));
        return item;
    }

    // ---------------- 计分 ----------------

    private void addScore(HeroAircraft hero, int points) {
        int index = hero == null ? 0 : heroes.indexOf(hero);
        if (index < 0) {
            index = 0;
        }
        scores[index] += points;
    }

    public int getScore(int playerIndex) {
        return scores[Math.max(0, Math.min(scores.length - 1, playerIndex))];
    }

    /** 队伍总分（单人模式即个人分数） */
    public int totalScore() {
        int sum = 0;
        for (int s : scores) {
            sum += s;
        }
        return sum;
    }

    private boolean isAnyHeroDead() {
        for (HeroAircraft hero : heroes) {
            if (hero.getHp() <= 0) {
                return true;
            }
        }
        return false;
    }

    private void finishGame() {
        gameOver = true;
        listener.onGameOver();
    }

    // ---------------- 只读访问（渲染层使用） ----------------

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public List<HeroAircraft> getHeroes() {
        return Collections.unmodifiableList(heroes);
    }

    public HeroAircraft getHero(int playerIndex) {
        return heroes.get(Math.max(0, Math.min(heroes.size() - 1, playerIndex)));
    }

    public List<AbstractItem> getItems() {
        return items;
    }

    public List<AbstractAircraft> getEnemies() {
        return enemies;
    }

    public List<BaseBullet> getHeroBullets() {
        return heroBullets;
    }

    public List<BaseBullet> getEnemyBullets() {
        return enemyBullets;
    }

    /** 只读视图：渲染层可以遍历，但不该直接改背包 */
    public List<Item> getBackpack(int playerIndex) {
        return Collections.unmodifiableList(backpacks.get(playerIndex));
    }

    public int getFreezeRemainingFrames() {
        return freezeRemainingFrames;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isBossExists() {
        return bossExists;
    }

    public double getBackgroundScroll() {
        return backgroundScroll;
    }

    public long getFrameIndex() {
        return frameIndex;
    }

    /** 供测试注入固定随机源时使用 */
    public void resetForTest() {
        enemies.clear();
        items.clear();
        heroBullets.clear();
        enemyBullets.clear();
    }
}
