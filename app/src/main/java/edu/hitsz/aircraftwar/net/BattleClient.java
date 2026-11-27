package edu.hitsz.aircraftwar.net;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket 连接的薄封装（基于 OkHttp）。
 *
 * <p>只负责「建立连接、发文本、收文本、断开」，不关心业务含义；
 * 消息怎么解析、心跳怎么发、断线怎么重连，都交给 {@link BattleSession}。
 *
 * <p>注意：{@link Listener} 的回调跑在 OkHttp 的后台线程上，不是主线程，
 * 实现方要自己切换线程（BattleSession 里统一 post 回主线程）。
 */
public class BattleClient {

    private static final String TAG = "BattleClient";

    /** 连接层事件回调。 */
    public interface Listener {
        void onOpen();

        void onMessage(String text);

        void onClosed(int code, String reason);

        void onFailure(Throwable t);
    }

    private final OkHttpClient client;
    private WebSocket socket;

    public BattleClient() {
        // 连接超时设短一点，让连不上时能快速失败并走重连；
        // 读超时设为 0（不超时），因为 WebSocket 是长连接，平时可能很久没有数据。
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void connect(String url, Listener listener) {
        Request request = new Request.Builder().url(url).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                listener.onOpen();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                listener.onMessage(text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onFailure(t);
            }
        });
    }

    public boolean isOpen() {
        return socket != null;
    }

    public void send(String text) {
        if (socket != null) {
            socket.send(text);
        }
    }

    public void close() {
        if (socket != null) {
            WebSocket s = socket;
            socket = null;
            try {
                s.close(1000, "client close");
            } catch (Exception e) {
                Log.w(TAG, "close error", e);
            }
        }
    }
}
