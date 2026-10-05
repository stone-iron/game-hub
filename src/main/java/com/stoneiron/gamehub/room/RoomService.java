package com.stoneiron.gamehub.room;

import com.stoneiron.gamehub.game.ActionResult;
import com.stoneiron.gamehub.game.GameBot;
import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameRule;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.GameType;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 방 생성·입장·퇴장, 게임 시작, 행동 처리, 채팅, 재접속, 타이머, 컴퓨터 상대를 담당한다.
 *
 * <p><b>동시성 원칙:</b> 방 하나의 상태를 바꾸는 모든 작업은 {@link #withRoom}을 통해 방 단위 락 안에서 실행된다.
 * 같은 방에 들어온 요청은 도착한 순서대로 하나씩 처리되므로, 십자격돌에서 두 사람이 거의 동시에
 * 같은 단어의 정답을 보내도 먼저 처리된 한 사람만 그 단어를 차지한다.</p>
 *
 * <p>이 클래스는 Spring에 의존하지 않는다. 메시지 전송은 {@link RoomNotifier}에 맡기고,
 * 시간은 {@link Clock}으로 주입받아 테스트에서 시간을 마음대로 움직일 수 있다.</p>
 */
public class RoomService {

    /** 대기실에서 연결이 끊긴 사람을 내보내기까지 기다리는 시간 (새로고침은 이 안에 돌아온다) */
    static final Duration WAITING_RECONNECT_GRACE = Duration.ofSeconds(20);
    /** 게임 중 연결이 끊긴 사람을 기권 처리하기까지 기다리는 시간 */
    static final Duration PLAYING_RECONNECT_GRACE = Duration.ofSeconds(60);
    /** 자동 시작 게임에서 인원이 모인 뒤 시작하기까지 기다리는 시간 */
    static final Duration AUTO_START_DELAY = Duration.ofSeconds(3);
    /** 자동 시작 게임에서 결과를 보여주고 다시 대기실로 돌아가기까지의 시간 */
    static final Duration RESULT_DISPLAY_TIME = Duration.ofSeconds(8);
    static final int MAX_NICKNAME_LENGTH = 12;
    static final int MAX_CHAT_LENGTH = 50;
    private static final System.Logger LOG = System.getLogger(RoomService.class.getName());

    private final Map<GameType, GameRule> rules = new EnumMap<>(GameType.class);
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, SessionRef> sessions = new ConcurrentHashMap<>();
    private final RoomNotifier notifier;
    private final Clock clock;
    private final RoomCodeGenerator codeGenerator;

    record SessionRef(String code, String playerId) {
    }

    public RoomService(Collection<GameRule> gameRules, RoomNotifier notifier, Clock clock,
                       RoomCodeGenerator codeGenerator) {
        gameRules.forEach(rule -> rules.put(rule.type(), rule));
        this.notifier = notifier;
        this.clock = clock;
        this.codeGenerator = codeGenerator;
    }

    // ---------------------------------------------------------------- 방 생성 · 입장 · 퇴장

    public JoinResult create(GameType gameType, String nickname) {
        GameRule rule = requireRule(gameType);
        String name = validateNickname(nickname);
        Instant now = clock.instant();
        Room room = newRoom(rule.type(), false, now);
        return withRoom(room.code(), r -> addPlayer(r, name, now));
    }

    /**
     * 혼자하기. 컴퓨터 상대가 있는 게임은 컴퓨터를 자리에 앉히고, 혼자 푸는 게임은 그대로 바로 시작한다.
     *
     * @param level 컴퓨터 난이도 (예: 오목 BEGINNER / INTERMEDIATE / ADVANCED)
     */
    public JoinResult createSolo(GameType gameType, String nickname, String level) {
        GameRule rule = requireRule(gameType);
        if (!gameType.soloPlayable()) {
            throw new GameException(gameType.displayName() + "은(는) 혼자 할 수 없는 게임입니다.");
        }
        String name = validateNickname(nickname);
        Instant now = clock.instant();
        Room room = newRoom(gameType, true, now);
        try {
            return withRoom(room.code(), r -> {
                JoinResult me = addPlayer(r, name, now);
                String botId = "bot-" + UUID.randomUUID().toString().substring(0, 6);
                Optional<GameBot> bot = rule.createBot(botId, level);
                bot.ifPresent(b -> r.addBot(b, RoomPlayer.bot(botId, b.nickname(), UUID.randomUUID().toString())));
                if (r.players().size() < gameType.minPlayers()) {
                    throw new GameException(gameType.displayName() + "은(는) 컴퓨터 상대가 없어 혼자 할 수 없습니다.");
                }
                startSession(r, now);
                return me;
            });
        } catch (RuntimeException e) {
            rooms.remove(room.code()); // 준비 중 실패하면 만들다 만 방을 남기지 않는다.
            throw e;
        }
    }

    private Room newRoom(GameType gameType, boolean solo, Instant now) {
        synchronized (rooms) {
            String code = codeGenerator.generate(rooms::containsKey);
            Room room = new Room(code, gameType, solo, now);
            rooms.put(code, room);
            return room;
        }
    }

    public JoinResult join(String code, String nickname) {
        String name = validateNickname(nickname);
        Instant now = clock.instant();
        return withRoom(code, room -> {
            if (room.solo()) {
                throw new GameException("혼자하기 중인 방에는 들어갈 수 없습니다.");
            }
            boolean playing = room.status() == RoomStatus.PLAYING;
            if (playing && !room.gameType().joinWhilePlaying()) {
                throw new GameException("이미 게임이 진행 중인 방입니다.");
            }
            if (room.players().size() >= room.gameType().maxPlayers()) {
                throw new GameException("방이 가득 찼습니다.");
            }
            boolean duplicated = room.players().stream().anyMatch(p -> p.nickname().equals(name));
            if (duplicated) {
                throw new GameException("이미 같은 닉네임이 방에 있습니다.");
            }
            JoinResult result = addPlayer(room, name, now);
            if (playing) {
                ActionResult joined = room.session().onPlayerJoined(new PlayerInfo(result.playerId(), name), now);
                afterGameUpdate(room, joined, now);
            }
            system(room, name + "님이 들어왔어요.");
            broadcast(room);
            return result;
        });
    }

    private JoinResult addPlayer(Room room, String nickname, Instant now) {
        String playerId = UUID.randomUUID().toString().substring(0, 8);
        String token = UUID.randomUUID().toString();
        room.addPlayer(new RoomPlayer(playerId, nickname, token, now));
        room.touch(now);
        return new JoinResult(room.code(), playerId, token, room.gameType().name());
    }

    public void leave(String code, String token) {
        Instant now = clock.instant();
        withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            removePlayer(room, player, now);
            return null;
        });
    }

    private void removePlayer(Room room, RoomPlayer player, Instant now) {
        sessions.values().removeIf(ref -> ref.playerId().equals(player.id()));
        if (room.status() == RoomStatus.PLAYING && room.session() != null) {
            ActionResult result = room.session().onPlayerLeft(player.id(), now);
            room.removePlayer(player.id());
            if (!room.hasNoHumans()) {
                afterGameUpdate(room, result, now);
            }
        } else {
            room.removePlayer(player.id());
        }
        room.touch(now);
        if (room.hasNoHumans()) {
            // 사람이 모두 나가면 컴퓨터만 남아도 방을 닫는다.
            rooms.remove(room.code());
            notifier.roomClosed(room.code());
        } else {
            system(room, player.nickname() + "님이 나갔어요.");
            broadcast(room);
        }
    }

    // ---------------------------------------------------------------- 연결 · 재접속

    /**
     * WebSocket 연결이 방을 구독했음을 알린다. 같은 토큰으로 다시 들어오면 원래 자리로 복귀한다.
     */
    public RoomView connect(String code, String token, String sessionId) {
        return withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            player.markConnected();
            sessions.put(sessionId, new SessionRef(code, player.id()));
            room.touch(clock.instant());
            broadcast(room);
            return toView(room);
        });
    }

    public void disconnect(String sessionId) {
        SessionRef ref = sessions.remove(sessionId);
        if (ref == null || !rooms.containsKey(ref.code())) {
            return;
        }
        boolean stillConnected = sessions.values().stream().anyMatch(other -> other.equals(ref));
        if (stillConnected) {
            return;
        }
        Instant now = clock.instant();
        withRoom(ref.code(), room -> {
            room.findById(ref.playerId()).ifPresent(p -> p.markDisconnected(now));
            broadcast(room);
            return null;
        });
    }

    // ---------------------------------------------------------------- 게임 진행

    public void start(String code, String token) {
        Instant now = clock.instant();
        withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            if (!player.id().equals(room.hostId())) {
                throw new GameException("방장만 게임을 시작할 수 있습니다.");
            }
            if (room.status() == RoomStatus.PLAYING) {
                throw new GameException("이미 게임이 진행 중입니다.");
            }
            startSession(room, now);
            return null;
        });
    }

    private void startSession(Room room, Instant now) {
        GameType type = room.gameType();
        if (room.players().size() < type.minPlayers()) {
            throw new GameException(type.displayName() + "은(는) " + type.minPlayers() + "명 이상이 필요합니다.");
        }
        List<PlayerInfo> infos = room.players().stream()
                .map(p -> new PlayerInfo(p.id(), p.nickname()))
                .toList();
        GameSession session = rules.get(type).create(infos, now);
        room.startGame(session);
        room.touch(now);
        broadcast(room);
    }

    public void action(String code, String token, String type, Map<String, Object> data) {
        Instant now = clock.instant();
        withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            requirePlaying(room);
            ActionResult result = room.session().handle(player.id(), type, data, now);
            room.touch(now);
            afterGameUpdate(room, result, now);
            return null;
        });
    }

    /**
     * 채팅. 그림맞추기 게임 중에는 채팅이 곧 정답 시도이므로 게임 규칙으로 넘긴다.
     * 그 밖에는 대기실·결과 화면 어디서든 방 전체에 보낸다.
     */
    public void chat(String code, String token, String text) {
        Instant now = clock.instant();
        withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            String message = text == null ? "" : text.strip();
            if (message.isEmpty()) {
                throw new GameException("내용을 입력해 주세요.");
            }
            if (message.length() > MAX_CHAT_LENGTH) {
                message = message.substring(0, MAX_CHAT_LENGTH);
            }
            room.touch(now);
            if (room.gameType() == GameType.DRAWING && room.status() == RoomStatus.PLAYING) {
                afterGameUpdate(room, room.session().handle(player.id(), "guess", Map.of("text", message), now), now);
                return null;
            }
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("type", "chat");
            chat.put("playerId", player.id());
            chat.put("nickname", player.nickname());
            chat.put("text", message);
            notifier.events(room.code(), List.of(chat));
            return null;
        });
    }

    /** 결과 화면에서 다시 대기실로 돌아간다. 혼자하기는 바로 새 판을 시작한다. */
    public void reset(String code, String token) {
        Instant now = clock.instant();
        withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            if (!player.id().equals(room.hostId())) {
                throw new GameException("방장만 다시 시작할 수 있습니다.");
            }
            if (room.status() != RoomStatus.FINISHED) {
                throw new GameException("게임이 끝난 뒤에 다시 시작할 수 있습니다.");
            }
            room.resetToWaiting();
            if (room.solo()) {
                startSession(room, now);
            } else {
                broadcast(room);
            }
            return null;
        });
    }

    private void afterGameUpdate(Room room, ActionResult result, Instant now) {
        if (!result.events().isEmpty()) {
            notifier.events(room.code(), result.events());
        }
        if (room.session().isFinished() && room.status() == RoomStatus.PLAYING) {
            room.finishGame(now);
            broadcast(room);
        } else if (result.stateChanged()) {
            broadcast(room);
        }
    }

    // ---------------------------------------------------------------- 타이머

    /**
     * 주기적으로 호출된다. 제한 시간, 연결이 끊긴 사람 정리, 컴퓨터 차례, 자동 시작을 처리한다.
     */
    public void tickAll() {
        Instant now = clock.instant();
        for (String code : List.copyOf(rooms.keySet())) {
            try {
                withRoom(code, room -> {
                    tickRoom(room, now);
                    return null;
                });
            } catch (GameException ignored) {
                // 그사이 방이 사라진 경우
            } catch (RuntimeException e) {
                // 한 방의 오류 때문에 다른 방의 타이머까지 멈추지 않도록 기록만 하고 넘어간다.
                LOG.log(System.Logger.Level.ERROR, "방 " + code + " 타이머 처리 중 오류", e);
            }
        }
    }

    private void tickRoom(Room room, Instant now) {
        Duration grace = room.status() == RoomStatus.PLAYING ? PLAYING_RECONNECT_GRACE : WAITING_RECONNECT_GRACE;
        for (RoomPlayer player : room.humans()) {
            if (!player.connected() && player.disconnectedAt() != null
                    && Duration.between(player.disconnectedAt(), now).compareTo(grace) >= 0) {
                removePlayer(room, player, now);
                if (!rooms.containsKey(room.code())) {
                    return;
                }
            }
        }
        if (room.status() == RoomStatus.PLAYING && room.session() != null) {
            afterGameUpdate(room, room.session().tick(now), now);
            playBotTurn(room, now);
        }
        if (room.gameType().autoStart()) {
            tickAutoStart(room, now);
        }
    }

    private void playBotTurn(Room room, Instant now) {
        GameBot bot = room.bot();
        if (bot == null || room.status() != RoomStatus.PLAYING) {
            return;
        }
        bot.decide(room.session(), now).ifPresent(action -> {
            try {
                afterGameUpdate(room, room.session().handle(bot.playerId(), action.type(), action.data(), now), now);
            } catch (GameException e) {
                LOG.log(System.Logger.Level.WARNING, "컴퓨터가 규칙에 맞지 않는 행동을 했습니다: " + e.getMessage());
            }
        });
    }

    /** 인원이 모이면 잠시 뒤 자동으로 시작하고, 끝난 판은 잠시 결과를 보여준 뒤 대기실로 돌린다. */
    private void tickAutoStart(Room room, Instant now) {
        if (room.status() == RoomStatus.FINISHED && room.finishedAt() != null
                && !now.isBefore(room.finishedAt().plus(RESULT_DISPLAY_TIME))) {
            room.resetToWaiting();
            broadcast(room);
        }
        if (room.status() != RoomStatus.WAITING) {
            return;
        }
        boolean enough = room.connectedHumanCount() >= room.gameType().minPlayers();
        if (enough && room.autoStartAt() == null) {
            room.scheduleAutoStart(now.plus(AUTO_START_DELAY));
            broadcast(room);
        } else if (enough && !now.isBefore(room.autoStartAt())) {
            startSession(room, now);
        } else if (!enough && room.autoStartAt() != null) {
            room.scheduleAutoStart(null);
            broadcast(room);
        }
    }

    // ---------------------------------------------------------------- 조회

    public RoomView view(String code, String token) {
        return withRoom(code, room -> {
            requirePlayer(room, token);
            return toView(room);
        });
    }

    public Map<String, Object> privateState(String code, String token) {
        return withRoom(code, room -> {
            RoomPlayer player = requirePlayer(room, token);
            return room.session() == null ? Map.of() : room.session().privateState(player.id());
        });
    }

    public List<RoomSummary> openRooms() {
        List<RoomSummary> result = new ArrayList<>();
        for (Room room : rooms.values()) {
            room.lock().lock();
            try {
                GameType type = room.gameType();
                boolean joinable = room.status() != RoomStatus.PLAYING || type.joinWhilePlaying();
                if (!room.solo() && joinable && !room.hasNoHumans() && room.players().size() < type.maxPlayers()) {
                    String host = room.findById(room.hostId()).map(RoomPlayer::nickname).orElse("?");
                    result.add(new RoomSummary(room.code(), type.name(), type.displayName(), host,
                            room.players().size(), type.maxPlayers(), room.status() == RoomStatus.PLAYING));
                }
            } finally {
                room.lock().unlock();
            }
        }
        result.sort(Comparator.comparing(RoomSummary::code));
        return result;
    }

    int roomCount() {
        return rooms.size();
    }

    // ---------------------------------------------------------------- 내부 도우미

    /**
     * 방 단위 락을 잡고 작업을 실행한다. 이 메서드 밖에서 방 상태를 바꾸지 않는다.
     */
    <T> T withRoom(String code, Function<Room, T> work) {
        Room room = rooms.get(normalizeCode(code));
        if (room == null) {
            throw new GameException("방을 찾을 수 없습니다. 입장 코드를 확인해 주세요.");
        }
        room.lock().lock();
        try {
            if (rooms.get(room.code()) != room) {
                throw new GameException("이미 닫힌 방입니다.");
            }
            return work.apply(room);
        } finally {
            room.lock().unlock();
        }
    }

    private void broadcast(Room room) {
        notifier.roomUpdated(room.code(), toView(room));
        if (room.session() != null) {
            for (RoomPlayer player : room.humans()) {
                Map<String, Object> privateState = room.session().privateState(player.id());
                notifier.privateUpdated(room.code(), player.token(), privateState);
            }
        }
    }

    private void system(Room room, String text) {
        notifier.events(room.code(), List.of(Map.of("type", "system", "text", text)));
    }

    private RoomView toView(Room room) {
        List<RoomView.PlayerView> players = room.players().stream()
                .map(p -> new RoomView.PlayerView(p.id(), p.nickname(), p.connected(),
                        p.id().equals(room.hostId()), p.bot()))
                .toList();
        GameType type = room.gameType();
        return new RoomView(room.code(), type.name(), type.displayName(), room.status(), room.hostId(),
                type.minPlayers(), type.maxPlayers(), room.solo(), type.autoStart(),
                room.autoStartAt() == null ? null : room.autoStartAt().toEpochMilli(),
                players,
                room.session() == null ? null : room.session().publicState(),
                room.lastRanking());
    }

    private GameRule requireRule(GameType gameType) {
        GameRule rule = gameType == null ? null : rules.get(gameType);
        if (rule == null) {
            throw new GameException("지원하지 않는 게임입니다.");
        }
        return rule;
    }

    private static void requirePlaying(Room room) {
        if (room.status() != RoomStatus.PLAYING || room.session() == null) {
            throw new GameException("게임이 진행 중이 아닙니다.");
        }
    }

    private static RoomPlayer requirePlayer(Room room, String token) {
        return room.findByToken(token)
                .orElseThrow(() -> new GameException("이 방의 참가자가 아닙니다. 다시 입장해 주세요."));
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    static String validateNickname(String nickname) {
        String name = nickname == null ? "" : nickname.strip();
        if (name.isEmpty()) {
            throw new GameException("닉네임을 입력해 주세요.");
        }
        if (name.length() > MAX_NICKNAME_LENGTH) {
            throw new GameException("닉네임은 " + MAX_NICKNAME_LENGTH + "자 이하로 입력해 주세요.");
        }
        return name;
    }
}
