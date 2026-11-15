package edu.hitsz.aircraftwar.core.aircraft;

import edu.hitsz.aircraftwar.core.bullet.BaseBullet;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.strategy.RoundShootStrategy;
import edu.hitsz.aircraftwar.core.strategy.ShootContext;
import edu.hitsz.aircraftwar.core.strategy.SpreadShootStrategy;
import edu.hitsz.aircraftwar.core.strategy.StraightShootStrategy;
import edu.hitsz.aircraftwar.core.world.PlayField;

import java.util.List;

/**
 * 英雄机。
 * <p>
 * 与 Windows 版相比有三处调整（都是平台化 / 可测性的需要，玩法不变）：
 * <ol>
 *   <li><b>不再是单例</b>：双人合作模式需要两架英雄机同时存在，改为普通可实例化对象，
 *       用 {@code playerSlot} 区分 P1 / P2。</li>
 *   <li><b>火力加速改为按帧倒计时</b>：原版用后台线程 sleep + {@code SwingUtilities.invokeLater}
 *       回主线程，Android 上没有 EDT。改成 {@link #tickBoost()} 每逻辑帧递减，
 *       150 帧 × 40ms = 6 秒，与原来一致，而且是确定性的（不受设备速度影响），也便于单测。</li>
 *   <li><b>皮肤不再持有图片</b>：{@link Skin} 只保留名称与解锁条件，图片由渲染层按枚举映射。</li>
 * </ol>
 */
public class HeroAircraft extends AbstractAircraft {

    /** 满血值 */
    public static final int MAX_HP = 1000;
    /** 初始火力 */
    public static final int BASE_POWER = 30;
    /** 初始单次射击子弹数 */
    public static final int BASE_BULLET_NUM = 2;
    /** 火力加速持续帧数：150 帧 × 40ms = 6 秒（与原版 6000ms 等价） */
    public static final int BOOST_DURATION_FRAMES = 150;

    /**
     * 皮肤。
     * <p>
     * 解锁条件沿用 Windows 版数值（依据本机历史最高分，与云端榜单无关）。
     * 声明顺序即皮肤选择界面的浏览顺序，按难度分组排列。
     */
    public enum Skin {
        HERO("水蓝", null, 0),
        SILVER("银翼", Difficulty.EASY, 1500),
        JADE("翡翠", Difficulty.EASY, 3000),
        GOLDEN("帝皇", Difficulty.NORMAL, 3000),
        CRIMSON("赤霄", Difficulty.NORMAL, 6000),
        MANBA("曼巴", Difficulty.HARD, 3000),
        OBSIDIAN("曜黑", Difficulty.HARD, 6000);

        private final String displayName;
        private final Difficulty unlockDifficulty;
        private final int unlockScore;

        Skin(String displayName, Difficulty unlockDifficulty, int unlockScore) {
            this.displayName = displayName;
            this.unlockDifficulty = unlockDifficulty;
            this.unlockScore = unlockScore;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Difficulty getUnlockDifficulty() {
            return unlockDifficulty;
        }

        public int getUnlockScore() {
            return unlockScore;
        }

        public boolean isDefaultUnlocked() {
            return unlockDifficulty == null;
        }
    }

    /** 火力档位 */
    public enum FireType {
        NONE, FIRE, SUPER_FIRE
    }

    private final int playerSlot;
    private int power = BASE_POWER;
    private boolean spreadMode = false;
    private Skin skin = Skin.HERO;
    private FireType fireType = FireType.NONE;
    private int boostFramesRemaining = 0;
    private final ShootContext shootContext;

    public HeroAircraft() {
        this(0);
    }

    public HeroAircraft(int playerSlot) {
        super(PlayField.width() / 2, PlayField.height() - 80, 0, 0, MAX_HP);
        this.playerSlot = playerSlot;
        this.shootContext = new ShootContext(new StraightShootStrategy());
        reset();
    }

    public int getPlayerSlot() {
        return playerSlot;
    }

    // ---------------- 射击策略 ----------------

    /** 切换直射 / 分叉（对应原版 Q 键；Android 上由界面按钮触发） */
    public void toggleShootStrategy() {
        spreadMode = !spreadMode;
        applyPlayerStrategy();
    }

    public void setSpreadMode(boolean spread) {
        this.spreadMode = spread;
        applyPlayerStrategy();
    }

    public boolean isSpreadMode() {
        return spreadMode;
    }

    private void applyPlayerStrategy() {
        shootContext.setStrategy(spreadMode ? new SpreadShootStrategy() : new StraightShootStrategy());
    }

    /** 当前单次射击子弹数（火力档位决定） */
    public int getCurrentBulletCount() {
        if (fireType == FireType.SUPER_FIRE) {
            return 5;
        }
        if (fireType == FireType.FIRE) {
            return 3;
        }
        return BASE_BULLET_NUM;
    }

    @Override
    public int getPower() {
        return power;
    }

    @Override
    public List<BaseBullet> shoot() {
        return shootContext.executeShoot(this);
    }

    // ---------------- 火力加速 ----------------

    public void setFireBoost(FireType type) {
        if (type == FireType.NONE) {
            return;
        }
        fireType = type;
        boostFramesRemaining = BOOST_DURATION_FRAMES;
        shootContext.setStrategy(type == FireType.SUPER_FIRE
                ? new RoundShootStrategy()
                : new SpreadShootStrategy());
    }

    /**
     * 每逻辑帧调用一次；倒计时归零后恢复成「玩家自己选的」射击模式。
     * <p>
     * 原版加速结束时无条件恢复直射，但 {@code isSpreadMode} 标记没跟着复位，
     * 导致加速结束后按 Q 第一次没有视觉效果。这里按玩家选择的模式恢复，顺带修掉这个不一致。
     */
    public void tickBoost() {
        if (fireType == FireType.NONE) {
            return;
        }
        if (--boostFramesRemaining > 0) {
            return;
        }
        fireType = FireType.NONE;
        boostFramesRemaining = 0;
        applyPlayerStrategy();
    }

    public FireType getFireType() {
        return fireType;
    }

    public int getBoostFramesRemaining() {
        return boostFramesRemaining;
    }

    // ---------------- 数值调整 ----------------

    public void increaseHp(int increase) {
        hp += increase;
        if (hp > maxHp) {
            hp = maxHp;
        }
    }

    public void increasePower(int amount) {
        power += amount;
    }

    // ---------------- 皮肤 ----------------

    public Skin getSkin() {
        return skin;
    }

    public void setSkin(Skin skin) {
        if (skin != null) {
            this.skin = skin;
        }
    }

    // ---------------- 生命周期 ----------------

    /** 复位到初始状态并居中 */
    public void reset() {
        resetAt(0.5);
    }

    /**
     * 复位并指定水平位置比例。
     * @param xRatio 0~1，双人合作时两架飞机分别用 0.35 / 0.65
     */
    public void resetAt(double xRatio) {
        hp = maxHp;
        power = BASE_POWER;
        isValid = true;
        fireType = FireType.NONE;
        boostFramesRemaining = 0;
        spreadMode = false;
        applyPlayerStrategy();
        locationX = (int) (PlayField.width() * xRatio);
        locationY = PlayField.height() - getHeight();
        prevLocationX = locationX;
        prevLocationY = locationY;
    }

    /** 英雄机不受 forward 驱动（位置由输入控制） */
    @Override
    public void forward() {
        // 交给输入层直接设置坐标
    }
}
