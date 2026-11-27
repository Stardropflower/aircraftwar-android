package edu.hitsz.aircraftwar.render;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import edu.hitsz.aircraftwar.R;
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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源仓库：把 drawable 里的图片解码成 {@link Bitmap}，并承担「实体 → 位图」的映射。
 * <p>
 * 按课程实验二的要求，图片资源全部走 <b>res/drawable + BitmapFactory.decodeResource</b>
 * （而不是 Windows 版的 {@code ImageIO.read}，也不是 assets）。
 * <p>
 * 两个细节：
 * <ol>
 *   <li>图片放在 <b>drawable-nodpi</b> 目录，解码时 {@code inScaled = false}。
 *       系统不能按屏幕密度改像素。飞机贴图是 3 倍清晰度，碰撞框仍用
 *       {@link #logicalAircraftBox(Class)} 里的原始逻辑尺寸，不能改用位图像素。</li>
 *   <li>映射用 {@code instanceof} 而不是「类名 → 图片」的字符串表：
 *       Android 上 R8 可能混淆类名，靠字符串匹配在 release 包里会静默失效。</li>
 * </ol>
 * <p>
 * 核心层不持有任何图像（它只有 {@link SpriteMetrics} 拿到的宽高），
 * 图像完全由这一层负责 —— 这是把逻辑与平台解耦的直接收益：
 * 核心层能在 JVM 上单测，渲染层则独占 Android 的图像 API。
 */
public final class Assets implements SpriteMetrics {

    private final Resources resources;
    /** 关闭按屏幕密度缩放，保证拿到的是图片原始像素尺寸 */
    private final BitmapFactory.Options options = new BitmapFactory.Options();

    private final Map<String, Bitmap> images = new HashMap<>();
    private final Map<HeroAircraft.Skin, Bitmap> heroSkins = new EnumMap<>(HeroAircraft.Skin.class);
    private final Map<Difficulty, Bitmap> backgrounds = new EnumMap<>(Difficulty.class);

    private Assets(Context context) {
        this.resources = context.getApplicationContext().getResources();
        this.options.inScaled = false;
    }

    public static Assets load(Context context) {
        Assets assets = new Assets(context);

        // 敌机与子弹
        assets.put("mob", R.drawable.mob);
        assets.put("elite", R.drawable.elite);
        assets.put("elitePlus", R.drawable.eliteplus);
        assets.put("elitePro", R.drawable.elitepro);
        assets.put("boss", R.drawable.boss);
        assets.put("bullet_hero", R.drawable.bullet_hero);
        assets.put("bullet_enemy", R.drawable.bullet_enemy);

        // 道具
        assets.put("prop_blood", R.drawable.prop_blood);
        assets.put("prop_bomb", R.drawable.prop_bomb);
        assets.put("prop_bullet", R.drawable.prop_bullet);
        assets.put("prop_bulletPlus", R.drawable.prop_bulletplus);
        assets.put("prop_freeze", R.drawable.prop_freeze);

        // 七款皮肤
        assets.put("hero_skin", R.drawable.hero);
        assets.put("plane_silver", R.drawable.plane_silver);
        assets.put("plane_jade", R.drawable.plane_jade);
        assets.put("plane_golden", R.drawable.plane_golden);
        assets.put("plane_crimson", R.drawable.plane_crimson);
        assets.put("plane_manba", R.drawable.plane_manba);
        assets.put("plane_obsidian", R.drawable.plane_obsidian);

        assets.heroSkins.put(HeroAircraft.Skin.HERO, assets.get("hero_skin"));
        assets.heroSkins.put(HeroAircraft.Skin.SILVER, assets.get("plane_silver"));
        assets.heroSkins.put(HeroAircraft.Skin.JADE, assets.get("plane_jade"));
        assets.heroSkins.put(HeroAircraft.Skin.GOLDEN, assets.get("plane_golden"));
        assets.heroSkins.put(HeroAircraft.Skin.CRIMSON, assets.get("plane_crimson"));
        assets.heroSkins.put(HeroAircraft.Skin.MANBA, assets.get("plane_manba"));
        assets.heroSkins.put(HeroAircraft.Skin.OBSIDIAN, assets.get("plane_obsidian"));

        // 三档难度的背景（沿用 Windows 版的对应关系）
        assets.putBackground(Difficulty.EASY, R.drawable.bg3);
        assets.putBackground(Difficulty.NORMAL, R.drawable.bg);
        assets.putBackground(Difficulty.HARD, R.drawable.bg4);

        assets.validate();
        return assets;
    }

    private void put(String key, int resId) {
        Bitmap bmp = BitmapFactory.decodeResource(resources, resId, options);
        if (bmp != null) {
            images.put(key, bmp);
        } else {
            System.err.println("资源解码失败: " + key);
        }
    }

    private void putBackground(Difficulty difficulty, int resId) {
        Bitmap bmp = BitmapFactory.decodeResource(resources, resId, options);
        if (bmp != null) {
            backgrounds.put(difficulty, bmp);
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
        if (backgrounds.isEmpty()) {
            throw new IllegalStateException("缺少背景资源");
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
        return bmp != null ? bmp : backgrounds.get(Difficulty.NORMAL);
    }

    // ---------------- SpriteMetrics（核心层向这里要宽高） ----------------

    /**
     * 飞机的逻辑宽高，沿用替换贴图之前的像素尺寸。
     * 碰撞和绘制都用这组数；贴图本身可以是 3 倍清晰度，不能反过来把碰撞框放大。
     * 子弹和道具没有重绘，仍按位图像素返回。
     */
    private int[] logicalAircraftBox(Class<?> type) {
        if (HeroAircraft.class.isAssignableFrom(type)) {
            return new int[]{100, 83};
        }
        if (MobEnemy.class.isAssignableFrom(type)) {
            return new int[]{75, 50};
        }
        if (ElitePlusEnemy.class.isAssignableFrom(type)) {
            return new int[]{105, 68};
        }
        if (EliteProEnemy.class.isAssignableFrom(type)) {
            return new int[]{100, 83};
        }
        if (EliteEnemy.class.isAssignableFrom(type)) {
            return new int[]{105, 68};
        }
        if (BossEnemy.class.isAssignableFrom(type)) {
            return new int[]{300, 200};
        }
        return null;
    }

    @Override
    public int widthOf(Class<?> type) {
        int[] box = logicalAircraftBox(type);
        if (box != null) {
            return box[0];
        }
        Bitmap bmp = bitmapForType(type);
        return bmp == null ? 32 : bmp.getWidth();
    }

    @Override
    public int heightOf(Class<?> type) {
        int[] box = logicalAircraftBox(type);
        if (box != null) {
            return box[1];
        }
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
