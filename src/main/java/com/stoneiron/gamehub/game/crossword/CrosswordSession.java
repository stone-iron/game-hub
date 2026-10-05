package com.stoneiron.gamehub.game.crossword;

import com.stoneiron.gamehub.game.ActionResult;
import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.Payloads;
import com.stoneiron.gamehub.game.PlayerInfo;
import com.stoneiron.gamehub.game.crossword.Puzzle.Word;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 십자격돌(자유 점령 모드). 모두가 동시에 풀고, 단어를 가장 먼저 맞힌 사람이 그 단어를 차지한다.
 *
 * <ul>
 *     <li>정답 글자는 맞히기 전까지 절대 클라이언트로 보내지 않는다.</li>
 *     <li>같은 단어에 거의 동시에 정답이 들어오면, 방 단위 락 덕분에 먼저 처리된 한 명만 점수를 얻는다.</li>
 *     <li>오답을 내면 잠시 답을 낼 수 없어서, 아무 글자나 빠르게 넣어 보는 무차별 대입을 막는다.</li>
 * </ul>
 */
public class CrosswordSession implements GameSession {

    public static final Duration TIME_LIMIT = Duration.ofMinutes(20);
    public static final Duration WRONG_ANSWER_COOLDOWN = Duration.ofSeconds(2);
    static final int POINTS_PER_LETTER = 10;
    static final int COMBO_BONUS = 5;

    enum Status { OPEN, CLAIMED, AUTO }

    private final Puzzle puzzle;
    private final List<PlayerInfo> players;
    private final Instant endsAt;
    private final boolean[][] revealed;
    private final Map<String, Status> status = new LinkedHashMap<>();
    private final Map<String, String> owners = new HashMap<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<String, Integer> claimedCount = new HashMap<>();
    private final Map<String, Integer> combo = new HashMap<>();
    private final Map<String, Instant> cooldownUntil = new HashMap<>();
    private final Set<String> leftPlayers = new HashSet<>();

    private boolean finished;

    public CrosswordSession(Puzzle puzzle, List<PlayerInfo> players, Instant now) {
        if (players.isEmpty()) {
            throw new GameException("참가자가 없습니다.");
        }
        this.puzzle = puzzle;
        this.players = List.copyOf(players);
        this.endsAt = now.plus(TIME_LIMIT);
        this.revealed = new boolean[puzzle.size()][puzzle.size()];
        puzzle.words().forEach(w -> status.put(w.id(), Status.OPEN));
        players.forEach(p -> {
            scores.put(p.id(), 0);
            claimedCount.put(p.id(), 0);
        });
    }

    @Override
    public ActionResult handle(String playerId, String type, Map<String, Object> data, Instant now) {
        if (!scores.containsKey(playerId) || leftPlayers.contains(playerId)) {
            throw new GameException("이 게임의 참가자가 아닙니다.");
        }
        if (finished) {
            throw new GameException("이미 끝난 게임입니다.");
        }
        if (!"answer".equals(type)) {
            throw new GameException("알 수 없는 행동입니다: " + type);
        }
        return answer(playerId, Payloads.text(data, "wordId"), Payloads.text(data, "text"), now);
    }

    private ActionResult answer(String playerId, String wordId, String text, Instant now) {
        Word word = puzzle.word(wordId);
        if (word == null) {
            throw new GameException("없는 문제입니다.");
        }
        Instant blockedUntil = cooldownUntil.get(playerId);
        if (blockedUntil != null && now.isBefore(blockedUntil)) {
            throw new GameException("오답 후에는 잠시 기다려야 합니다.");
        }
        if (status.get(wordId) != Status.OPEN) {
            throw new GameException("이미 풀린 단어입니다.");
        }
        if (!normalize(text).equals(word.answer())) {
            combo.put(playerId, 0);
            cooldownUntil.put(playerId, now.plus(WRONG_ANSWER_COOLDOWN));
            throw new GameException("오답입니다. 2초 뒤에 다시 도전할 수 있어요.");
        }

        int currentCombo = combo.merge(playerId, 1, Integer::sum);
        int points = word.length() * POINTS_PER_LETTER + (currentCombo - 1) * COMBO_BONUS;
        status.put(wordId, Status.CLAIMED);
        owners.put(wordId, playerId);
        scores.merge(playerId, points, Integer::sum);
        claimedCount.merge(playerId, 1, Integer::sum);
        reveal(word);
        List<String> autoSolved = autoCompleteWords();

        if (status.values().stream().noneMatch(s -> s == Status.OPEN)) {
            finished = true;
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "claimed");
        event.put("wordId", wordId);
        event.put("number", puzzle.numberOf(wordId));
        event.put("playerId", playerId);
        event.put("nickname", nicknameOf(playerId));
        event.put("points", points);
        event.put("combo", currentCombo);
        event.put("autoSolved", autoSolved);
        return ActionResult.changed(List.of(event));
    }

