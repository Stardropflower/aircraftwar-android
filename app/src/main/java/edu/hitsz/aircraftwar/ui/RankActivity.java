package edu.hitsz.aircraftwar.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import edu.hitsz.aircraftwar.R;
import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.db.ScoreDao;
import edu.hitsz.aircraftwar.db.ScoreRecord;

/**
 * 排行榜页面（实验四的 4 项内容都在这里体现）。
 * <p>
 * <ul>
 *   <li>开发排行榜页面 → 本类 + {@code activity_rank.xml}</li>
 *   <li>展示排行榜数据 → {@link RankAdapter} 从 {@link ScoreDao} 取数</li>
 *   <li>删除排行榜数据 → 行末「×」按钮，删库后刷新列表</li>
 *   <li>改用 SQLite 存储 → {@link ScoreDao} + {@code ScoreDbHelper}</li>
 * </ul>
 */
public class RankActivity extends AppCompatActivity {

    /** 从外部指定初始难度（游戏结算页「查看排行榜」会带过来） */
    public static final String EXTRA_DIFFICULTY = "difficulty";

    private ScoreDao dao;
    private RankAdapter adapter;
    private ListView listView;
    private TextView emptyView;
    private TextView summaryView;
    private String currentDifficulty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rank);
        UiInsets.applySystemBars(findViewById(R.id.root_rank));

        dao = new ScoreDao(this);
        currentDifficulty = readDifficulty();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        listView = findViewById(R.id.lv_rank);
        emptyView = findViewById(R.id.tv_empty);
        summaryView = findViewById(R.id.tv_summary);

        adapter = new RankAdapter(this, this::confirmDelete);
        // 课件第 10 页：找到 ListView → 创建 Adapter → setAdapter
        listView.setAdapter(adapter);

        findViewById(R.id.btn_tab_easy).setOnClickListener(v -> switchDifficulty(Difficulty.EASY));
        findViewById(R.id.btn_tab_normal).setOnClickListener(v -> switchDifficulty(Difficulty.NORMAL));
        findViewById(R.id.btn_tab_hard).setOnClickListener(v -> switchDifficulty(Difficulty.HARD));

        updateTabs();
        loadScores();
    }

    private String readDifficulty() {
        String extra = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_DIFFICULTY);
        if (extra != null) {
            try {
                return Difficulty.valueOf(extra).name();
            } catch (IllegalArgumentException ignored) {
                // 传了非法值就回退到简单难度
            }
        }
        return Difficulty.EASY.name();
    }


    /** 高亮当前选中的难度标签 */
    private void updateTabs() {
        int selected = getColor(R.color.accent_deep);
        int normal = getColor(R.color.surface_hover);
        tint(R.id.btn_tab_easy, Difficulty.EASY.name().equals(currentDifficulty) ? selected : normal);
        tint(R.id.btn_tab_normal, Difficulty.NORMAL.name().equals(currentDifficulty) ? selected : normal);
        tint(R.id.btn_tab_hard, Difficulty.HARD.name().equals(currentDifficulty) ? selected : normal);
    }

    private void tint(int id, int color) {
        Button button = findViewById(id);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    /** 切换难度标签：只改选中状态和标题，然后重新查库 */
    private void switchDifficulty(Difficulty difficulty) {
        currentDifficulty = difficulty.name();
        updateTabs();
        loadScores();
    }

    private void loadScores() {
        List<ScoreRecord> records = dao.queryByDifficulty(currentDifficulty);
        adapter.submit(records);

        boolean empty = records.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);

        int best = empty ? 0 : records.get(0).score;
        summaryView.setText(empty
                ? "该难度还没有记录"
                : "共 " + records.size() + " 条 · 最高 " + best + " 分（最多显示 " + ScoreDao.TOP_LIMIT + " 条）");
    }

    /**
     * 删除确认。课件第 4 页要求「点击行末删除按钮，同步移除数据源并刷新列表」，
     * 这里多一步确认弹窗，避免误触删掉记录。
     */
    private void confirmDelete(ScoreRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除「" + record.name + "」的 " + record.score + " 分吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    int rows = dao.deleteById(record.id);
                    if (rows > 0) {
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    }
                    // 重新查库并刷新列表（adapter.notifyDataSetChanged 在 submit 里）
                    loadScores();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (dao != null) {
            dao.close();
        }
        super.onDestroy();
    }
}
