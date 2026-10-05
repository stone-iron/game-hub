package com.stoneiron.gamehub.game;

import com.stoneiron.gamehub.game.drawing.DrawingSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingSessionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final PlayerInfo A = new PlayerInfo("a", "에이");
    private static final PlayerInfo B = new PlayerInfo("b", "비");
    private static final PlayerInfo C = new PlayerInfo("c", "씨");

    private DrawingSession newGame() {
        return new DrawingSession(List.of(A, B, C), List.of("사과", "바나나", "수박"), NOW);
    }

    private static Map<String, Object> stroke() {
        return Map.of("points", List.of(List.of(0.1, 0.1), List.of(0.5, 0.5)), "color", "#ff0000", "width", 5);
    }

    @Test
    @DisplayName("제시어는 출제자에게만 보내고 공개 상태에는 담지 않는다")
    void wordIsPrivateToDrawer() {
        DrawingSession game = newGame();
        assertEquals("사과", game.privateState("a").get("word"));
        assertTrue(game.privateState("b").isEmpty());
        assertFalse(game.publicState().toString().contains("사과"));
        assertEquals(2, game.publicState().get("wordLength"));
    }

    @Test
    @DisplayName("그림 획은 출제자만 그릴 수 있고 이벤트로만 전달된다")
    void onlyDrawerCanDraw() {
        DrawingSession game = newGame();
        ActionResult result = game.handle("a", "stroke", stroke(), NOW);
        assertFalse(result.stateChanged());
        assertEquals("stroke", result.events().get(0).get("type"));
        assertThrows(GameException.class, () -> game.handle("b", "stroke", stroke(), NOW));
    }

    @Test
    @DisplayName("출제자나 이미 맞힌 사람은 채팅으로 정답을 흘릴 수 없다")
    void cannotLeakAnswer() {
        DrawingSession game = newGame();
        assertThrows(GameException.class, () -> game.handle("a", "guess", Map.of("text", "정답은 사과"), NOW));
        game.handle("b", "guess", Map.of("text", "사과"), NOW);
        assertThrows(GameException.class, () -> game.handle("b", "guess", Map.of("text", "사과 맞죠"), NOW));
    }

    @Test
    @DisplayName("빨리 맞힐수록 점수가 크고, 모두 맞히면 정답을 공개한다")
    void scoringAndReveal() {
        DrawingSession game = newGame();
        game.handle("b", "guess", Map.of("text", "사 과"), NOW.plusSeconds(10));
        game.handle("c", "guess", Map.of("text", "사과"), NOW.plusSeconds(40));

        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) game.publicState().get("scores");
        assertTrue(scores.get("b") > scores.get("c"));
        assertEquals(40, (int) scores.get("a"));
        assertEquals("REVEAL", game.publicState().get("phase"));
        assertEquals("사과", game.publicState().get("revealedWord"));
    }

    @Test
    @DisplayName("제한 시간이 지나면 다음 사람이 출제자가 되고, 모두 한 번씩 그리면 끝난다")
    void turnsRotateUntilFinished() {
        DrawingSession game = newGame();
        Instant t = NOW;
        for (String expectedDrawer : List.of("a", "b", "c")) {
            assertEquals(expectedDrawer, game.publicState().get("drawerId"));
            t = t.plus(DrawingSession.TURN_TIME);
            game.tick(t);
            t = t.plus(DrawingSession.REVEAL_TIME);
            game.tick(t);
        }
        assertTrue(game.isFinished());
    }

    @Test
    @DisplayName("출제자가 나가면 바로 정답을 공개하고 다음 차례로 넘어간다")
    void drawerLeavingEndsTurn() {
        DrawingSession game = newGame();
        game.onPlayerLeft("a", NOW);
        assertEquals("REVEAL", game.publicState().get("phase"));
        game.tick(NOW.plus(DrawingSession.REVEAL_TIME));
        assertEquals("b", game.publicState().get("drawerId"));
    }

    private static Map<String, Object> penChunk(int group) {
        return Map.of("tool", "pen", "points", List.of(List.of(0.1, 0.1), List.of(0.2, 0.2)), "color", "#000000",
                "width", 4, "group", group);
    }

    @Test
    @DisplayName("되돌리기는 같은 group으로 나눠 보낸 펜 획을 한 번에 지우고, 다시하기로 되살린다")
    void undoAndRedoByGroup() {
        DrawingSession game = newGame();
        game.handle("a", "stroke", penChunk(1), NOW);
        game.handle("a", "stroke", penChunk(2), NOW);
        game.handle("a", "stroke", penChunk(2), NOW);
        assertEquals(3, ((List<?>) game.publicState().get("strokes")).size());

        ActionResult undo = game.handle("a", "undo", Map.of(), NOW);
        assertEquals("redraw", undo.events().get(0).get("type"));
        assertEquals(1, ((List<?>) game.publicState().get("strokes")).size());

        game.handle("a", "redo", Map.of(), NOW);
        assertEquals(3, ((List<?>) game.publicState().get("strokes")).size());
        assertThrows(GameException.class, () -> game.handle("b", "undo", Map.of(), NOW));
    }

    @Test
    @DisplayName("도형과 채우기는 필요한 좌표만 남기고, 모르는 도구는 거절한다")
    void sanitizesTools() {
        DrawingSession game = newGame();
        ActionResult rect = game.handle("a", "stroke", Map.of("tool", "rect",
                "points", List.of(List.of(0.1, 0.1), List.of(0.5, 0.5), List.of(0.9, 0.9)), "color", "#ff0000", "group", 1), NOW);
        @SuppressWarnings("unchecked")
        Map<String, Object> stroke = (Map<String, Object>) rect.events().get(0).get("stroke");
        assertEquals(2, ((List<?>) stroke.get("points")).size());

        ActionResult eraser = game.handle("a", "stroke", Map.of("tool", "eraser",
                "points", List.of(List.of(0.1, 0.1)), "color", "#ff0000", "group", 2), NOW);
        @SuppressWarnings("unchecked")
        Map<String, Object> erased = (Map<String, Object>) eraser.events().get(0).get("stroke");
        assertEquals("#ffffff", erased.get("color"));

        assertThrows(GameException.class, () -> game.handle("a", "stroke", Map.of("tool", "laser",
                "points", List.of(List.of(0.1, 0.1))), NOW));
    }

    @Test
    @DisplayName("게임 중에 들어온 사람은 점수 0으로 참가하고 마지막에 그린다")
    void joinWhilePlaying() {
        DrawingSession game = new DrawingSession(List.of(A, B), List.of("사과", "바나나", "수박", "포도"), NOW);
        game.onPlayerJoined(C, NOW);

        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) game.publicState().get("scores");
        assertEquals(0, (int) scores.get("c"));
        assertEquals(3, game.publicState().get("totalRounds"));

        Instant t = NOW;
        for (int i = 0; i < 2; i++) {
            t = t.plus(DrawingSession.TURN_TIME);
            game.tick(t);
            t = t.plus(DrawingSession.REVEAL_TIME);
            game.tick(t);
        }
        assertEquals("c", game.publicState().get("drawerId"));
    }
}
