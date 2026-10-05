package com.stoneiron.gamehub.game.crossword;

import com.stoneiron.gamehub.game.crossword.Puzzle.Word;

import java.util.List;
import java.util.Random;

import static com.stoneiron.gamehub.game.crossword.Puzzle.Direction.ACROSS;
import static com.stoneiron.gamehub.game.crossword.Puzzle.Direction.DOWN;

/**
 * 직접 만든 7x7 퍼즐 모음. 각 퍼즐은 가로 8개, 세로 6개, 모두 14개의 단어로 이루어진다.
 *
 * <p>가로 단어는 0·2·4·6행에 3글자씩 두 개(0~2열, 4~6열)가 놓이고,
 * 세로 단어는 그 사이를 이어 주는 3글자 단어다. 3열과 1·3·5행의 나머지 칸은 비어 있다.</p>
 *
 * <p>새 퍼즐을 추가하면 {@link Puzzle} 생성자가 교차 글자를 자동으로 검증한다.</p>
 */
public class PuzzleBank {

    private final List<Puzzle> puzzles;

    public PuzzleBank() {
        this(List.of(everyday(), neighborhood()));
    }

    public PuzzleBank(List<Puzzle> puzzles) {
        if (puzzles.isEmpty()) {
            throw new IllegalArgumentException("퍼즐이 비어 있습니다.");
        }
        this.puzzles = List.copyOf(puzzles);
    }

    public Puzzle pick(Random random) {
        return puzzles.get(random.nextInt(puzzles.size()));
    }

    public List<Puzzle> all() {
        return puzzles;
    }

    public static Puzzle everyday() {
        return new Puzzle("P1", "생활 속 낱말", 7, List.of(
                new Word("a1", ACROSS, 0, 0, "고구마", "쪄 먹거나 구워 먹는 달콤한 덩이뿌리 작물"),
                new Word("a2", ACROSS, 0, 4, "자동차", "엔진의 힘으로 도로 위를 달리는 탈것"),
                new Word("a3", ACROSS, 2, 0, "이발사", "머리카락을 자르고 다듬는 일을 하는 사람"),
                new Word("a4", ACROSS, 2, 4, "강아지", "개의 새끼"),
                new Word("a5", ACROSS, 4, 0, "개나리", "이른 봄에 노란 꽃을 피우는 나무"),
                new Word("a6", ACROSS, 4, 4, "콩나물", "콩을 물에 불려 싹을 틔운 나물"),
                new Word("a7", ACROSS, 6, 0, "리모컨", "멀리서 텔레비전 같은 기기를 조작하는 장치"),
                new Word("a8", ACROSS, 6, 4, "비행기", "날개로 하늘을 나는 탈것"),
                new Word("d1", DOWN, 0, 0, "고양이", "'야옹' 하고 우는 반려동물"),
                new Word("d2", DOWN, 0, 6, "차고지", "버스나 차량을 모아 두는 곳"),
                new Word("d3", DOWN, 2, 2, "사다리", "높은 곳에 오르내릴 때 딛도록 만든 기구"),
                new Word("d4", DOWN, 2, 4, "강낭콩", "밥에 넣어 먹기도 하는 붉은빛의 콩"),
                new Word("d5", DOWN, 4, 0, "개구리", "올챙이가 자라서 되는 동물"),
                new Word("d6", DOWN, 4, 6, "물고기", "물속에서 아가미로 숨 쉬며 사는 동물")
        ));
    }

    public static Puzzle neighborhood() {
        return new Puzzle("P2", "동네 한 바퀴", 7, List.of(
                new Word("a1", ACROSS, 0, 0, "수요일", "화요일 다음 날"),
                new Word("a2", ACROSS, 0, 4, "무지개", "비가 갠 뒤 하늘에 나타나는 일곱 빛깔의 띠"),
                new Word("a3", ACROSS, 2, 0, "장난감", "아이들이 가지고 노는 물건"),
                new Word("a4", ACROSS, 2, 4, "도토리", "다람쥐가 좋아하는 참나무 열매"),
                new Word("a5", ACROSS, 4, 0, "소나무", "사계절 내내 푸른 잎을 지닌 대표적인 바늘잎나무"),
                new Word("a6", ACROSS, 4, 4, "관리자", "시설이나 시스템을 맡아 관리하는 사람"),
                new Word("a7", ACROSS, 6, 0, "차고지", "버스나 차량을 모아 두는 곳"),
                new Word("a8", ACROSS, 6, 4, "구급차", "아픈 사람을 병원으로 옮기는 차"),
                new Word("d1", DOWN, 0, 0, "수영장", "헤엄을 칠 수 있도록 만든 곳"),
                new Word("d2", DOWN, 0, 6, "개나리", "이른 봄에 노란 꽃을 피우는 나무"),
                new Word("d3", DOWN, 2, 2, "감나무", "가을에 주황색 열매가 열리는 나무"),
                new Word("d4", DOWN, 2, 4, "도서관", "책을 모아 두고 빌려주는 곳"),
                new Word("d5", DOWN, 4, 0, "소방차", "불을 끄러 출동하는 차"),
                new Word("d6", DOWN, 4, 6, "자동차", "엔진의 힘으로 도로 위를 달리는 탈것")
        ));
    }
}
