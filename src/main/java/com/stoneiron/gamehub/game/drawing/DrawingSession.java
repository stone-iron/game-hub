package com.stoneiron.gamehub.game.drawing;

import com.stoneiron.gamehub.game.ActionResult;
import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.Payloads;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 그림맞추기. 출제자가 제시어를 그리면 나머지가 채팅으로 정답을 맞힌다.
 *
 * <ul>
 *     <li>제시어는 출제자에게만 privateState로 보낸다. publicState에는 글자 수만 담는다.</li>
 *     <li>정답 판정과 점수 계산은 서버에서 한다.</li>
 *     <li>그림 획은 상태 전체를 다시 보내지 않고 이벤트로만 전달해 전송량을 줄인다.</li>
 *     <li>그리기 도구(펜·지우개·채우기·직선·사각형·원)는 "그리기 명령"으로 저장하고, 모두가 같은 순서로 다시 그린다.
 *         펜처럼 여러 조각으로 나눠 보내는 획은 같은 group 번호로 묶어 되돌리기 한 번에 함께 지운다.</li>
 *     <li>게임 중에 들어온 사람은 그리는 순서 맨 뒤에 추가된다.</li>
 * </ul>
 */
public class DrawingSession implements GameSession {

    public static final Duration TURN_TIME = Duration.ofSeconds(80);
    public static final Duration REVEAL_TIME = Duration.ofSeconds(4);
    static final int MAX_STROKES = 3000;
    static final int MAX_POINTS_PER_STROKE = 300;
    static final int MAX_CHAT_LENGTH = 50;
    static final Set<String> TOOLS = Set.of("pen", "eraser", "line", "rect", "circle", "fill");

    enum Phase { DRAWING, REVEAL, FINISHED }

    private final List<PlayerInfo> players;
    private final List<String> words;
    private final Deque<List<Map<String, Object>>> redoStack = new ArrayDeque<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Set<String> leftPlayers = new HashSet<>();
    private final Set<String> guessed = new LinkedHashSet<>();
    private final List<Map<String, Object>> strokes = new ArrayList<>();

    private int turn = -1;
    private Phase phase = Phase.DRAWING;
    private Instant phaseEndsAt;
    private String lastWord;

    public DrawingSession(List<PlayerInfo> players, List<String> words, Instant now) {
        if (players.size() < 2) {
            throw new GameException("그림맞추기는 2명 이상이 필요합니다.");
        }
        if (words.size() < players.size()) {
            throw new IllegalArgumentException("제시어 수가 참가자 수보다 적습니다.");
        }
        this.players = new ArrayList<>(players);
        this.words = List.copyOf(words);
        players.forEach(p -> scores.put(p.id(), 0));
        startNextTurn(now);
    }

    @Override
    public ActionResult handle(String playerId, String type, Map<String, Object> data, Instant now) {
        requireParticipant(playerId);
        if (phase == Phase.FINISHED) {
            throw new GameException("이미 끝난 게임입니다.");
        }
        return switch (type) {
            case "stroke" -> stroke(playerId, data);
            case "clear" -> clear(playerId);
            case "undo" -> undo(playerId);
            case "redo" -> redo(playerId);
            case "guess" -> guess(playerId, Payloads.text(data, "text"), now);
            default -> throw new GameException("알 수 없는 행동입니다: " + type);
        };
    }

    private ActionResult stroke(String playerId, Map<String, Object> data) {
        requireDrawerWhileDrawing(playerId);
        if (strokes.size() >= MAX_STROKES) {
            throw new GameException("더 이상 그릴 수 없습니다. 지우고 다시 그려 주세요.");
        }
        Map<String, Object> stroke = sanitizeStroke(data);
        strokes.add(stroke);
        redoStack.clear();
        return ActionResult.eventsOnly(List.of(event("stroke", "stroke", stroke)));
    }

    private ActionResult clear(String playerId) {
        requireDrawerWhileDrawing(playerId);
        strokes.clear();
        redoStack.clear();
        return ActionResult.eventsOnly(List.of(redrawEvent()));
    }

    /** 마지막으로 그린 한 덩어리(같은 group)를 지운다. */
    private ActionResult undo(String playerId) {
        requireDrawerWhileDrawing(playerId);
        if (strokes.isEmpty()) {
            return ActionResult.none();
        }
        Object lastGroup = strokes.get(strokes.size() - 1).get("group");
        List<Map<String, Object>> removed = new ArrayList<>();
        while (!strokes.isEmpty() && Objects.equals(strokes.get(strokes.size() - 1).get("group"), lastGroup)) {
            removed.add(0, strokes.remove(strokes.size() - 1));
        }
        redoStack.push(removed);
        return ActionResult.eventsOnly(List.of(redrawEvent()));
    }

