package com.stoneiron.gamehub.game.omok;

/**
 * 컴퓨터 상대의 실력.
 */
public enum OmokLevel {
    /** 가로·세로 줄만 본다. 대각선 위협은 잘 놓친다. */
    BEGINNER("초급"),
    /** 네 방향을 모두 보지만, 이어진 돌만 센다. 한 칸 띄운 모양은 읽지 못한다. */
    INTERMEDIATE("중급"),
    /** 다섯 칸 단위로 판을 훑어 띄운 모양까지 읽는다. */
    ADVANCED("고급");

    private final String displayName;

    OmokLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
