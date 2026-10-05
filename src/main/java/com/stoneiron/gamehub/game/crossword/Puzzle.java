package com.stoneiron.gamehub.game.crossword;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 십자말 퍼즐 한 세트. 생성할 때 교차하는 글자가 서로 맞는지 검증하고,
 * 칸마다 들어갈 글자와 단어 번호를 계산해 둔다.
 */
public final class Puzzle {

    public enum Direction { ACROSS, DOWN }

    public record Word(String id, Direction direction, int row, int col, String answer, String clue) {

        public int length() {
            return answer.length();
        }

        public int rowAt(int index) {
            return direction == Direction.ACROSS ? row : row + index;
        }

        public int colAt(int index) {
            return direction == Direction.ACROSS ? col + index : col;
        }
    }

    private final String id;
    private final String title;
    private final int size;
    private final List<Word> words;
    private final String[][] letters;
    private final Map<String, Integer> numbers = new HashMap<>();

    public Puzzle(String id, String title, int size, List<Word> words) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.words = List.copyOf(words);
        this.letters = new String[size][size];
        fillAndValidate();
        assignNumbers();
    }

    private void fillAndValidate() {
        for (Word word : words) {
            for (int i = 0; i < word.length(); i++) {
                int r = word.rowAt(i);
                int c = word.colAt(i);
                if (r < 0 || c < 0 || r >= size || c >= size) {
                    throw new IllegalStateException(id + ": 판 밖으로 나간 단어 " + word.answer());
                }
                String letter = String.valueOf(word.answer().charAt(i));
                if (letters[r][c] != null && !letters[r][c].equals(letter)) {
                    throw new IllegalStateException(id + ": 교차 글자가 맞지 않습니다 (" + r + "," + c + ") "
                            + letters[r][c] + " / " + letter);
                }
                letters[r][c] = letter;
            }
        }
        validateNoAccidentalWords();
    }

    /**
     * 가로·세로로 이어진 글자 묶음은 반드시 정의된 단어여야 한다.
     * 단어끼리 붙어서 의도하지 않은 글자 줄이 생기는 실수를 막는다.
     */
    private void validateNoAccidentalWords() {
        for (Direction direction : Direction.values()) {
            for (int line = 0; line < size; line++) {
                int start = -1;
                for (int pos = 0; pos <= size; pos++) {
                    boolean filled = pos < size && cell(direction, line, pos) != null;
                    if (filled && start < 0) {
                        start = pos;
                    } else if (!filled && start >= 0) {
                        int length = pos - start;
                        if (length >= 2 && findWord(direction, line, start, length) == null) {
                            throw new IllegalStateException(id + ": 정의되지 않은 글자 줄이 있습니다 ("
                                    + direction + " " + line + ")");
                        }
                        start = -1;
                    }
                }
            }
        }
    }

    private String cell(Direction direction, int line, int pos) {
        return direction == Direction.ACROSS ? letters[line][pos] : letters[pos][line];
    }

    private Word findWord(Direction direction, int line, int start, int length) {
        for (Word w : words) {
            boolean sameLine = direction == Direction.ACROSS ? w.row() == line && w.col() == start
                    : w.col() == line && w.row() == start;
            if (w.direction() == direction && sameLine && w.length() == length) {
                return w;
            }
        }
        return null;
    }

    /** 시작 칸 위치(위→아래, 왼쪽→오른쪽) 순서로 1번부터 번호를 붙인다. */
    private void assignNumbers() {
        List<Word> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingInt(Word::row).thenComparingInt(Word::col));
        Map<String, Integer> byCell = new HashMap<>();
        int next = 1;
        for (Word w : sorted) {
            String key = w.row() + ":" + w.col();
            Integer number = byCell.get(key);
            if (number == null) {
                number = next++;
                byCell.put(key, number);
            }
            numbers.put(w.id(), number);
        }
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public List<Word> words() {
        return words;
    }

    public String letterAt(int row, int col) {
        return letters[row][col];
    }

    public boolean isBlock(int row, int col) {
        return letters[row][col] == null;
    }

    public int numberOf(String wordId) {
        return numbers.get(wordId);
    }

    public Word word(String wordId) {
        return words.stream().filter(w -> w.id().equals(wordId)).findFirst().orElse(null);
    }
}
