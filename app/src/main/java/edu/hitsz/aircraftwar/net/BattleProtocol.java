package edu.hitsz.aircraftwar.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.security.SecureRandom;

/**
 * 对战协议：消息类型常量、房间码生成、JSON 编解码。
 *
 * <p>协议是「JSON over WebSocket」，与任务书 / 队友入手指南里定稿的一致，只做
 * <b>比分竞赛</b>（不是实时互射）：两名玩家凭 6 位房间码进入同一局，各自在本地跑关卡，
 * 每 1 秒把分数与血量上报服务端，先到目标分者胜，<b>胜负由服务端判定</b>（防作弊）。
 *
 * <p>消息示例（客户端 → 服务端）：
 * <pre>{@code
 * {"type":"create", "name":"玩家A", "difficulty":"NORMAL", "target":5000}
 * {"type":"join",   "room":"A3F9K2", "name":"玩家B"}
 * {"type":"score",  "room":"A3F9K2", "score":1200, "hp":900}
 * {"type":"leave",  "room":"A3F9K2"}
 * {"type":"ping"}
 * }</pre>
 *
 * <p>消息示例（服务端 → 客户端）：
 * <pre>{@code
 * {"type":"joined", "room":"A3F9K2", "you":"A", "opponent":"玩家B", "difficulty":"NORMAL", "target":5000}
 * {"type":"state",  "a":{"name":"玩家A","score":1200,"hp":900},
 *                   "b":{"name":"玩家B","score":950,"hp":1000}}
 * {"type":"over",   "winner":"A", "scoreA":5000, "scoreB":4210, "reason":"达到目标分"}
 * {"type":"opponent_left"}
 * {"type":"pong"}
 * {"type":"error",  "message":"房间不存在"}
 * }</pre>
 */
public final class BattleProtocol {

    // ---------- 客户端 → 服务端 ----------
    public static final String TYPE_CREATE = "create";     // 创建房间
    public static final String TYPE_JOIN = "join";         // 加入房间（也用于断线重连）
    public static final String TYPE_SCORE = "score";       // 每秒上报分数/血量
    public static final String TYPE_LEAVE = "leave";       // 主动离开
    public static final String TYPE_PING = "ping";         // 心跳

    // ---------- 服务端 → 客户端 ----------
    public static final String TYPE_JOINED = "joined";             // 加入成功，返回房间/你是哪边
    public static final String TYPE_STATE = "state";               // 双方最新分数/血量
    public static final String TYPE_OVER = "over";                 // 一局结束，胜负已定
    public static final String TYPE_OPPONENT_LEFT = "opponent_left"; // 对手离开/掉线
    public static final String TYPE_PONG = "pong";                 // 心跳应答
    public static final String TYPE_ERROR = "error";               // 出错（房间不存在等）

    /**
     * 默认服务器地址。
     * <p>Android 模拟器里 {@code 10.0.2.2} 指向宿主机（电脑）；真机调试要把它改成
     * 电脑在同一局域网里的 IP，例如 {@code ws://192.168.1.100:8765}。
     * 房间页里可以直接改，不必重新编译。
     */
    public static final String DEFAULT_SERVER_URL = "ws://10.0.2.2:8765";

    /** 默认目标分（先到者胜）。 */
    public static final int DEFAULT_TARGET = 5000;

    /** 房间码长度。 */
    public static final int ROOM_CODE_LENGTH = 6;

    /** 房间码字符集：去掉容易看混的 O / 0 / I / 1。 */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();

    private BattleProtocol() {
    }

    /** 生成 6 位房间码（大写字母 + 数字，排除 O/0/I/1）。 */
    public static String generateRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ---------------- JSON 编解码 ----------------

    public static String encode(JsonObject obj) {
        return GSON.toJson(obj);
    }

    /** 解析失败时返回空对象，避免一条坏消息把整个会话带崩。 */
    public static JsonObject parse(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static String typeOf(JsonObject obj) {
        return obj.has("type") ? obj.get("type").getAsString() : "";
    }

    /** 取字符串字段，缺失或 null 时返回 null。 */
    public static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    /** 取整数字段，缺失或非法时返回默认值。 */
    public static int integer(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return def;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }
}
