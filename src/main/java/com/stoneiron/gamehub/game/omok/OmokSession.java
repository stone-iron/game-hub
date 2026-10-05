package com.stoneiron.gamehub.game.omok;

import com.stoneiron.gamehub.game.ActionResult;
import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.game.GameSession;
import com.stoneiron.gamehub.game.Payloads;
import com.stoneiron.gamehub.game.PlayerInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 15x15 오목. 흑이 먼저 두고, 가로·세로·대각선으로 5개 이상 이으면 승리한다.
 *
 * <p>클라이언트는 좌표만 보낸다. 차례 확인, 빈칸 확인, 승패 판정, 제한 시간 처리는 모두 서버가 한다.</p>
 */
public class OmokSession implements GameSession {

    public static final int SIZE = 15;
    public static final Duration TURN_LIMIT = Duration.ofSeconds(60);

    private static final int EMPTY = 0;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private final List<PlayerInfo> players;
    private final int[][] board = new int[SIZE][SIZE];
    private final List<int[]> moves = new ArrayList<>();

    private int turnIndex = 0;
    private Instant turnStartedAt;
    private String winnerId;
    private String endReason;
    private boolean finished;

    public OmokSession(List<PlayerInfo> players, Instant now) {
        if (players.size() != 2) {
            throw new GameException("오목은 2명이 필요합니다.");
        }
        this.players = List.copyOf(players);
        this.turnStartedAt = now;
    }

    @Override
    public ActionResult handle(String playerId, String type, Map<String, Object> data, Instant now) {
        if (finished) {
            throw new GameException("이미 끝난 게임입니다.");
        }
        return switch (type) {
            case "place" -> place(playerId, Payloads.intValue(data, "x"), Payloads.intValue(data, "y"), now);
            case "resign" -> resign(playerId);
            default -> throw new GameException("알 수 없는 행동입니다: " + type);
        };
    }

    private ActionResult place(String playerId, int x, int y, Instant now) {
        int seat = seatOf(playerId);
        if (seat != turnIndex) {
            throw new GameException("상대의 차례입니다.");
        }
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            throw new GameException("판 밖에는 둘 수 없습니다.");
        }
        if (board[y][x] != EMPTY) {
            throw new GameException("이미 돌이 놓인 자리입니다.");
        }

        int stone = seat + 1;
        board[y][x] = stone;
        moves.add(new int[]{x, y, stone});

        if (isFiveInRow(x, y, stone)) {
            finish(playerId, "FIVE_IN_ROW");
        } else if (moves.size() == SIZE * SIZE) {
            finish(null, "DRAW");
        } else {
            turnIndex = 1 - turnIndex;
            turnStartedAt = now;
        }
        return ActionResult.changed();
    }

    private ActionResult resign(String playerId) {
        int seat = seatOf(playerId);
        finish(players.get(1 - seat).id(), "RESIGN");
        return ActionResult.changed();
    }

    /** 마지막에 둔 돌을 기준으로 네 방향을 검사한다. 판 전체를 훑지 않아도 된다. */
    private boolean isFiveInRow(int x, int y, int stone) {
        for (int[] d : DIRECTIONS) {
            int count = 1 + countDirection(x, y, d[0], d[1], stone) + countDirection(x, y, -d[0], -d[1], stone);
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }

    private int countDirection(int x, int y, int dx, int dy, int stone) {
        int count = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (cx >= 0 && cy >= 0 && cx < SIZE && cy < SIZE && board[cy][cx] == stone) {
            count++;
            cx += dx;
            cy += dy;
        }
        return count;
    }

    @Override
    public ActionResult tick(Instant now) {
        if (finished) {
            return ActionResult.none();
        }
        if (Duration.between(turnStartedAt, now).compareTo(TURN_LIMIT) >= 0) {
            finish(players.get(1 - turnIndex).id(), "TIMEOUT");
            return ActionResult.changed();
        }
        return ActionResult.none();
    }

    @Override
    public ActionResult onPlayerLeft(String playerId, Instant now) {
        if (finished) {
            return ActionResult.none();
        }
        finish(players.get(1 - seatOf(playerId)).id(), "LEFT");
        return ActionResult.changed();
    }

    /** 지금 둘 차례인 플레이어. 게임이 끝났으면 null */
    public String turnPlayerId() {
        return finished ? null : players.get(turnIndex).id();
    }

    public Instant turnStartedAt() {
        return turnStartedAt;
    }

    /** 1 = 흑, 2 = 백 */
    public int stoneOf(String playerId) {
        return seatOf(playerId) + 1;
    }

    /** 컴퓨터가 수를 계산할 수 있도록 판을 복사해서 돌려준다. 원본은 바꿀 수 없다. */
    public int[][] boardSnapshot() {
        int[][] copy = new int[SIZE][];
        for (int y = 0; y < SIZE; y++) {
            copy[y] = board[y].clone();
        }
        return copy;
    }

    private int seatOf(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                return i;
            }
        }
        throw new GameException("이 게임의 참가자가 아닙니다.");
    }

    private void finish(String winnerId, String reason) {
        this.finished = true;
        this.winnerId = winnerId;
        this.endReason = reason;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public Map<String, Object> publicState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("size", SIZE);
        List<List<Integer>> rows = new ArrayList<>();
        for (int[] row : board) {
            List<Integer> r = new ArrayList<>(SIZE);
            for (int cell : row) {
                r.add(cell);
            }
            rows.add(r);
        }
        state.put("board", rows);
        state.put("black", players.get(0).id());
        state.put("white", players.get(1).id());
        state.put("turnPlayerId", finished ? null : players.get(turnIndex).id());
        state.put("turnEndsAt", finished ? null : turnStartedAt.plus(TURN_LIMIT).toEpochMilli());
        int[] last = moves.isEmpty() ? null : moves.get(moves.size() - 1);
        state.put("lastMove", last == null ? null : Map.of("x", last[0], "y", last[1]));
        state.put("moveCount", moves.size());
        state.put("winnerId", winnerId);
        state.put("endReason", endReason);
        return state;
    }

    @Override
    public List<String> ranking() {
        if (!finished || winnerId == null) {
            return List.of();
        }
        String loser = players.get(0).id().equals(winnerId) ? players.get(1).id() : players.get(0).id();
        return List.of(winnerId, loser);
    }
}
