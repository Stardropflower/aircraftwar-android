package edu.hitsz.aircraftwar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.MobEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.bullet.HeroBullet;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.item.BloodItem;
import edu.hitsz.aircraftwar.core.item.BombItem;
import edu.hitsz.aircraftwar.core.item.FireItem;
import edu.hitsz.aircraftwar.core.item.Item;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.List;

/**
 * 核心逻辑层单元测试。
 * <p>
 * 这些测试**不需要 Android 环境**，直接在 JVM 上跑 —— 这正是把逻辑层与平台层解耦的目的。
 * 覆盖的是迁移过程中最容易出错、且最值得守护的规则：
 * 血量与火力、60fps 插值、碰撞因子、炸弹/冰冻、背包后进先出、击杀归属。
 */
public class CoreLogicTest {

    /** 测试用世界：关掉自动刷怪与自动射击，让测试能自行布置场景 */
    private static class TestWorld extends GameWorld {
        TestWorld() {
            this(1);
        }

        TestWorld(int playerCount) {
            super(Difficulty.NORMAL, playerCount);
        }

        @Override
        protected void initDifficultyParams() {
            enemySpawnCycle = 1e9;   // 不自动刷怪
            heroShootCycle = 1e9;    // 不自动射击
            bossSpawnThreshold = 1_000_000;
            mobProbability = 1.0;
            eliteProbability = 0.0;
            elitePlusProbability = 0.0;
        }

        @Override
        protected void applyDifficultyProgression(int playerIndex) {
        }

        @Override
        protected int getBossHp() {
            return 600;
        }
    }

    @Before
    public void setUp() {
        PlayField.resetToDefault();
    }

    // ==================== 英雄机 ====================

    @Test
    public void 血量不会超过上限且归零后失效() {
        HeroAircraft hero = new HeroAircraft();
        assertEquals(HeroAircraft.MAX_HP, hero.getHp());

        hero.decreaseHp(400);
        assertEquals(600, hero.getHp());

        hero.increaseHp(500);
        assertEquals(HeroAircraft.MAX_HP, hero.getHp());   // 封顶

        hero.decreaseHp(HeroAircraft.MAX_HP);
        assertEquals(0, hero.getHp());
        assertTrue("血量归零应标记为失效", hero.notValid());
    }

    @Test
    public void 火力加速按帧倒计时且结束后恢复玩家选择的模式() {
        HeroAircraft hero = new HeroAircraft();
        assertEquals(2, hero.getCurrentBulletCount());

        hero.setFireBoost(HeroAircraft.FireType.FIRE);
        assertEquals(3, hero.getCurrentBulletCount());
        assertEquals(HeroAircraft.BOOST_DURATION_FRAMES, hero.getBoostFramesRemaining());

        for (int i = 0; i < HeroAircraft.BOOST_DURATION_FRAMES; i++) {
            hero.tickBoost();
        }
        assertEquals(HeroAircraft.FireType.NONE, hero.getFireType());
        assertEquals(2, hero.getCurrentBulletCount());
    }

    @Test
    public void 加速结束不会清掉玩家自己选的分叉模式() {
        HeroAircraft hero = new HeroAircraft();
        hero.toggleShootStrategy();          // 玩家选择分叉
        assertTrue(hero.isSpreadMode());

        hero.setFireBoost(HeroAircraft.FireType.SUPER_FIRE);
        assertEquals(5, hero.getCurrentBulletCount());
        for (int i = 0; i < HeroAircraft.BOOST_DURATION_FRAMES; i++) {
            hero.tickBoost();
        }

        // 这是相对 Windows 版修掉的问题：原版加速结束会强制回直射，
        // 但 isSpreadMode 标记没复位，导致按 Q 第一次没有视觉变化
        assertTrue("加速结束后应保留玩家选择的分叉模式", hero.isSpreadMode());
    }

    @Test
    public void 直射与分叉的子弹数符合规则() {
        HeroAircraft hero = new HeroAircraft();

        List<BaseBullet> straight = hero.shoot();
        assertEquals("默认 2 发直射", 2, straight.size());

        hero.toggleShootStrategy();
        List<BaseBullet> spread = hero.shoot();
        assertEquals("分叉模式 2 发", 2, spread.size());
        // 分叉的子弹带横向速度
        boolean anyLateral = false;
        for (BaseBullet b : spread) {
            if (b.getSpeedX() != 0) {
                anyLateral = true;
            }
        }
        assertTrue("分叉子弹应有横向速度", anyLateral);
    }

    // ==================== 插值 ====================

