package com.stoneiron.gamehub.game.omok;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 오목 컴퓨터의 수 계산.
 *
 * <p>둘 수 있는 빈칸마다 "내가 여기에 두면 얼마나 좋은가(공격)"와
 * "상대가 여기에 두면 얼마나 위험한가(수비)"를 점수로 매기고, 합이 가장 큰 칸에 둔다.
 * 실력은 판을 읽는 방식으로 나눈다.</p>
 *
 * <ul>
 *     <li>초급: 가로·세로만 보고, 같은 점수 근처에서는 무작위로 고른다.</li>
 *     <li>중급: 네 방향 모두, 연속으로 이어진 돌과 열린 끝만 센다.</li>
 *     <li>고급: 다섯 칸 묶음(윈도우)마다 돌을 세서 ●●_●● 같은 띄운 모양도 읽는다.</li>
 * </ul>
 */
public class OmokAi {

    static final long WIN = 1_000_000L;

    private static final int[][] ALL_DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int[][] STRAIGHT_DIRECTIONS = {{1, 0}, {0, 1}};
    private static final long[] WINDOW_SCORE = {0, 2, 50, 1_000, 20_000, WIN};

    private final OmokLevel level;
    private final Random random;

    public OmokAi(OmokLevel level, Random random) {
        this.level = level;
        this.random = random;
    }

    /**
     * @param board board[y][x] = 0(빈칸), 1(흑), 2(백)
     * @param me    컴퓨터의 돌 (1 또는 2)
     * @return {x, y}
     */
    public int[] choose(int[][] board, int me) {
        int size = board.length;
        int opponent = me == 1 ? 2 : 1;
        List<int[]> candidates = candidates(board);
        if (candidates.isEmpty()) {
            return new int[]{size / 2, size / 2};
        }

        long bestScore = Long.MIN_VALUE;
        List<int[]> best = new ArrayList<>();
        for (int[] cell : candidates) {
            int x = cell[0];
            int y = cell[1];
            long attack = evaluate(board, x, y, me);
            if (attack >= WIN) {
                return cell; // 이길 수 있으면 바로 둔다.
            }
            long defense = evaluate(board, x, y, opponent);
            long score = attack + defense * 9 / 10 + centerBonus(size, x, y);
            if (level == OmokLevel.BEGINNER) {
                score += random.nextInt(40);
            }
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(cell);
            } else if (score == bestScore) {
                best.add(cell);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    /** 이미 놓인 돌에서 두 칸 이내의 빈칸만 후보로 본다. */
    private List<int[]> candidates(int[][] board) {
        int size = board.length;
        List<int[]> result = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                if (hasNeighbor(board, x, y, 2)) {
                    result.add(new int[]{x, y});
                }
            }
        }
        return result;
    }

    private static boolean hasNeighbor(int[][] board, int x, int y, int distance) {
        int size = board.length;
        for (int dy = -distance; dy <= distance; dy++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < size && ny < size && board[ny][nx] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** (x, y)에 stone을 둔다고 가정했을 때의 점수 */
    long evaluate(int[][] board, int x, int y, int stone) {
        return level == OmokLevel.ADVANCED
                ? windowScore(board, x, y, stone)
                : runScore(board, x, y, stone, level == OmokLevel.BEGINNER ? STRAIGHT_DIRECTIONS : ALL_DIRECTIONS);
    }

    /** 이어진 돌의 수와 양 끝이 열려 있는지로 점수를 매긴다. 띄운 모양은 읽지 못한다. */
    private static long runScore(int[][] board, int x, int y, int stone, int[][] directions) {
        long total = 0;
        for (int[] d : directions) {
            int[] forward = run(board, x, y, d[0], d[1], stone);
            int[] backward = run(board, x, y, -d[0], -d[1], stone);
            int count = 1 + forward[0] + backward[0];
            int openEnds = forward[1] + backward[1];
            total += runValue(count, openEnds);
        }
        return total;
    }

    /** @return {같은 돌 개수, 그 뒤 칸이 비어 있으면 1} */
    private static int[] run(int[][] board, int x, int y, int dx, int dy, int stone) {
        int size = board.length;
        int count = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (cx >= 0 && cy >= 0 && cx < size && cy < size && board[cy][cx] == stone) {
            count++;
            cx += dx;
            cy += dy;
        }
        boolean open = cx >= 0 && cy >= 0 && cx < size && cy < size && board[cy][cx] == 0;
        return new int[]{count, open ? 1 : 0};
    }

    private static long runValue(int count, int openEnds) {
        if (count >= 5) {
            return WIN;
        }
        if (openEnds == 0) {
            return 0;
        }
        return switch (count) {
            case 4 -> openEnds == 2 ? 100_000 : 10_000;
            case 3 -> openEnds == 2 ? 5_000 : 300;
            case 2 -> openEnds == 2 ? 200 : 20;
            default -> openEnds == 2 ? 10 : 1;
        };
    }

    /**
     * 이 칸을 포함하는 다섯 칸 묶음마다, 상대 돌이 없으면 내 돌 개수로 점수를 더한다.
     * ●●_●● 처럼 띄운 모양도 "다섯 칸 중 네 개"로 읽히므로 놓치지 않는다.
     */
    private static long windowScore(int[][] board, int x, int y, int stone) {
        int size = board.length;
        long total = 0;
        for (int[] d : ALL_DIRECTIONS) {
            for (int offset = -4; offset <= 0; offset++) {
                int count = 0;
                boolean blocked = false;
                for (int i = 0; i < 5 && !blocked; i++) {
                    int cx = x + (offset + i) * d[0];
                    int cy = y + (offset + i) * d[1];
                    if (cx < 0 || cy < 0 || cx >= size || cy >= size) {
                        blocked = true;
                    } else if (cx == x && cy == y || board[cy][cx] == stone) {
                        count++;
                    } else if (board[cy][cx] != 0) {
                        blocked = true;
                    }
                }
                if (!blocked) {
                    total += WINDOW_SCORE[count];
                }
            }
        }
        return total;
    }

    /** 점수가 같으면 가운데에 가까운 칸을 조금 더 좋아한다. */
    private static long centerBonus(int size, int x, int y) {
        int center = size / 2;
        return size - (Math.abs(x - center) + Math.abs(y - center)) / 2;
    }
}
