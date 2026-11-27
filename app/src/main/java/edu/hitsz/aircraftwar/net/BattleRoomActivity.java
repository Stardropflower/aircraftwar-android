package edu.hitsz.aircraftwar.net;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import edu.hitsz.aircraftwar.core.data.Difficulty;
import edu.hitsz.aircraftwar.data.LocalStore;
import edu.hitsz.aircraftwar.ui.GameActivity;

/**
 * 网络对战入口：创建房间 / 加入房间。
 *
 * <p>流程：
 * <ul>
 *   <li><b>创建房间</b>：填昵称、选难度、定目标分 → 连接服务端 → 拿到 6 位房间码，
 *       把它告诉队友 → 队友加入后双方同时开局。</li>
 *   <li><b>加入房间</b>：填昵称、输入队友给的房间码 → 连接服务端 → 直接开局。</li>
 * </ul>
 *
 * <p>服务器地址默认是 {@code ws://10.0.2.2:8765}（模拟器访问宿主机）；真机调试要改成
 * 电脑的局域网 IP，界面上可以直接改，不必重新编译。
 */
public class BattleRoomActivity extends AppCompatActivity {

    private static final int ACCENT = 0xFF3EC8FF;
    private static final int TEXT = 0xFFE9EEF7;
    private static final int TEXT_MUTED = 0xFF8FA0B8;
    private static final int SURFACE = 0xFF161C26;
    private static final int BORDER = 0xFF2A3442;
    private static final int SUCCESS = 0xFF46D39A;
    private static final int DANGER = 0xFFFF5C6C;

    private LocalStore store;
    private EditText nicknameField;
    private EditText serverField;
    private EditText targetField;
    private EditText roomField;
    private TextView statusText;

    private Difficulty difficulty = Difficulty.NORMAL;
    private Button[] difficultyButtons;

