package com.stoneiron.gamehub.room;

public enum RoomStatus {
    /** 참가자를 모으는 중 */
    WAITING,
    /** 게임 진행 중. 새로 들어올 수 없다. */
    PLAYING,
    /** 한 판이 끝나고 결과를 보여주는 중. 방장이 다시 대기 상태로 돌릴 수 있다. */
    FINISHED
}
