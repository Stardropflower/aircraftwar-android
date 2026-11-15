package edu.hitsz.aircraftwar.core.item;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;

/**
 * 道具接口。
 * <p>
 * 与 Windows 版相比去掉了 {@code getImage()}：图像由渲染层按道具类型映射，核心层不碰图像类型。
 */
public interface Item {

    /** 对拾取它的英雄机生效（双人模式下就是拾取的那一架） */
    void effect(HeroAircraft heroAircraft);

    int getLocationX();

    int getLocationY();

    void forward();

    void vanish();

    boolean notValid();
}
