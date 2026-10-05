package com.stoneiron.gamehub.game;

import com.stoneiron.gamehub.game.omok.OmokAi;
import com.stoneiron.gamehub.game.omok.OmokLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OmokAiTest {

    private static final int SIZE = 15;
    private static final int BLACK = 1;
    private static final int WHITE = 2;

    private static int[][] emptyBoard() {
        return new int[SIZE][SIZE];
    }

    private static boolean at(int[] move, int x, int y) {
        return move[0] == x && move[1] == y;
    }

    @Test
    @DisplayName("모든 난이도는 다섯 개를 완성할 수 있으면 바로 이긴다")
    void takesWinningMove() {
        for (OmokLevel level : OmokLevel.values()) {
            int[][] board = emptyBoard();
            for (int x = 3; x <= 6; x++) {
                board[5][x] = WHITE;
            }
            board[6][6] = BLACK;
            board[7][7] = BLACK;
            int[] move = new OmokAi(level, new Random(1)).choose(board, WHITE);
            assertTrue(at(move, 2, 5) || at(move, 7, 5), level + " 이 이기는 수를 두지 않았습니다");
        }
    }

    @Test
    @DisplayName("모든 난이도는 세로로 이어진 네 개를 막는다")
    void blocksStraightFour() {
        for (OmokLevel level : OmokLevel.values()) {
            int[][] board = emptyBoard();
            for (int y = 3; y <= 6; y++) {
                board[y][8] = BLACK;
            }
            board[0][0] = WHITE;
            int[] move = new OmokAi(level, new Random(1)).choose(board, WHITE);
            assertTrue(at(move, 8, 2) || at(move, 8, 7), level + " 이 네 개를 막지 않았습니다");
        }
    }

    @Test
    @DisplayName("중급 이상은 대각선 네 개도 막는다")
    void blocksDiagonalFour() {
        for (OmokLevel level : new OmokLevel[]{OmokLevel.INTERMEDIATE, OmokLevel.ADVANCED}) {
            int[][] board = emptyBoard();
            for (int i = 3; i <= 6; i++) {
                board[i][i] = BLACK;
            }
            board[0][14] = WHITE;
            int[] move = new OmokAi(level, new Random(1)).choose(board, WHITE);
            assertTrue(at(move, 2, 2) || at(move, 7, 7), level + " 이 대각선을 막지 않았습니다");
        }
    }

    @Test
    @DisplayName("고급은 중간이 비어 있는 ●●_●● 모양을 막는다")
    void advancedBlocksBrokenFour() {
        int[][] board = emptyBoard();
        for (int x : new int[]{3, 4, 6, 7}) {
            board[9][x] = BLACK;
        }
        board[0][0] = WHITE;
        int[] move = new OmokAi(OmokLevel.ADVANCED, new Random(1)).choose(board, WHITE);
        assertTrue(at(move, 5, 9));
    }

    @Test
    @DisplayName("고급은 초급을 상대로 대부분 이긴다")
    void advancedBeatsBeginner() {
        int advancedWins = 0;
        for (int game = 0; game < 10; game++) {
            boolean advancedBlack = game % 2 == 0;
            OmokAi black = new OmokAi(advancedBlack ? OmokLevel.ADVANCED : OmokLevel.BEGINNER, new Random(game));
            OmokAi white = new OmokAi(advancedBlack ? OmokLevel.BEGINNER : OmokLevel.ADVANCED, new Random(game + 100));
            int winner = play(black, white);
            if (winner == (advancedBlack ? BLACK : WHITE)) {
                advancedWins++;
            }
        }
        assertTrue(advancedWins >= 8, "고급 승리 수: " + advancedWins);
    }

    private static int play(OmokAi black, OmokAi white) {
        int[][] board = emptyBoard();
        board[7][7] = BLACK;
        int turn = WHITE;
        for (int moves = 1; moves < SIZE * SIZE; moves++) {
            int[] move = (turn == BLACK ? black : white).choose(board, turn);
            board[move[1]][move[0]] = turn;
            if (fiveInRow(board, move[0], move[1], turn)) {
                return turn;
            }
            turn = turn == BLACK ? WHITE : BLACK;
        }
        return 0;
    }

    private static boolean fiveInRow(int[][] board, int x, int y, int stone) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            for (int sign : new int[]{1, -1}) {
                int cx = x + d[0] * sign;
                int cy = y + d[1] * sign;
                while (cx >= 0 && cy >= 0 && cx < SIZE && cy < SIZE && board[cy][cx] == stone) {
                    count++;
                    cx += d[0] * sign;
                    cy += d[1] * sign;
                }
            }
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }
}
