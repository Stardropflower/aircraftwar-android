package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.AbstractEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.ElitePlusEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy;

/**
 * 道具工厂（简单工厂模式）。
 * <p>
 * 掉落概率与 Windows 版一致，属于调过的手感数值，迁移时未做改动。
 */
public final class ItemFactory {

    private ItemFactory() {
    }

    /**
     * 按敌机类型决定掉落什么道具。
     *
     * @return 掉落物；普通 Mob 不掉落，返回 null
     */
    public static AbstractItem createItemForEnemy(AbstractEnemy enemy, int x, int y) {
        double rand = Math.random();

        if (enemy instanceof EliteProEnemy) {
            return getItem(x, y, rand);
        }
        if (enemy instanceof EliteEnemy || enemy instanceof ElitePlusEnemy) {
            if (rand < 0.25) {
                return BloodItem.create(x, y);
            } else if (rand < 0.5) {
                return BombItem.create(x, y);
            } else if (rand < 0.75) {
                return FireItem.create(x, y);
            } else {
                return SuperFireItem.create(x, y);
            }
        }
        if (enemy instanceof BossEnemy) {
            return getItem(x, y, rand);
        }
        return null;
    }

    /** 全类型等概率抽取（Boss 与 ElitePro 使用） */
    public static AbstractItem getItem(int x, int y, double rand) {
        if (rand < 0.2) {
            return BloodItem.create(x, y);
        } else if (rand < 0.4) {
            return BombItem.create(x, y);
        } else if (rand < 0.6) {
            return FireItem.create(x, y);
        } else if (rand < 0.8) {
            return SuperFireItem.create(x, y);
        } else {
            return FreezeItem.create(x, y);
        }
    }
}
