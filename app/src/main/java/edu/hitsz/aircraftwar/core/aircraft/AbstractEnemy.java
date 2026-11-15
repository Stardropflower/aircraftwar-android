package edu.hitsz.aircraftwar.core.aircraft;

import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.ElitePlusEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy;
import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.strategy.ShootContext;
import edu.hitsz.aircraftwar.core.strategy.ShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.Collections;
import java.util.List;

/**
 * 敌机基类：冻结 / 减速状态、射击冷却、难度倍率、掉落判定。
 * <p>
 * 与 Windows 版的关键差别：<b>{@code vanish()} 不再自己创建道具</b>。
 * 原版在 {@code vanish()} 里回调 {@code Game.getInstance()} 往战场里塞道具，
 * 这让「实体」反向依赖了「游戏面板」这个 UI 类。现在改为把「掉不掉、掉几个」
 * 暴露成 {@link #dropCount()}，由 {@code GameWorld} 在清理阶段统一生成。
 */
public abstract class AbstractEnemy extends AbstractAircraft {

    private int frozenRemainingFrames = 0;
    private int slowRemainingFrames = 0;
    private int originalSpeedX;
    protected int originalSpeedY;

    private int shootCooldown = 0;
    protected boolean killedByBomb = false;
    private double shootIntervalMultiplier = 1.0;

    protected ShootContext shootContext;

    protected AbstractEnemy(int locationX, int locationY, int speedX, int speedY, int hp) {
        super(locationX, locationY, speedX, speedY, hp);
        this.originalSpeedX = speedX;
        this.originalSpeedY = speedY;
    }

    public void setShootStrategy(ShootStrategy strategy) {
        if (shootContext == null) {
            shootContext = new ShootContext(strategy);
        } else {
            shootContext.setStrategy(strategy);
        }
    }

    public void setKilledByBomb(boolean killedByBomb) {
        this.killedByBomb = killedByBomb;
    }

    public boolean isKilledByBomb() {
        return killedByBomb;
    }

    public void setShootIntervalMultiplier(double multiplier) {
        this.shootIntervalMultiplier = multiplier;
    }

    /** 难度递增时对速度与血量做整体放大 */
    public void applyDifficultyMultiplier(double speedMultiplier, double hpMultiplier) {
        this.speedX = (int) (this.speedX * speedMultiplier);
        this.speedY = (int) (this.speedY * speedMultiplier);
        this.originalSpeedX = this.speedX;
        this.originalSpeedY = this.speedY;
        this.hp = (int) (this.hp * hpMultiplier);
        this.maxHp = this.hp;
    }

    @Override
    public void forward() {
        // 冻结：完全不动，但仍然消耗帧数
        if (frozenRemainingFrames > 0) {
            frozenRemainingFrames--;
            return;
        }
        if (slowRemainingFrames > 0) {
            this.speedX = originalSpeedX / 2;
            this.speedY = originalSpeedY / 2;
            slowRemainingFrames--;
        } else {
            this.speedX = originalSpeedX;
            this.speedY = originalSpeedY;
        }

        int oldSpeedX = this.speedX;
        int oldSpeedY = this.speedY;
        super.forward();
        // 撞到左右边界后速度方向会翻转，记得把基准也更新，否则减速恢复时会跳回去
        if (this.speedX != oldSpeedX) {
            originalSpeedX = this.speedX;
        }
        if (this.speedY != oldSpeedY) {
            originalSpeedY = this.speedY;
        }

        if (locationY >= PlayField.height()) {
            vanish();
        }
    }

    @Override
    public List<BaseBullet> shoot() {
        if (frozenRemainingFrames > 0) {
            return Collections.emptyList();
        }
        if (shootCooldown > 0) {
            shootCooldown--;
            return Collections.emptyList();
        }
        List<BaseBullet> bullets = shootContext != null
                ? shootContext.executeShoot(this)
                : doShoot();
        shootCooldown = (int) (getShootInterval() * shootIntervalMultiplier);
        return bullets;
    }

    /** 子类可覆盖；默认走策略（若已设置策略则本方法不会被调用） */
    protected abstract List<BaseBullet> doShoot();

    protected int getShootInterval() {
        return 30;
    }

    // ---------------- 掉落 ----------------

    /**
     * 死亡时掉落几个道具。
     * <p>
     * 由 {@code GameWorld} 在清理阶段调用；被炸弹炸死的不掉（{@code killedByBomb}）。
     */
    public int dropCount() {
        // 被炸弹炸死的敌机不应当再掉落道具
        if (killedByBomb) {
            return 0;
        }
        return shouldDropItem() ? 1 : 0;
    }

    /** 是否掉落（子类按概率实现） */
    public boolean shouldDropItem() {
        return false;
    }

    public void markDropped() {
        // 默认无状态，子类维护「是否已掉过」
    }

    // ---------------- 冰冻 / 减速 ----------------

    public void freeze(int frames) {
        this.frozenRemainingFrames = frames;
    }

    public void slow(int frames) {
        this.slowRemainingFrames = frames;
    }

    public boolean isFrozen() {
        return frozenRemainingFrames > 0;
    }

    public boolean isSlowed() {
        return slowRemainingFrames > 0;
    }

    /**
     * 按敌机类型施加冰冻效果（原版规则，逐条保留）：
     * <ul>
     *   <li>MVP 级（Mob）：永久冻结</li>
     *   <li>Elite / ElitePlus：冻结 6 秒</li>
     *   <li>ElitePro：只减速 6 秒</li>
     *   <li>Boss：免疫</li>
     * </ul>
     */
    public void applyItemFreeze(int freezeDuration, int slowDuration) {
        if (this instanceof EliteProEnemy) {
            slow(slowDuration);
        } else if (!(this instanceof edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy)) {
            if (this instanceof ElitePlusEnemy || this instanceof EliteEnemy) {
                freeze(freezeDuration);
            } else {
                // Mob：Integer.MAX_VALUE 帧 ≈ 永久
                freeze(Integer.MAX_VALUE);
            }
        }
    }

    public int getScore() {
        return 10;
    }
}
