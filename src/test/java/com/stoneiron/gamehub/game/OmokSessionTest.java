package com.stoneiron.gamehub.game;

import com.stoneiron.gamehub.game.omok.OmokSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmokSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final PlayerInfo BLACK = new PlayerInfo("black", "흑");
    private static final PlayerInfo WHITE = new PlayerInfo("white", "백");

    private OmokSession newGame() {
        return new OmokSession(List.of(BLACK, WHITE), NOW);
    }

    private static Map<String, Object> at(int x, int y) {
        return Map.of("x", x, "y", y);
    }

    @Test
    @DisplayName("가로로 다섯 개를 이으면 이긴다")
    void horizontalFiveWins() {
        OmokSession game = newGame();
        for (int i = 0; i < 4; i++) {
            game.handle("black", "place", at(i, 0), NOW);
            game.handle("white", "place", at(i, 1), NOW);
        }
        game.handle("black", "place", at(4, 0), NOW);

        assertTrue(game.isFinished());
        assertEquals("black", game.publicState().get("winnerId"));
        assertEquals(List.of("black", "white"), game.ranking());
    }

    @Test
    @DisplayName("대각선 다섯 개도 승리로 판정한다")
    void diagonalFiveWins() {
        OmokSession game = newGame();
        for (int i = 0; i < 4; i++) {
            game.handle("black", "place", at(i, i), NOW);
            game.handle("white", "place", at(i, 10), NOW);
        }
        game.handle("black", "place", at(4, 4), NOW);

        assertEquals("black", game.publicState().get("winnerId"));
    }

    @Test
    @DisplayName("자기 차례가 아니면 둘 수 없다")
    void rejectsOutOfTurn() {
        OmokSession game = newGame();
        assertThrows(GameException.class, () -> game.handle("white", "place", at(0, 0), NOW));
    }

    @Test
    @DisplayName("이미 돌이 있는 자리와 판 밖에는 둘 수 없다")
    void rejectsOccupiedAndOutside() {
        OmokSession game = newGame();
        game.handle("black", "place", at(7, 7), NOW);
        assertThrows(GameException.class, () -> game.handle("white", "place", at(7, 7), NOW));
        assertThrows(GameException.class, () -> game.handle("white", "place", at(15, 0), NOW));
        assertFalse(game.isFinished());
    }

    @Test
    @DisplayName("제한 시간 안에 두지 않으면 상대가 이긴다")
    void timeoutLoses() {
        OmokSession game = newGame();
        game.tick(NOW.plus(OmokSession.TURN_LIMIT).minusSeconds(1));
        assertFalse(game.isFinished());

        game.tick(NOW.plus(OmokSession.TURN_LIMIT));
        assertTrue(game.isFinished());
        assertEquals("white", game.publicState().get("winnerId"));
        assertEquals("TIMEOUT", game.publicState().get("endReason"));
    }

    @Test
    @DisplayName("게임 중 나가면 남은 사람이 이긴다")
    void leavingLoses() {
        OmokSession game = newGame();
        game.onPlayerLeft("white", NOW);
        assertEquals("black", game.publicState().get("winnerId"));
    }
}
