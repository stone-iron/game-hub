package com.stoneiron.gamehub.game;

import com.stoneiron.gamehub.game.crossword.CrosswordSession;
import com.stoneiron.gamehub.game.crossword.Puzzle;
import com.stoneiron.gamehub.game.crossword.PuzzleBank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.stoneiron.gamehub.game.crossword.Puzzle.Direction.ACROSS;
import static com.stoneiron.gamehub.game.crossword.Puzzle.Direction.DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrosswordSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final PlayerInfo A = new PlayerInfo("a", "에이");
    private static final PlayerInfo B = new PlayerInfo("b", "비");

    private static Map<String, Object> answer(String wordId, String text) {
        return Map.of("wordId", wordId, "text", text);
    }

    @Test
    @DisplayName("기본 퍼즐은 모두 교차 글자가 맞는다")
    void bundledPuzzlesAreValid() {
        List<Puzzle> puzzles = new PuzzleBank().all();
        assertEquals(2, puzzles.size());
        puzzles.forEach(p -> assertEquals(14, p.words().size()));
    }

    @Test
    @DisplayName("교차 글자가 맞지 않는 퍼즐은 만들 수 없다")
    void rejectsInconsistentPuzzle() {
        assertThrows(IllegalStateException.class, () -> new Puzzle("bad", "잘못된 퍼즐", 3, List.of(
                new Puzzle.Word("a", ACROSS, 0, 0, "사과", "과일"),
                new Puzzle.Word("d", DOWN, 0, 0, "포도", "과일"))));
    }

    @Test
    @DisplayName("맞히기 전에는 정답 글자를 공개 상태에 담지 않는다")
    void answersAreHidden() {
        CrosswordSession game = new CrosswordSession(PuzzleBank.everyday(), List.of(A, B), NOW);
        assertFalse(game.publicState().toString().contains("고구마"));
    }

    @Test
    @DisplayName("먼저 맞힌 사람이 단어를 차지하고, 같은 단어는 다시 차지할 수 없다")
    void firstCorrectAnswerClaims() {
        CrosswordSession game = new CrosswordSession(PuzzleBank.everyday(), List.of(A, B), NOW);
        game.handle("b", "answer", answer("a1", "고구마"), NOW);

        assertThrows(GameException.class, () -> game.handle("a", "answer", answer("a1", "고구마"), NOW));
        assertEquals("고", letterAt(game, 0, 0));
        assertEquals("마", letterAt(game, 0, 2));
        assertEquals(List.of("b", "a"), game.ranking());
    }

    @SuppressWarnings("unchecked")
    private static Object letterAt(CrosswordSession game, int row, int col) {
        List<List<Map<String, Object>>> grid = (List<List<Map<String, Object>>>) game.publicState().get("grid");
        return grid.get(row).get(col).get("letter");
    }

    @Test
    @DisplayName("오답을 내면 잠시 답을 낼 수 없다 (무차별 대입 방지)")
    void wrongAnswerCooldown() {
        CrosswordSession game = new CrosswordSession(PuzzleBank.everyday(), List.of(A, B), NOW);
        assertThrows(GameException.class, () -> game.handle("a", "answer", answer("a1", "감자"), NOW));
        assertThrows(GameException.class, () -> game.handle("a", "answer", answer("a1", "고구마"), NOW.plusSeconds(1)));

        game.handle("a", "answer", answer("a1", "고구마"), NOW.plus(CrosswordSession.WRONG_ANSWER_COOLDOWN));
        assertEquals("a", game.ranking().get(0));
    }

    @Test
    @DisplayName("모든 단어가 풀리면 게임이 끝난다")
    void finishesWhenAllSolved() {
        Puzzle puzzle = PuzzleBank.everyday();
        CrosswordSession game = new CrosswordSession(puzzle, List.of(A, B), NOW);
        for (Puzzle.Word word : puzzle.words()) {
            game.handle("a", "answer", answer(word.id(), word.answer()), NOW);
        }
        assertTrue(game.isFinished());
    }

    @Test
    @DisplayName("20분이 지나면 게임이 끝난다")
    void timeLimit() {
        CrosswordSession game = new CrosswordSession(PuzzleBank.everyday(), List.of(A, B), NOW);
        game.tick(NOW.plus(CrosswordSession.TIME_LIMIT));
        assertTrue(game.isFinished());
    }
}
