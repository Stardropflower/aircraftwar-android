package edu.hitsz.aircraftwar.render;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.aircraft.enemy.BossEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.ElitePlusEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.EliteProEnemy;
import edu.hitsz.aircraftwar.core.aircraft.enemy.MobEnemy;
import edu.hitsz.aircraftwar.core.bullet.EnemyBullet;
import edu.hitsz.aircraftwar.core.bullet.HeroBullet;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.core.item.BloodItem;
import edu.hitsz.aircraftwar.core.item.BombItem;
import edu.hitsz.aircraftwar.core.item.FireItem;
import edu.hitsz.aircraftwar.core.item.FreezeItem;
import edu.hitsz.aircraftwar.core.item.SuperFireItem;
import edu.hitsz.aircraftwar.core.sprite.SpriteMetrics;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源仓库：把 assets 里的图片解码成 {@link Bitmap}，并承担「实体 → 位图」的映射。
 * <p>
 * 核心层不持有任何图像（它只有 {@code SpriteMetrics} 拿到的宽高），
 * 图像完全由这一层负责 —— 这是把逻辑与平台解耦的直接收益：
 * 核心层能在 JVM 上单测，渲染层则独占 Android 的图像 API。
 * <p>
 * 映射用 {@code instanceof} 而不是原版的「类名 → 图片」字符串表：
 * Android 上 R8 可能混淆类名，靠字符串匹配在 release 包里会静默失效。
 */
public final class Assets implements SpriteMetrics {

    private final Map<String, Bitmap> images = new HashMap<>();
    private final Map<HeroAircraft.Skin, Bitmap> heroSkins = new EnumMap<>(HeroAircraft.Skin.class);
    private final Map<Difficulty, Bitmap> backgrounds = new EnumMap<>(Difficulty.class);

    private Assets() {
    }

    public static Assets load(Context context) {
        Assets assets = new Assets();
        AssetManager am = context.getAssets();
        // 实体图
        assets.put(am, "mob", "images/mob.png");
        assets.put(am, "elite", "images/elite.png");
        assets.put(am, "elitePlus", "images/elitePlus.png");
        assets.put(am, "elitePro", "images/elitePro.png");
        assets.put(am, "boss", "images/boss.png");
        assets.put(am, "bullet_hero", "images/bullet_hero.png");
        assets.put(am, "bullet_enemy", "images/bullet_enemy.png");
        assets.put(am, "prop_blood", "images/prop_blood.png");
        assets.put(am, "prop_bomb", "images/prop_bomb.png");
        assets.put(am, "prop_bullet", "images/prop_bullet.png");
        assets.put(am, "prop_bulletPlus", "images/prop_bulletPlus.png");
        assets.put(am, "prop_freeze", "images/prop_freeze.png");

        // 七款皮肤
        assets.put(am, "hero_skin", "images/hero.png");
        assets.put(am, "plane_silver", "images/plane_silver.png");
        assets.put(am, "plane_jade", "images/plane_jade.png");
        assets.put(am, "plane_golden", "images/plane_golden.png");
        assets.put(am, "plane_crimson", "images/plane_crimson.png");
        assets.put(am, "plane_manba", "images/plane_manba.png");
        assets.put(am, "plane_obsidian", "images/plane_obsidian.png");

        assets.heroSkins.put(HeroAircraft.Skin.HERO, assets.get("hero_skin"));
        assets.heroSkins.put(HeroAircraft.Skin.SILVER, assets.get("plane_silver"));
        assets.heroSkins.put(HeroAircraft.Skin.JADE, assets.get("plane_jade"));
        assets.heroSkins.put(HeroAircraft.Skin.GOLDEN, assets.get("plane_golden"));
        assets.heroSkins.put(HeroAircraft.Skin.CRIMSON, assets.get("plane_crimson"));
        assets.heroSkins.put(HeroAircraft.Skin.MANBA, assets.get("plane_manba"));
        assets.heroSkins.put(HeroAircraft.Skin.OBSIDIAN, assets.get("plane_obsidian"));

        // 三档难度的背景（沿用 Windows 版的对应关系）
        assets.backgrounds.put(Difficulty.EASY, assets.loadOrNull(am, "images/bg3.jpg"));
        assets.backgrounds.put(Difficulty.NORMAL, assets.loadOrNull(am, "images/bg.jpg"));
        assets.backgrounds.put(Difficulty.HARD, assets.loadOrNull(am, "images/bg4.jpg"));

        assets.validate();
        return assets;
    }

