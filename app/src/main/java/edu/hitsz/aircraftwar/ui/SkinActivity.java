package edu.hitsz.aircraftwar.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.core.aircraft.HeroAircraft;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;
import edu.hitsz.aircraftwar.render.Assets;

/**
 * 皮肤选择页。
 * <p>
 * 七款皮肤以网格展示，未解锁的显示解锁条件。
 * 解锁判定用 {@link LocalStore#isSkinUnlocked}，依据的是**本机**对应难度的历史最高分 ——
 * 与 Windows 版口径一致，不能拿云端榜单来判，否则榜上出现高手就会让所有人瞬间全解锁。
 */
public class SkinActivity extends AppCompatActivity {

    private static final int ACCENT = 0xFF3EC8FF;
    private static final int TEXT = 0xFFE9EEF7;
    private static final int TEXT_MUTED = 0xFF8FA0B8;
    private static final int SURFACE = 0xFF161C26;
    private static final int BORDER = 0xFF2A3442;

    private LocalStore store;
    private Assets assets;
    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);
        assets = Assets.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1218);

        TextView title = new TextView(this);
        title.setText("皮肤选择");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(40), 0, dp(6));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("解锁条件依据本机各难度的历史最高分");
        hint.setTextSize(12);
        hint.setTextColor(TEXT_MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(20));
        root.addView(hint);

        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(dp(16), 0, dp(16), dp(24));
        root.addView(grid);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        rebuildGrid();
    }

    private void rebuildGrid() {
        grid.removeAllViews();
        HeroAircraft.Skin selected = store.getSelectedSkin();

        for (HeroAircraft.Skin skin : HeroAircraft.Skin.values()) {
            boolean unlocked = store.isSkinUnlocked(skin);
            boolean isSelected = skin == selected;
            grid.addView(buildCell(skin, unlocked, isSelected));
        }
    }

    private LinearLayout buildCell(HeroAircraft.Skin skin, boolean unlocked, boolean isSelected) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(10), dp(12), dp(10), dp(12));
        cell.setBackground(roundedBackground(
                SURFACE,
                isSelected ? ACCENT : BORDER,
                isSelected ? 2 : 1));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        cell.setLayoutParams(params);

        ImageView preview = new ImageView(this);
        preview.setImageBitmap(assets.heroSkin(skin));
        preview.setAlpha(unlocked ? 1f : 0.35f);
        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(dp(96), dp(80));
        preview.setLayoutParams(imageParams);
        cell.addView(preview);

        TextView name = new TextView(this);
        name.setText(skin.getDisplayName());
        name.setTextSize(16);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(unlocked ? TEXT : TEXT_MUTED);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(0, dp(6), 0, dp(2));
        cell.addView(name);

        TextView status = new TextView(this);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        if (isSelected) {
            status.setText("使用中");
            status.setTextColor(ACCENT);
        } else if (unlocked) {
            status.setText("已解锁");
            status.setTextColor(0xFF46D39A);
        } else {
            status.setText(unlockRequirement(skin));
            status.setTextColor(TEXT_MUTED);
        }
        cell.addView(status);

        cell.setOnClickListener(v -> {
            if (store.isSkinUnlocked(skin)) {
                store.setSelectedSkin(skin);
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "未解锁：" + unlockRequirement(skin),
                        Toast.LENGTH_SHORT).show();
            }
        });
        return cell;
    }

    /** 例如「简单模式 3000 分」 */
    private String unlockRequirement(HeroAircraft.Skin skin) {
        if (skin.isDefaultUnlocked()) {
            return "默认解锁";
        }
        Difficulty difficulty = skin.getUnlockDifficulty();
        String diffName;
        if (difficulty == null) {
            diffName = "未知";
        } else {
            switch (difficulty) {
                case EASY:
                    diffName = "简单";
                    break;
                case HARD:
                    diffName = "困难";
                    break;
                default:
                    diffName = "普通";
                    break;
            }
        }
        return diffName + "模式 " + skin.getUnlockScore() + " 分";
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
