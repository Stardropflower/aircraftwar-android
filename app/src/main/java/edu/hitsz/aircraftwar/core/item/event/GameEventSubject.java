package edu.hitsz.aircraftwar.core.item.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏事件主体基类。
 * <p>
 * 事件主体（炸弹 / 冰冻）是全局限定的单例，而每个 {@code GameWorld} 开局注册、结束时注销。
 * 这里必须做到两点，否则跨局会出问题：
 * <ul>
 *   <li><b>同一观察者只注册一次</b>：漏注销时旧的一局会一直被单例引用着，
 *       而且下一局扔炸弹会把已经结束的旧局也通知一遍。</li>
 *   <li><b>遍历期间允许写入</b>：用 {@link CopyOnWriteArrayList}，通知时遍历的是快照。</li>
 * </ul>
 */
public abstract class GameEventSubject {

    protected final List<GameEventObserver> devices = new CopyOnWriteArrayList<>();

    public synchronized void addDevice(GameEventObserver device) {
        if (device != null && !devices.contains(device)) {
            devices.add(device);
        }
    }

    public synchronized void removeDevice(GameEventObserver device) {
        devices.remove(device);
    }

    /** 清空所有观察者（游戏结束时兜底调用） */
    public synchronized void clearDevices() {
        devices.clear();
    }

    public abstract void notifyDevices();

    public abstract void trigger();
}