    private ActionResult redo(String playerId) {
        requireDrawerWhileDrawing(playerId);
        if (redoStack.isEmpty()) {
            return ActionResult.none();
        }
        strokes.addAll(redoStack.pop());
        return ActionResult.eventsOnly(List.of(redrawEvent()));
    }

    private Map<String, Object> redrawEvent() {
        Map<String, Object> redraw = new LinkedHashMap<>();
        redraw.put("type", "redraw");
        redraw.put("strokes", List.copyOf(strokes));
        return redraw;
    }

    private ActionResult guess(String playerId, String rawText, Instant now) {
        String text = rawText.strip();
        if (text.isEmpty()) {
            throw new GameException("내용을 입력해 주세요.");
        }
        if (text.length() > MAX_CHAT_LENGTH) {
            text = text.substring(0, MAX_CHAT_LENGTH);
        }

        boolean drawing = phase == Phase.DRAWING;
        String answer = normalize(currentWord());
        boolean mentionsAnswer = drawing && normalize(text).contains(answer);

        if (drawing && !playerId.equals(drawerId()) && !guessed.contains(playerId) && normalize(text).equals(answer)) {
            return correctGuess(playerId, now);
        }
        // 출제자나 이미 맞힌 사람이 정답을 채팅에 흘리지 못하게 막는다.
        if (mentionsAnswer && (playerId.equals(drawerId()) || guessed.contains(playerId))) {
            throw new GameException("정답이 포함된 메시지는 보낼 수 없습니다.");
        }
        return ActionResult.eventsOnly(List.of(chatEvent(playerId, text)));
    }

    private ActionResult correctGuess(String playerId, Instant now) {
        guessed.add(playerId);
        long remaining = Math.max(0, Duration.between(now, phaseEndsAt).toSeconds());
        int guesserPoints = 50 + (int) remaining;
        scores.merge(playerId, guesserPoints, Integer::sum);
        scores.merge(drawerId(), 20, Integer::sum);

        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> correct = new LinkedHashMap<>();
        correct.put("type", "correct");
        correct.put("playerId", playerId);
        correct.put("nickname", nicknameOf(playerId));
        correct.put("points", guesserPoints);
        events.add(correct);

        if (guessed.size() >= activeGuesserCount()) {
            events.addAll(beginReveal(now).events());
        }
        return ActionResult.changed(events);
    }

    @Override
    public ActionResult tick(Instant now) {
        if (phase == Phase.FINISHED || now.isBefore(phaseEndsAt)) {
            return ActionResult.none();
        }
        if (phase == Phase.DRAWING) {
            return beginReveal(now);
        }
        startNextTurn(now);
        return ActionResult.changed();
    }

    @Override
    public ActionResult onPlayerJoined(PlayerInfo player, Instant now) {
        if (phase == Phase.FINISHED) {
            throw new GameException("이미 끝난 게임입니다.");
        }
        if (players.size() >= words.size()) {
            throw new GameException("이번 판에는 더 들어올 수 없어요. 다음 판에 함께해요.");
        }
        players.add(player);
        scores.put(player.id(), 0);
        return ActionResult.changed();
    }

    @Override
    public ActionResult onPlayerLeft(String playerId, Instant now) {
        if (phase == Phase.FINISHED || !scores.containsKey(playerId)) {
            return ActionResult.none();
        }
        leftPlayers.add(playerId);
        guessed.remove(playerId);
        if (activePlayers().size() < 2) {
            phase = Phase.FINISHED;
            return ActionResult.changed();
        }
        if (phase == Phase.DRAWING && (playerId.equals(drawerId()) || guessed.size() >= activeGuesserCount())) {
            return beginReveal(now);
        }
        return ActionResult.changed();
    }

    private ActionResult beginReveal(Instant now) {
        phase = Phase.REVEAL;
        phaseEndsAt = now.plus(REVEAL_TIME);
        lastWord = currentWord();
        return ActionResult.changed(List.of(Map.of("type", "reveal", "word", lastWord)));
    }

    private void startNextTurn(Instant now) {
        strokes.clear();
        redoStack.clear();
        guessed.clear();
        do {
            turn++;
        } while (turn < players.size() && leftPlayers.contains(players.get(turn).id()));

        if (turn >= players.size()) {
            phase = Phase.FINISHED;
            return;
        }
        phase = Phase.DRAWING;
        phaseEndsAt = now.plus(TURN_TIME);
    }