    @Test
    public void 渲染插值在上一帧与当前帧之间() {
        MobEnemy enemy = new MobEnemy(100, 100, 10, 0, 20);
        enemy.snapshotPosition();          // 记录起点 100
        enemy.forward();                   // 推进到 110

        assertEquals(110, enemy.getLocationX());
        assertEquals("alpha=0 应画在上一帧位置", 100, enemy.renderX(0.0));
        assertEquals("alpha=0.5 应画在中点", 105, enemy.renderX(0.5));
        assertEquals("alpha=1 应画在当前帧位置", 110, enemy.renderX(1.0));
    }

    // ==================== 碰撞 ====================

    @Test
    public void 同位置必定碰撞_远离则不碰撞() {
        MobEnemy a = new MobEnemy(200, 200, 0, 0, 20);
        MobEnemy b = new MobEnemy(200, 200, 0, 0, 20);
        assertTrue(a.crash(b));

        MobEnemy far = new MobEnemy(500, 700, 0, 0, 20);
        assertFalse(a.crash(far));
    }

    @Test
    public void 子弹命中后立即消耗不再穿透() {
        TestWorld world = new TestWorld();

        MobEnemy front = new MobEnemy(200, 400, 0, 0, 20);
        MobEnemy behind = new MobEnemy(200, 400, 0, 0, 20);
        world.getEnemies().add(front);
        world.getEnemies().add(behind);

        HeroBullet bullet = new HeroBullet(200, 400, 0, -20, 30);
        world.getHeroBullets().add(bullet);

        world.update();

        assertTrue("子弹命中后应消失", bullet.notValid());
        assertNotSame("一发子弹不应同时打穿两架敌机",
                front.notValid(), behind.notValid());
    }

    // ==================== 炸弹 ====================

    @Test
    public void 炸弹对Boss造成一百伤害而不是秒杀() {
        TestWorld world = new TestWorld();
        BossEnemy boss = BossEnemy.create(600);
        world.getEnemies().add(boss);

        world.onBombDetonated();

        assertEquals(500, boss.getHp());
        assertFalse("Boss 不应被炸弹秒杀", boss.notValid());
    }

    @Test
    public void 炸弹对ElitePro造成一百伤害而不是秒杀() {
        TestWorld world = new TestWorld();
        EliteProEnemy pro = EliteProEnemy.create();   // 200 血
        world.getEnemies().add(pro);

        world.onBombDetonated();

        assertEquals(100, pro.getHp());
        assertFalse(pro.notValid());
    }

    @Test
    public void 炸弹清掉杂兵并加分() {
        TestWorld world = new TestWorld();
        MobEnemy mob = MobEnemy.create();
        world.getEnemies().add(mob);

        world.onBombDetonated();

        assertTrue("杂兵应被清掉", mob.notValid());
        assertEquals("杂兵 10 分", 10, world.getScore(0));
    }

    // ==================== 冰冻 ====================

    @Test
    public void 冰冻按敌机类型区分效果() {
        TestWorld world = new TestWorld();
        EliteEnemy elite = EliteEnemy.create();
        EliteProEnemy pro = EliteProEnemy.create();
        BossEnemy boss = BossEnemy.create(600);
        MobEnemy mob = MobEnemy.create();
        world.getEnemies().add(elite);
        world.getEnemies().add(pro);
        world.getEnemies().add(boss);
        world.getEnemies().add(mob);

        world.onFreezeActivated();

        assertEquals(GameWorld.FREEZE_DURATION, world.getFreezeRemainingFrames());
        assertTrue("Elite 应被冻结", elite.isFrozen());
        assertFalse("对 ElitePro 不冻结", pro.isFrozen());
        assertTrue("对 ElitePro 应减速", pro.isSlowed());
        assertFalse("Boss 免疫冻结", boss.isFrozen());
        assertFalse("Boss 免疫减速", boss.isSlowed());
        assertTrue("Mob 应被冻结", mob.isFrozen());
    }

    // ==================== 背包 ====================

    @Test
    public void 背包后进先出_先使用最后拾取的道具() {
        TestWorld world = new TestWorld();
        HeroAircraft hero = world.getHero(0);

        BloodItem blood = BloodItem.create(hero.getLocationX(), hero.getLocationY());
        world.getItems().add(blood);
        world.update();
        assertEquals(1, world.getBackpack(0).size());

        FireItem fire = FireItem.create(hero.getLocationX(), hero.getLocationY());
        world.getItems().add(fire);
        world.update();
        assertEquals(2, world.getBackpack(0).size());

        // 栈式：最后放入的 fire 应先被取出
        assertSame("应先使用最后拾取的燃料道具", fire, world.useBackpackItem(0));
        assertSame("再使用先拾取的血包", blood, world.useBackpackItem(0));
        assertNull("用完后返回 null", world.useBackpackItem(0));
    }

