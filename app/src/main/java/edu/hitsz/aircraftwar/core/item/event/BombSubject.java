package edu.hitsz.aircraftwar.core.item.event;

/** 炸弹事件主体 */
public class BombSubject extends GameEventSubject {

    private static final BombSubject INSTANCE = new BombSubject();

    private BombSubject() {
    }

    public static BombSubject getInstance() {
        return INSTANCE;
    }

    @Override
    public void notifyDevices() {
        for (GameEventObserver device : devices) {
            device.onBombDetonated();
        }
    }

    @Override
    public void trigger() {
        notifyDevices();
    }
}
