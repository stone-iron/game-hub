package com.stoneiron.gamehub.game;

/**
 * 플랫폼에서 제공하는 게임 종류.
 * 인원 제한과 방 운영 방식은 방 생성·입장·시작 시점에 서버가 검사한다.
 */
public enum GameType {
    /** 2인 대국. 혼자하기를 고르면 컴퓨터가 상대한다. */
    OMOK("오목", 2, 2, true, false, false),
    /** 2명이 모이면 자동으로 시작하고, 게임 중에도 들어올 수 있다. */
    DRAWING("그림맞추기", 2, 10, false, true, true),
    /** 혼자 풀거나 최대 4명이 함께 경쟁한다. */
    CROSSWORD("십자격돌", 1, 4, true, false, false);

    private final String displayName;
    private final int minPlayers;
    private final int maxPlayers;
    private final boolean soloPlayable;
    private final boolean joinWhilePlaying;
    private final boolean autoStart;

    GameType(String displayName, int minPlayers, int maxPlayers,
             boolean soloPlayable, boolean joinWhilePlaying, boolean autoStart) {
        this.displayName = displayName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.soloPlayable = soloPlayable;
        this.joinWhilePlaying = joinWhilePlaying;
        this.autoStart = autoStart;
    }

    public String displayName() {
        return displayName;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    /** 혼자하기(컴퓨터 상대 또는 혼자 풀기)를 지원하는지 */
    public boolean soloPlayable() {
        return soloPlayable;
    }

    /** 게임이 진행 중일 때도 새로 들어올 수 있는지 */
    public boolean joinWhilePlaying() {
        return joinWhilePlaying;
    }

    /** 최소 인원이 모이면 방장이 누르지 않아도 자동으로 시작하는지 */
    public boolean autoStart() {
        return autoStart;
    }
}