    private void reveal(Word word) {
        for (int i = 0; i < word.length(); i++) {
            revealed[word.rowAt(i)][word.colAt(i)] = true;
        }
    }

    /** 교차 글자로 모든 칸이 공개된 단어는 주인 없이 자동으로 풀린 것으로 처리한다. */
    private List<String> autoCompleteWords() {
        List<String> solved = new ArrayList<>();
        for (Word word : puzzle.words()) {
            if (status.get(word.id()) != Status.OPEN) {
                continue;
            }
            boolean allRevealed = true;
            for (int i = 0; i < word.length(); i++) {
                if (!revealed[word.rowAt(i)][word.colAt(i)]) {
                    allRevealed = false;
                    break;
                }
            }
            if (allRevealed) {
                status.put(word.id(), Status.AUTO);
                solved.add(word.id());
            }
        }
        return solved;
    }

    @Override
    public ActionResult tick(Instant now) {
        if (!finished && !now.isBefore(endsAt)) {
            finished = true;
            return ActionResult.changed();
        }
        return ActionResult.none();
    }

    @Override
    public ActionResult onPlayerLeft(String playerId, Instant now) {
        if (finished || !scores.containsKey(playerId)) {
            return ActionResult.none();
        }
        leftPlayers.add(playerId);
        if (leftPlayers.size() == players.size()) {
            finished = true;
        }
        return ActionResult.changed();
    }

    static String normalize(String text) {
        return text.replaceAll("\\s+", "");
    }

    private String nicknameOf(String playerId) {
        return players.stream().filter(p -> p.id().equals(playerId)).map(PlayerInfo::nickname).findFirst().orElse("?");
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public Map<String, Object> publicState() {
        int size = puzzle.size();
        List<List<Map<String, Object>>> grid = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (int c = 0; c < size; c++) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("block", puzzle.isBlock(r, c));
                // 공개된 칸만 글자를 담는다. 끝난 게임은 정답 확인을 위해 모두 공개한다.
                boolean show = !puzzle.isBlock(r, c) && (revealed[r][c] || finished);
                cell.put("letter", show ? puzzle.letterAt(r, c) : null);
                row.add(cell);
            }
            grid.add(row);
        }
        for (Word w : puzzle.words()) {
            Map<String, Object> start = grid.get(w.row()).get(w.col());
            start.put("number", puzzle.numberOf(w.id()));
        }

        List<Map<String, Object>> clues = new ArrayList<>();
        puzzle.words().stream()
                .sorted(Comparator.comparing(Word::direction).thenComparingInt(w -> puzzle.numberOf(w.id())))
                .forEach(w -> {
                    Map<String, Object> clue = new LinkedHashMap<>();
                    clue.put("wordId", w.id());
                    clue.put("number", puzzle.numberOf(w.id()));
                    clue.put("direction", w.direction().name());
                    clue.put("row", w.row());
                    clue.put("col", w.col());
                    clue.put("length", w.length());
                    clue.put("clue", w.clue());
                    clue.put("status", status.get(w.id()).name());
                    clue.put("ownerId", owners.get(w.id()));
                    clues.add(clue);
                });

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("puzzleTitle", puzzle.title());
        state.put("size", size);
        state.put("grid", grid);
        state.put("clues", clues);
        state.put("scores", new LinkedHashMap<>(scores));
        state.put("claimedCount", new HashMap<>(claimedCount));
        state.put("endsAt", endsAt.toEpochMilli());
        state.put("finished", finished);
        return state;
    }

    /** 차지한 단어 수가 많은 순, 같으면 점수가 높은 순. */
    @Override
    public List<String> ranking() {
        return players.stream()
                .map(PlayerInfo::id)
                .filter(id -> !leftPlayers.contains(id))
                .sorted(Comparator.<String>comparingInt(id -> claimedCount.get(id)).reversed()
                        .thenComparing(Comparator.<String>comparingInt(id -> scores.get(id)).reversed()))
                .toList();
    }
}
