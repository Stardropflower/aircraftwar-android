package edu.hitsz.aircraftwar.core.item.event;

/** 冰冻事件主体 */
public class FreezeSubject extends GameEventSubject {

    private static final FreezeSubject INSTANCE = new FreezeSubject();

    private FreezeSubject() {
    }

    public static FreezeSubject getInstance() {
        return INSTANCE;
    }

    @Override
    public void notifyDevices() {
        for (GameEventObserver device : devices) {
            device.onFreezeActivated();
        }
    }

    @Override
    public void trigger() {
        notifyDevices();
    }
}
