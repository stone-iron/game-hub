package com.stoneiron.gamehub.room;

/**
 * 방에 들어온 본인에게만 돌려주는 정보. token은 다시 접속할 때와 행동을 보낼 때 본인 확인에 쓴다.
 */
public record JoinResult(String code, String playerId, String token, String gameType) {
}
