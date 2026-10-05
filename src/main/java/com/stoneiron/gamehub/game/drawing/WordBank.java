package com.stoneiron.gamehub.game.drawing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 그림맞추기 제시어 목록. 한 판 안에서는 같은 단어가 두 번 나오지 않도록 섞어서 꺼낸다.
 */
public class WordBank {

    private static final List<String> DEFAULT_WORDS = List.of(
            "사과", "바나나", "수박", "포도", "딸기", "당근", "고양이", "강아지", "토끼", "거북이",
            "기린", "코끼리", "펭귄", "자전거", "비행기", "기차", "우산", "안경", "시계", "모자",
            "눈사람", "무지개", "해바라기", "피아노", "기타", "축구공", "컴퓨터", "냉장고", "의자", "침대",
            "라면", "햄버거", "피자", "케이크", "아이스크림", "로켓", "등대", "선풍기", "칫솔", "가방"
    );

    private final List<String> words;

    public WordBank() {
        this(DEFAULT_WORDS);
    }

    public WordBank(List<String> words) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("제시어가 비어 있습니다.");
        }
        this.words = List.copyOf(words);
    }

    /** 섞인 제시어를 count개 돌려준다. 단어가 모자라면 다시 섞어서 이어 붙인다. */
    public List<String> draw(int count, Random random) {
        List<String> result = new ArrayList<>(count);
        List<String> pool = new ArrayList<>();
        while (result.size() < count) {
            if (pool.isEmpty()) {
                pool.addAll(words);
                Collections.shuffle(pool, random);
            }
            result.add(pool.remove(pool.size() - 1));
        }
        return result;
    }
}
