package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.GameBot;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.GameType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 게임 방 하나.
 *
 * <p>방의 상태를 바꾸는 모든 작업은 {@link #lock()}을 잡은 상태에서만 일어난다.
 * 그래서 같은 방에 요청이 동시에 몰려도 한 번에 하나씩 순서대로 처리되고,
 * 서로 다른 방끼리는 락을 공유하지 않아 서로를 기다리지 않는다.</p>
 */
public class Room {

    private final String code;
    private final GameType gameType;
    private final boolean solo;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<String, RoomPlayer> players = new LinkedHashMap<>();

    private String hostId;
    private RoomStatus status = RoomStatus.WAITING;
    private GameSession session;
    private GameBot bot;
    private List<String> lastRanking = List.of();
    private Instant lastActivityAt;
    private Instant autoStartAt;
    private Instant finishedAt;

    public Room(String code, GameType gameType, boolean solo, Instant now) {
        this.code = code;
        this.gameType = gameType;
        this.solo = solo;
        this.lastActivityAt = now;
    }

    public ReentrantLock lock() {
        return lock;
    }

    public String code() {
        return code;
    }

    public GameType gameType() {
        return gameType;
    }

    /** 혼자하기 방. 다른 사람이 들어올 수 없고 열린 방 목록에도 나오지 않는다. */
    public boolean solo() {
        return solo;
    }

    public String hostId() {
        return hostId;
    }

    public RoomStatus status() {
        return status;
    }

    public GameSession session() {
        return session;
    }

    public GameBot bot() {
        return bot;
    }

    public List<String> lastRanking() {
        return lastRanking;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    /** 자동 시작 게임에서 시작이 예약된 시각. 예약이 없으면 null */
    public Instant autoStartAt() {
        return autoStartAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public List<RoomPlayer> players() {
        return new ArrayList<>(players.values());
    }

    public List<RoomPlayer> humans() {
        return players.values().stream().filter(p -> !p.bot()).toList();
    }

    public Optional<RoomPlayer> findByToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return players.values().stream().filter(p -> !p.bot() && p.token().equals(token)).findFirst();
    }

    public Optional<RoomPlayer> findById(String playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    void addPlayer(RoomPlayer player) {
        players.put(player.id(), player);
        if (hostId == null && !player.bot()) {
            hostId = player.id();
        }
    }

    void addBot(GameBot bot, RoomPlayer botPlayer) {
        this.bot = bot;
        addPlayer(botPlayer);
    }

    void removePlayer(String playerId) {
        players.remove(playerId);
        if (playerId.equals(hostId)) {
            // 방장이 나가면 가장 먼저 들어온 사람이 방장을 이어받는다.
            hostId = humans().isEmpty() ? null : humans().get(0).id();
        }
    }

    void startGame(GameSession session) {
        this.session = session;
        this.status = RoomStatus.PLAYING;
        this.lastRanking = List.of();
        this.autoStartAt = null;
        this.finishedAt = null;
    }

    void finishGame(Instant now) {
        this.status = RoomStatus.FINISHED;
        this.lastRanking = session == null ? List.of() : session.ranking();
        this.finishedAt = now;
    }

    void resetToWaiting() {
        this.status = RoomStatus.WAITING;
        this.session = null;
        this.finishedAt = null;
    }

    void scheduleAutoStart(Instant at) {
        this.autoStartAt = at;
    }

    void touch(Instant now) {
        this.lastActivityAt = now;
    }

    boolean hasNoHumans() {
        return humans().isEmpty();
    }

    long connectedHumanCount() {
        return players.values().stream().filter(p -> !p.bot() && p.connected()).count();
    }
}