    /** 防止「对手加入 → 开局」被触发两次。 */
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new LocalStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1218);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("网络对战");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("比分竞赛 · 先到目标分者胜");
        subtitle.setTextSize(12);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(24));
        root.addView(subtitle);

        root.addView(label("昵称"));
        nicknameField = field("例如：玩家A");
        nicknameField.setText(store.getPlayerName());
        root.addView(nicknameField);

        root.addView(label("服务器地址"));
        serverField = field(BattleProtocol.DEFAULT_SERVER_URL);
        serverField.setText(BattleProtocol.DEFAULT_SERVER_URL);
        root.addView(serverField);

        // ---------------- 创建房间 ----------------
        root.addView(sectionTitle("创建房间"));

        root.addView(label("难度"));
        root.addView(buildDifficultySelector());

        root.addView(label("目标分"));
        targetField = field(String.valueOf(BattleProtocol.DEFAULT_TARGET));
        targetField.setText(String.valueOf(BattleProtocol.DEFAULT_TARGET));
        targetField.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(targetField);

        root.addView(primaryButton("创建房间", SUCCESS, v -> onCreateRoom()));

        // ---------------- 加入房间 ----------------
        root.addView(sectionTitle("加入房间"));

        root.addView(label("房间码（6 位）"));
        roomField = field("例如：A3F9K2");
        roomField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        root.addView(roomField);

        root.addView(primaryButton("加入房间", ACCENT, v -> onJoinRoom()));

        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(TEXT_MUTED);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(20), 0, dp(8));
        root.addView(statusText);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    // ---------------- 建房 / 进房 ----------------

    private void onCreateRoom() {
        if (launched) {
            return;
        }
        String nickname = validNickname();
        if (nickname == null) {
            return;
        }
        String server = validServer();
        int target = validTarget();
        store.setPlayerName(nickname);

        BattleSession session = BattleSession.getInstance();
        session.configure(server, nickname, BattleSession.Mode.CREATE, null, difficulty, target);
        session.setListener(sessionListener());
        statusText.setText("正在创建房间…");
        session.connect();
    }

    private void onJoinRoom() {
        if (launched) {
            return;
        }
        String nickname = validNickname();
        if (nickname == null) {
            return;
        }
        String server = validServer();
        String room = roomField.getText().toString().trim().toUpperCase();
        if (room.isEmpty()) {
            Toast.makeText(this, "请输入房间码", Toast.LENGTH_SHORT).show();
            return;
        }
        store.setPlayerName(nickname);

        BattleSession session = BattleSession.getInstance();
        session.configure(server, nickname, BattleSession.Mode.JOIN, room, difficulty, 0);
        session.setListener(sessionListener());
        statusText.setText("正在加入房间 " + room + " …");
        session.connect();
    }

    // ---------------- 会话回调 ----------------

    private BattleSession.Listener sessionListener() {
        return new BattleSession.Listener() {
            @Override
            public void onConnected() {
                statusText.setText("已连接，等待服务端应答…");
            }

            @Override
            public void onJoined(String room, String you, String opponent,
                                 Difficulty difficulty, int target) {
                if (you == null) {
                    return;
                }
                if ("A".equals(you) && (opponent == null || opponent.isEmpty())) {
                    // 房主：还没有对手，显示房间码等队友
                    statusText.setText("房间码： " + room + "\n\n等待对手加入…");
                    statusText.setTextColor(SUCCESS);
                } else {
                    // 加入者：已和对手碰头，直接开局
                    launchGame();
                }
            }

            @Override
            public void onState(String youName, String oppName,
                                int youScore, int youHp, int oppScore, int oppHp) {
                // 房主等到「对手加入」后的第一条 state，即开局
                if (oppName != null && !oppName.isEmpty()) {
                    launchGame();
                }
            }

            @Override
            public void onError(String message) {
                statusText.setText("出错了：" + (message == null ? "未知错误" : message));
                statusText.setTextColor(DANGER);
                Toast.makeText(BattleRoomActivity.this,
                        message == null ? "出错了" : message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected() {
                statusText.setText("连接断开，正在重连…");
                statusText.setTextColor(TEXT_MUTED);
            }
        };
    }

    /** 进入游戏页并结束本页（返回键会回到主菜单）。 */
    private void launchGame() {
        if (launched || isFinishing()) {
            return;
        }
        launched = true;
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_BATTLE, true);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果没进入对局（用户直接返回），把连接关掉，别让心跳一直挂着
        if (!launched) {
            BattleSession.getInstance().close();
        }
    }

    // ---------------- 输入校验 ----------------

    private String validNickname() {
        String name = nicknameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请先填昵称", Toast.LENGTH_SHORT).show();
            return null;
        }
        return name;
    }

    private String validServer() {
        String server = serverField.getText().toString().trim();
        if (server.isEmpty()) {
            return BattleProtocol.DEFAULT_SERVER_URL;
        }
        return server;
    }

    private int validTarget() {
        try {
            int t = Integer.parseInt(targetField.getText().toString().trim());
            return Math.max(100, Math.min(1_000_000, t));
        } catch (NumberFormatException e) {
            return BattleProtocol.DEFAULT_TARGET;
        }
    }

    // ---------------- 构建 UI 的辅助方法 ----------------

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(TEXT_MUTED);
        v.setPadding(0, dp(10), 0, dp(6));
        return v;
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(ACCENT);
        v.setPadding(0, dp(24), 0, dp(4));
        return v;
    }

    private EditText field(String hint) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setHintTextColor(0xFF5A6B80);
        v.setTextColor(TEXT);
        v.setTextSize(15);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setSingleLine(true);
        v.setBackground(roundedBackground(SURFACE, BORDER, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout buildDifficultySelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Difficulty[] values = {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD};
        String[] names = {"简单", "普通", "困难"};
        difficultyButtons = new Button[values.length];
        for (int i = 0; i < values.length; i++) {
            final Difficulty d = values[i];
            Button b = new Button(this);
            b.setText(names[i]);
            b.setTextSize(14);
            b.setAllCaps(false);
            b.setTag(d);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    dp(46), 1f);
            p.setMargins(dp(4), 0, dp(4), 0);
            b.setLayoutParams(p);
            b.setOnClickListener(v -> selectDifficulty(d));
            row.addView(b);
            difficultyButtons[i] = b;
        }
        selectDifficulty(difficulty);
        return row;
    }

    private void selectDifficulty(Difficulty d) {
        difficulty = d;
        for (Button b : difficultyButtons) {
            Difficulty tag = (Difficulty) b.getTag();
            boolean sel = tag == d;
            b.setTextColor(sel ? Color.rgb(0x0E, 0x12, 0x18) : TEXT);
            b.setBackgroundColor(sel ? ACCENT : SURFACE);
        }
    }

    private Button primaryButton(String text, int accent, android.view.View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(0x0E, 0x12, 0x18));
        b.setBackgroundColor(accent);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(0, dp(12), 0, dp(6));
        b.setLayoutParams(p);
        b.setOnClickListener(onClick);
        return b;
    }

    private GradientDrawable roundedBackground(int fill, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
