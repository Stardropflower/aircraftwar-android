package edu.hitsz.aircraftwar.net;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;

import edu.hitsz.aircraftwar.core.data.Difficulty;

/**
 * 一个对战会话：连接、建/进房、心跳、断线重连、状态同步。
 *
 * <p>它是「协议」与「界面」之间的中转：界面只调用 {@link #configure}/{@link #connect}
 * 和 {@link #reportScore}，收到结果通过 {@link Listener} 回调，不直接碰 WebSocket。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>单例</b>：房间页和游戏页是两个 Activity，用同一个会话对象才能把
 *       WebSocket 连接带过去，避免「建好房再断开重连一次」的浪费。</li>
 *   <li><b>所有状态都在主线程改</b>：OkHttp 回调在后台线程，这里统一 post 回主线程，
 *       避免多线程读写同一份状态。</li>
 *   <li><b>应用层心跳</b>：每 15 秒发一次 {@code ping}，连续 2 次没收到 {@code pong}
 *       就判掉线并自动重连。</li>
 *   <li><b>断线重连</b>：重连后带着同一房间码和名字重新 {@code join}，
 *       服务端会识别并补发当前 state（服务端保留房间 60 秒）。</li>
 * </ul>
 */
public class BattleSession {

    public enum Mode {CREATE, JOIN}

    /** 会话事件回调（都在主线程触发）。 */
    public interface Listener {
        default void onConnected() {
        }

        default void onJoined(String room, String you, String opponent,
                              Difficulty difficulty, int target) {
        }

        default void onState(String youName, String oppName,
                             int youScore, int youHp, int oppScore, int oppHp) {
        }

        /** @param win 是否本机获胜 */
        default void onOver(boolean win, int scoreA, int scoreB, String reason) {
        }

        default void onOpponentLeft() {
        }

        default void onError(String message) {
        }

        default void onDisconnected() {
        }
    }

    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final int MAX_MISSED_PONGS = 2;
    private static final long RECONNECT_DELAY_MS = 3_000L;
    private static final int MAX_RECONNECT = 5;

    private static BattleSession instance;

