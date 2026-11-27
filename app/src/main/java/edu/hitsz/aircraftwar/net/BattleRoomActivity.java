package edu.hitsz.aircraftwar.net;

import android.app.AlertDialog;
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
import edu.hitsz.aircraftwar.ui.UiInsets;

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

        // 右上角「使用教程」：真机联机最容易卡在网络配置（IP、防火墙、端口），
        // 把步骤直接写进界面，省得还要另外翻文档
        root.addView(buildTutorialBar());

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
        // 记住上次填的地址：真机联机时电脑 IP 是固定的，每次重敲太烦
        String savedServer = store.getBattleServer();
        serverField.setText(savedServer != null ? savedServer : BattleProtocol.DEFAULT_SERVER_URL);
        root.addView(serverField);

        // 状态提示放在这里而不是页面底部：房间码、连接错误都从这行出，
        // 放最底下时用户在屏幕上看不到「等待对手加入…」和房间码
        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setTextColor(TEXT_MUTED);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(12), 0, dp(4));
        root.addView(statusText);

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

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        // Android 15 起普通页面也强制 edge-to-edge，不补系统栏内边距的话
        // 右上角的「使用教程」会被状态栏挡住
        UiInsets.applySystemBars(scroll);
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
        // 记住这次填的地址，下次进对战页自动带出来
        store.setBattleServer(server);
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

    // ---------------- 联机教程 ----------------

    /** 右上角那一行：目前只有一个「使用教程」入口，右对齐 */
    private LinearLayout buildTutorialBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);

        Button help = new Button(this);
        help.setText("使用教程");
        help.setTextSize(13);
        help.setAllCaps(false);
        help.setTextColor(ACCENT);
        help.setBackground(roundedBackground(SURFACE, (ACCENT & 0x00FFFFFF) | 0x66000000, 1));
        help.setPadding(dp(16), 0, dp(16), 0);
        help.setMinWidth(0);
        help.setMinimumWidth(0);
        help.setMinHeight(dp(36));
        help.setMinimumHeight(dp(36));
        help.setOnClickListener(v -> showTutorial());
        bar.addView(help);
        return bar;
    }

    /**
     * 联机教程弹窗。
     * <p>
     * 内容按「最容易卡住的顺序」排：先确认网络，再起服务端，再填地址，最后才是建/加房间。
     * 真机对联失败绝大多数不是代码问题，而是 IP 填错、防火墙没放行、或者两台设备根本不在一个网段。
     */
    private void showTutorial() {
        String steps =
                "① 两台设备连同一个 WiFi / 局域网\n"
                        + "   跑服务端的电脑和手机必须在同一个网络里。\n\n"
                        + "② 在电脑上启动服务端\n"
                        + "   cd server\n"
                        + "   npm install      （只需第一次）\n"
                        + "   npm start\n"
                        + "   看到「ws://0.0.0.0:8765」就说明起来了。\n\n"
                        + "③ 查电脑的局域网 IP\n"
                        + "   Windows 命令行输入 ipconfig，找「IPv4 地址」那一行，\n"
                        + "   形如 192.168.1.5 或 10.250.115.81。\n\n"
                        + "④ 把上面的「服务器地址」改成 ws://电脑IP:8765\n"
                        + "   例如 ws://10.250.115.81:8765\n"
                        + "   用模拟器则填 ws://10.0.2.2:8765\n"
                        + "   （10.0.2.2 是模拟器访问宿主机的固定地址）\n\n"
                        + "⑤ 一人点「创建房间」：选难度、填目标分，拿到 6 位房间码，念给队友。\n"
                        + "   另一人点「加入房间」：输入这个房间码。双方自动开局。\n\n"
                        + "⑥ 各自在自己手机上打同一关卡，每秒同步分数与血量。\n"
                        + "   先到目标分者获胜，胜负由服务端判定，不是本地自己算的。\n\n"
                        + "── 连不上怎么排查 ──\n"
                        + "· 两台设备不在同一网络（比如一台走了 4G 流量）\n"
                        + "· 电脑防火墙拦了 8765：Windows 弹窗要点「允许」，\n"
                        + "  或到「防火墙 → 允许应用通过」里放行 Node.js\n"
                        + "· 服务端没启动，或者地址里的端口写错\n"
                        + "· 校园网 / 公司网做了终端隔离时，改用 USB 数据线：\n"
                        + "  电脑执行  adb reverse tcp:8765 tcp:8765\n"
                        + "  手机上的地址改填  ws://127.0.0.1:8765";

        TextView content = new TextView(this);
        content.setText(steps);
        content.setTextSize(13);
        content.setTextColor(TEXT);
        content.setLineSpacing(dp(2), 1f);
        content.setPadding(dp(18), dp(12), dp(18), dp(12));
        content.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("怎么联机（比分竞赛）")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
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
