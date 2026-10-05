package com.stoneiron.gamehub.room;

import java.time.Instant;

/**
 * 방에 들어온 플레이어.
 *
 * <p>로그인 없이도 플레이할 수 있도록 입장할 때 무작위 토큰을 발급한다.
 * 토큰은 본인에게만 전달되고, 다른 사람에게 보내는 방 정보에는 절대 담지 않는다.
 * 새로고침이나 연결 끊김 뒤에도 같은 토큰으로 다시 들어오면 같은 자리로 복귀한다.</p>
 *
 * <p>컴퓨터 플레이어(bot)는 연결이라는 개념이 없으므로 항상 접속한 것으로 본다.</p>
 */
public class RoomPlayer {

    private final String id;
    private final String nickname;
    private final String token;
    private final boolean bot;
    private boolean connected;
    private Instant disconnectedAt;

    public RoomPlayer(String id, String nickname, String token, Instant now) {
        this(id, nickname, token, false, now);
    }

    private RoomPlayer(String id, String nickname, String token, boolean bot, Instant now) {
        this.id = id;
        this.nickname = nickname;
        this.token = token;
        this.bot = bot;
        this.connected = bot;
        this.disconnectedAt = bot ? null : now;
    }

    static RoomPlayer bot(String id, String nickname, String token) {
        return new RoomPlayer(id, nickname, token, true, null);
    }

    public String id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public String token() {
        return token;
    }

    public boolean bot() {
        return bot;
    }

    public boolean connected() {
        return connected;
    }

    public Instant disconnectedAt() {
        return disconnectedAt;
    }

    void markConnected() {
        connected = true;
        disconnectedAt = null;
    }

    void markDisconnected(Instant now) {
        if (bot) {
            return;
        }
        connected = false;
        disconnectedAt = now;
    }
}