    public static BattleSession getInstance() {
        if (instance == null) {
            instance = new BattleSession();
        }
        return instance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BattleClient client;
    private Listener listener = new Listener() {
    };

    // 会话配置
    private String serverUrl;
    private String name;
    private Mode mode;
    private String room;
    private Difficulty difficulty;
    private int target;

    // 加入后确定的信息
    private String mySlot;        // "A" 或 "B"
    private String opponentName;

    // 双方最新状态（last-known）
    private String youName;
    private String oppName;
    private int youScore;
    private int youHp;
    private int oppScore;
    private int oppHp;

    // 状态标记
    private boolean joined = false;
    private boolean over = false;
    private boolean closedByUser = false;
    private boolean down = false;             // 是否已进入「掉线」状态（去重 onClosed/onFailure）
    private boolean reconnectScheduled = false;
    private int missedPongs = 0;
    private int reconnectCount = 0;

    private BattleSession() {
    }

    // ---------------- 配置与连接 ----------------

    /**
     * 配置一局对战。会先清掉上一局的所有状态。
     *
     * @param serverUrl  服务器地址（{@code ws://...}）
     * @param name       本机昵称
     * @param mode       建房还是进房
     * @param room       进房时的房间码（建房传 null）
     * @param difficulty 难度（建房时指定；进房时会被服务端返回的房间难度覆盖）
     * @param target     目标分
     */
    public void configure(String serverUrl, String name, Mode mode, String room,
                          Difficulty difficulty, int target) {
        reset();
        this.serverUrl = serverUrl;
        this.name = name;
        this.mode = mode;
        this.room = room;
        this.difficulty = difficulty;
        this.target = target;
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    /** 建立连接，成功后自动建房或进房。 */
    public void connect() {
        if (serverUrl == null) {
            listener.onError("服务器地址为空");
            return;
        }
        closedByUser = false;
        over = false;
        down = false;
        reconnectCount = 0;
        missedPongs = 0;
        open();
    }

    private void open() {
        if (client != null) {
            client.close();
        }
        client = new BattleClient();
        client.connect(serverUrl, createClientListener());
    }

    private BattleClient.Listener createClientListener() {
        return new BattleClient.Listener() {
            @Override
            public void onOpen() {
                handler.post(BattleSession.this::onOpenOnMain);
            }

            @Override
            public void onMessage(String text) {
                handler.post(() -> dispatch(text));
            }

            @Override
            public void onClosed(int code, String reason) {
                handler.post(BattleSession.this::onSocketDown);
            }

            @Override
            public void onFailure(Throwable t) {
                handler.post(BattleSession.this::onSocketDown);
            }
        };
    }

    private void onOpenOnMain() {
        down = false;
        reconnectCount = 0;
        resendCurrentRequest();
        listener.onConnected();
        startHeartbeat();
    }

    /** 连接建立后：没加入过就按模式建/进房，加入过就带着原房间码重连。 */
    private void resendCurrentRequest() {
        if (joined && room != null) {
            sendJoin();      // 断线重连：服务端会识别名字并补发 state
        } else if (mode == Mode.CREATE) {
            sendCreate();
        } else {
            sendJoin();
        }
    }

    // ---------------- 发送消息 ----------------

    private void send(JsonObject obj) {
        if (client != null && client.isOpen()) {
            client.send(BattleProtocol.encode(obj));
        }
    }

    private void sendCreate() {
        JsonObject o = new JsonObject();
        o.addProperty("type", BattleProtocol.TYPE_CREATE);
        o.addProperty("name", name);
        o.addProperty("difficulty", difficulty.name());
        o.addProperty("target", target);
        send(o);
    }

    private void sendJoin() {
        JsonObject o = new JsonObject();
        o.addProperty("type", BattleProtocol.TYPE_JOIN);
        o.addProperty("room", room);
        o.addProperty("name", name);
        send(o);
    }

    /** 每秒上报一次分数与血量。服务端会广播 state 并判定胜负。 */
    public void reportScore(int score, int hp) {
        if (!joined || over || client == null || !client.isOpen()) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", BattleProtocol.TYPE_SCORE);
        o.addProperty("room", room);
        o.addProperty("score", score);
        o.addProperty("hp", hp);
        send(o);
    }

    /** 主动离开本局（退房）。 */
    public void leave() {
        if (joined && client != null && client.isOpen()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", BattleProtocol.TYPE_LEAVE);
            o.addProperty("room", room);
            send(o);
        }
        close();
    }

    private void sendPing() {
        JsonObject o = new JsonObject();
        o.addProperty("type", BattleProtocol.TYPE_PING);
        send(o);
    }

    // ---------------- 收消息 ----------------

    private void dispatch(String text) {
        JsonObject msg = BattleProtocol.parse(text);
        String type = BattleProtocol.typeOf(msg);
        if (type.isEmpty()) {
            return;
        }
        switch (type) {
            case BattleProtocol.TYPE_JOINED:
                handleJoined(msg);
                break;
            case BattleProtocol.TYPE_STATE:
                handleState(msg);
                break;
            case BattleProtocol.TYPE_OVER:
                handleOver(msg);
                break;
            case BattleProtocol.TYPE_OPPONENT_LEFT:
                handleOpponentLeft();
                break;
            case BattleProtocol.TYPE_PONG:
                missedPongs = 0;
                break;
            case BattleProtocol.TYPE_ERROR:
                listener.onError(BattleProtocol.str(msg, "message"));
                break;
            default:
                break;
        }
    }

    private void handleJoined(JsonObject msg) {
        joined = true;
        room = BattleProtocol.str(msg, "room");
        mySlot = BattleProtocol.str(msg, "you");
        opponentName = BattleProtocol.str(msg, "opponent");
        String diff = BattleProtocol.str(msg, "difficulty");
        if (diff != null) {
            try {
                difficulty = Difficulty.valueOf(diff);
            } catch (IllegalArgumentException ignored) {
                // 服务端返回了未知难度，沿用本地值
            }
        }
        target = BattleProtocol.integer(msg, "target", target);
        youName = name;
        oppName = opponentName;
        listener.onJoined(room, mySlot, opponentName, difficulty, target);
    }

    private void handleState(JsonObject msg) {
        JsonObject a = msg.has("a") && !msg.get("a").isJsonNull()
                ? msg.getAsJsonObject("a") : new JsonObject();
        JsonObject b = msg.has("b") && !msg.get("b").isJsonNull()
                ? msg.getAsJsonObject("b") : new JsonObject();
        JsonObject mine = "B".equals(mySlot) ? b : a;
        JsonObject opp = "B".equals(mySlot) ? a : b;

        youName = BattleProtocol.str(mine, "name");
        youScore = BattleProtocol.integer(mine, "score", 0);
        youHp = BattleProtocol.integer(mine, "hp", 0);
        oppName = BattleProtocol.str(opp, "name");
        oppScore = BattleProtocol.integer(opp, "score", 0);
        oppHp = BattleProtocol.integer(opp, "hp", 0);

        listener.onState(youName, oppName, youScore, youHp, oppScore, oppHp);
    }

    private void handleOver(JsonObject msg) {
        over = true;
        String winner = BattleProtocol.str(msg, "winner");
        int scoreA = BattleProtocol.integer(msg, "scoreA", 0);
        int scoreB = BattleProtocol.integer(msg, "scoreB", 0);
        boolean win = mySlot != null && mySlot.equals(winner);
        stopHeartbeat();
        listener.onOver(win, scoreA, scoreB, BattleProtocol.str(msg, "reason"));
    }

    private void handleOpponentLeft() {
        over = true;
        stopHeartbeat();
        listener.onOpponentLeft();
    }

    // ---------------- 心跳与重连 ----------------

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (closedByUser || over) {
                return;
            }
            if (client == null || !client.isOpen()) {
                onSocketDown();
                return;
            }
            sendPing();
            missedPongs++;
            if (missedPongs >= MAX_MISSED_PONGS) {
                missedPongs = 0;
                onSocketDown();
            } else {
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private void startHeartbeat() {
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        handler.removeCallbacks(heartbeat);
    }

    /** 掉线入口（onClosed / onFailure / 心跳超时都会走到这，去重后只处理一次）。 */
    private void onSocketDown() {
        if (down || closedByUser || over) {
            return;
        }
        down = true;
        if (client != null) {
            client.close();
            client = null;
        }
        stopHeartbeat();
        listener.onDisconnected();

        if (reconnectCount >= MAX_RECONNECT) {
            listener.onError("连接失败，已放弃重连");
            return;
        }
        reconnectScheduled = true;
        reconnectCount++;
        handler.postDelayed(reconnectTask, RECONNECT_DELAY_MS);
    }

    private final Runnable reconnectTask = new Runnable() {
        @Override
        public void run() {
            reconnectScheduled = false;
            if (closedByUser || over) {
                return;
            }
            open(); // onOpen 里会重新 join，服务端补发 state
        }
    };

    // ---------------- 收尾 ----------------

    /** 完全关闭会话（游戏结束 / 退出时调用），清掉所有定时任务和连接。 */
    public void close() {
        closedByUser = true;
        stopHeartbeat();
        handler.removeCallbacks(reconnectTask);
        if (client != null) {
            client.close();
            client = null;
        }
        joined = false;
        over = true;
    }

    private void reset() {
        if (client != null) {
            client.close();
            client = null;
        }
        handler.removeCallbacksAndMessages(null);
        joined = false;
        over = false;
        closedByUser = false;
        down = false;
        reconnectScheduled = false;
        missedPongs = 0;
        reconnectCount = 0;
        mySlot = null;
        opponentName = null;
        youName = null;
        oppName = null;
        youScore = 0;
        youHp = 0;
        oppScore = 0;
        oppHp = 0;
    }

    // ---------------- 只读访问 ----------------

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getRoom() {
        return room;
    }

    public String getMySlot() {
        return mySlot;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public String getYouName() {
        return youName;
    }

    public int getYouScore() {
        return youScore;
    }

    public int getYouHp() {
        return youHp;
    }

    public int getOppScore() {
        return oppScore;
    }

    public int getOppHp() {
        return oppHp;
    }

    public boolean isJoined() {
        return joined;
    }

    public boolean isOver() {
        return over;
    }
}