    private void put(AssetManager am, String key, String path) {
        Bitmap bmp = loadOrNull(am, path);
        if (bmp != null) {
            images.put(key, bmp);
        }
    }

    private Bitmap loadOrNull(AssetManager am, String path) {
        try (InputStream in = am.open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            System.err.println("资源加载失败: " + path + " -> " + e.getMessage());
            return null;
        }
    }

    private Bitmap get(String key) {
        return images.get(key);
    }

    /** 启动时校验关键资源，缺失时尽早暴露而不是运行到一半才崩 */
    private void validate() {
        String[] required = {"mob", "elite", "elitePlus", "elitePro", "boss",
                "bullet_hero", "bullet_enemy", "hero_skin",
                "prop_blood", "prop_bomb", "prop_bullet", "prop_bulletPlus", "prop_freeze"};
        for (String key : required) {
            if (!images.containsKey(key)) {
                throw new IllegalStateException("缺少必需的资源: " + key);
            }
        }
    }

    // ---------------- 实体 → 位图 ----------------

    public Bitmap bitmapFor(Object entity) {
        if (entity instanceof HeroAircraft) {
            return heroSkin(((HeroAircraft) entity).getSkin());
        }
        if (entity instanceof MobEnemy) {
            return images.get("mob");
        }
        if (entity instanceof EliteEnemy) {
            return images.get("elite");
        }
        if (entity instanceof ElitePlusEnemy) {
            return images.get("elitePlus");
        }
        if (entity instanceof EliteProEnemy) {
            return images.get("elitePro");
        }
        if (entity instanceof BossEnemy) {
            return images.get("boss");
        }
        if (entity instanceof HeroBullet) {
            return images.get("bullet_hero");
        }
        if (entity instanceof EnemyBullet) {
            return images.get("bullet_enemy");
        }
        if (entity instanceof BloodItem) {
            return images.get("prop_blood");
        }
        if (entity instanceof BombItem) {
            return images.get("prop_bomb");
        }
        if (entity instanceof FireItem) {
            return images.get("prop_bullet");
        }
        if (entity instanceof SuperFireItem) {
            return images.get("prop_bulletPlus");
        }
        if (entity instanceof FreezeItem) {
            return images.get("prop_freeze");
        }
        return images.get("mob");
    }

    public Bitmap heroSkin(HeroAircraft.Skin skin) {
        Bitmap bmp = heroSkins.get(skin);
        return bmp != null ? bmp : images.get("hero_skin");
    }

    public Bitmap background(Difficulty difficulty) {
        Bitmap bmp = backgrounds.get(difficulty);
        return bmp != null ? bmp : images.get("hero_skin");
    }

    // ---------------- SpriteMetrics（核心层向这里要宽高） ----------------

    @Override
    public int widthOf(Class<?> type) {
        Bitmap bmp = bitmapForType(type);
        return bmp == null ? 32 : bmp.getWidth();
    }

    @Override
    public int heightOf(Class<?> type) {
        Bitmap bmp = bitmapForType(type);
        return bmp == null ? 32 : bmp.getHeight();
    }

    private Bitmap bitmapForType(Class<?> type) {
        if (HeroAircraft.class.isAssignableFrom(type)) {
            return images.get("hero_skin");
        }
        if (MobEnemy.class.isAssignableFrom(type)) {
            return images.get("mob");
        }
        if (EliteEnemy.class.isAssignableFrom(type)) {
            return images.get("elite");
        }
        if (ElitePlusEnemy.class.isAssignableFrom(type)) {
            return images.get("elitePlus");
        }
        if (EliteProEnemy.class.isAssignableFrom(type)) {
            return images.get("elitePro");
        }
        if (BossEnemy.class.isAssignableFrom(type)) {
            return images.get("boss");
        }
        if (HeroBullet.class.isAssignableFrom(type)) {
            return images.get("bullet_hero");
        }
        if (EnemyBullet.class.isAssignableFrom(type)) {
            return images.get("bullet_enemy");
        }
        if (BloodItem.class.isAssignableFrom(type)) {
            return images.get("prop_blood");
        }
        if (BombItem.class.isAssignableFrom(type)) {
            return images.get("prop_bomb");
        }
        if (FireItem.class.isAssignableFrom(type)) {
            return images.get("prop_bullet");
        }
        if (SuperFireItem.class.isAssignableFrom(type)) {
            return images.get("prop_bulletPlus");
        }
        if (FreezeItem.class.isAssignableFrom(type)) {
            return images.get("prop_freeze");
        }
        return null;
    }
}
