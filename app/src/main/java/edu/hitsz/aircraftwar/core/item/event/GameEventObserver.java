package edu.hitsz.aircraftwar.core.item.event;

/**
 * 游戏事件观察者（观察者模式）。
 * <p>
 * 炸弹 / 冰冻这类「一次性作用于全场」的道具，通过事件广播解耦：
 * 道具只负责「发出事件」，不去关心战场如何响应。
 */
public interface GameEventObserver {

    /** 炸弹引爆：清屏 + 清空敌弹 */
    void onBombDetonated();

    /** 冰冻生效：全场敌机进入冻结 / 减速 */
    void onFreezeActivated();
}