    private Map<String, Object> sanitizeStroke(Map<String, Object> data) {
        String tool = data != null && data.get("tool") instanceof String t ? t : "pen";
        if (!TOOLS.contains(tool)) {
            throw new GameException("알 수 없는 그리기 도구입니다.");
        }
        Object rawPoints = data == null ? null : data.get("points");
        if (!(rawPoints instanceof List<?> list) || list.isEmpty()) {
            throw new GameException("그림 좌표가 비어 있습니다.");
        }
        List<List<Double>> points = new ArrayList<>();
        for (Object item : list) {
            if (points.size() >= MAX_POINTS_PER_STROKE) {
                break;
            }
            if (item instanceof List<?> pair && pair.size() == 2
                    && pair.get(0) instanceof Number x && pair.get(1) instanceof Number y) {
                points.add(List.of(clamp(x.doubleValue()), clamp(y.doubleValue())));
            }
        }
        int required = switch (tool) {
            case "fill" -> 1;
            case "line", "rect", "circle" -> 2;
            default -> 1;
        };
        if (points.size() < required) {
            throw new GameException("그림 좌표 형식이 올바르지 않습니다.");
        }
        if (!tool.equals("pen") && !tool.equals("eraser")) {
            points = points.subList(0, required);
        }
        String color = data.get("color") instanceof String c && c.matches("#[0-9a-fA-F]{6}") ? c : "#222222";
        int width = data.get("width") instanceof Number w ? Math.max(1, Math.min(40, w.intValue())) : 4;
        int group = data.get("group") instanceof Number g ? g.intValue() : strokes.size();

        Map<String, Object> stroke = new LinkedHashMap<>();
        stroke.put("tool", tool);
        stroke.put("points", List.copyOf(points));
        stroke.put("color", tool.equals("eraser") ? "#ffffff" : color);
        stroke.put("width", width);
        stroke.put("group", group);
        return stroke;
    }

    /** 좌표는 캔버스 크기와 무관하게 0~1 비율로 주고받는다. */
    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    static String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase();
    }

    private void requireDrawerWhileDrawing(String playerId) {
        if (phase != Phase.DRAWING || !playerId.equals(drawerId())) {
            throw new GameException("지금은 그림을 그릴 수 없습니다.");
        }
    }

    private void requireParticipant(String playerId) {
        if (!scores.containsKey(playerId) || leftPlayers.contains(playerId)) {
            throw new GameException("이 게임의 참가자가 아닙니다.");
        }
    }

    private String drawerId() {
        return players.get(turn).id();
    }

    private String currentWord() {
        return words.get(turn);
    }

    private List<PlayerInfo> activePlayers() {
        return players.stream().filter(p -> !leftPlayers.contains(p.id())).toList();
    }

    private int activeGuesserCount() {
        return activePlayers().size() - 1;
    }

    private String nicknameOf(String playerId) {
        return players.stream().filter(p -> p.id().equals(playerId)).map(PlayerInfo::nickname).findFirst().orElse("?");
    }

    private Map<String, Object> chatEvent(String playerId, String text) {
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("type", "chat");
        chat.put("playerId", playerId);
        chat.put("nickname", nicknameOf(playerId));
        chat.put("text", text);
        return chat;
    }

    private static Map<String, Object> event(String type, String key, Object value) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", type);
        e.put(key, value);
        return e;
    }

    @Override
    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    @Override
    public Map<String, Object> publicState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("phase", phase.name());
        state.put("round", Math.min(turn + 1, players.size()));
        state.put("totalRounds", players.size());
        boolean active = phase != Phase.FINISHED;
        state.put("drawerId", active ? drawerId() : null);
        state.put("wordLength", phase == Phase.DRAWING ? currentWord().length() : null);
        state.put("revealedWord", phase == Phase.REVEAL ? lastWord : null);
        state.put("phaseEndsAt", active ? phaseEndsAt.toEpochMilli() : null);
        state.put("guessed", List.copyOf(guessed));
        state.put("scores", new LinkedHashMap<>(scores));
        // 재접속한 사람도 지금까지 그린 그림을 볼 수 있도록 현재 획을 함께 보낸다.
        state.put("strokes", phase == Phase.DRAWING ? List.copyOf(strokes) : List.of());
        return state;
    }

    @Override
    public Map<String, Object> privateState(String playerId) {
        if (phase == Phase.DRAWING && playerId.equals(drawerId())) {
            return Map.of("word", currentWord());
        }
        return Map.of();
    }

    @Override
    public List<String> ranking() {
        return scores.entrySet().stream()
                .filter(e -> !leftPlayers.contains(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