    @Test
    public void 背包容量满后不再拾取() {
        TestWorld world = new TestWorld();
        HeroAircraft hero = world.getHero(0);

        for (int i = 0; i < GameWorld.BACKPACK_CAPACITY; i++) {
            world.getItems().add(BloodItem.create(hero.getLocationX(), hero.getLocationY()));
            world.update();
        }
        assertEquals(GameWorld.BACKPACK_CAPACITY, world.getBackpack(0).size());

        world.getItems().add(BloodItem.create(hero.getLocationX(), hero.getLocationY()));
        world.update();
        assertEquals("超出容量应丢弃", GameWorld.BACKPACK_CAPACITY, world.getBackpack(0).size());
    }

    @Test
    public void 炸弹与冰冻不进入背包而是立即生效() {
        TestWorld world = new TestWorld();
        HeroAircraft hero = world.getHero(0);

        world.getItems().add(BombItem.create(hero.getLocationX(), hero.getLocationY()));
        world.update();

        assertEquals("炸弹不应进背包", 0, world.getBackpack(0).size());
    }

    // ==================== 双人 ====================

    @Test
    public void 双人模式下有两架独立英雄机() {
        TestWorld world = new TestWorld(2);
        assertEquals(2, world.getHeroes().size());
        assertNotSame(world.getHero(0), world.getHero(1));

        // 血量独立
        world.getHero(0).decreaseHp(300);
        assertEquals(700, world.getHero(0).getHp());
        assertEquals(HeroAircraft.MAX_HP, world.getHero(1).getHp());
    }

    @Test
    public void 双人击杀分数记在开枪的那名玩家头上() {
        TestWorld world = new TestWorld(2);
        MobEnemy target = new MobEnemy(200, 400, 0, 0, 20);
        world.getEnemies().add(target);

        HeroBullet bullet = new HeroBullet(200, 400, 0, -20, 30);
        bullet.setOwnerIndex(1);           // P2 开的枪
        world.getHeroBullets().add(bullet);
        world.update();

        assertEquals("P1 不应得分", 0, world.getScore(0));
        assertEquals("P2 应得 10 分", 10, world.getScore(1));
    }

    // ==================== 掉落 ====================

    @Test
    public void Boss死亡掉落三个道具() {
        TestWorld world = new TestWorld();
        BossEnemy boss = BossEnemy.create(600);
        world.getEnemies().add(boss);

        boss.decreaseHp(600);              // 直接打死
        assertTrue(boss.notValid());
        world.update();                    // 清理阶段处理掉落

        assertEquals("Boss 应掉 3 个道具", BossEnemy.DROP_COUNT, world.getItems().size());
    }

    @Test
    public void 被炸弹炸死的敌机不掉落道具() {
        TestWorld world = new TestWorld();
        // EliteEnemy 有 30% 掉率，用 killedByBomb 强制抑制
        EliteEnemy elite = EliteEnemy.create();
        world.getEnemies().add(elite);

        world.onBombDetonated();           // 标记 killedByBomb 并清屏
        assertTrue(elite.notValid());
        world.update();

        assertEquals("被炸死的敌机不应掉落", 0, world.getItems().size());
    }

    // ==================== 边界 ====================

    @Test
    public void 敌机飘出屏幕底部会消失() {
        MobEnemy enemy = new MobEnemy(100, PlayField.height() - 1, 0, 50, 20);
        enemy.forward();
        assertTrue("出界敌机应失效", enemy.notValid());
    }

    @Test
    public void 子弹飞出上边界会消失() {
        HeroBullet bullet = new HeroBullet(100, 5, 0, -20, 30);
        bullet.forward();
        assertTrue(bullet.notValid());
    }

    @Test
    public void 战场尺寸可配置且影响边界判定() {
        PlayField.configure(800, 1200);
        assertEquals(800, PlayField.width());
        assertEquals(1200, PlayField.height());

        MobEnemy enemy = new MobEnemy(100, 1190, 0, 50, 20);
        enemy.forward();
        assertTrue("按新高度判定的越界", enemy.notValid());

        PlayField.resetToDefault();
    }

    // ==================== 游戏结束 ====================

    @Test
    public void 血量归零后游戏结束且回调触发() {
        TestWorld world = new TestWorld();
        final boolean[] over = {false};
        world.setListener(new GameWorld.Listener() {
            @Override
            public void onGameOver() {
                over[0] = true;
            }
        });

        world.getHero(0).decreaseHp(HeroAircraft.MAX_HP);
        world.update();

        assertTrue(world.isGameOver());
        assertTrue("应回调 onGameOver", over[0]);
    }
}
