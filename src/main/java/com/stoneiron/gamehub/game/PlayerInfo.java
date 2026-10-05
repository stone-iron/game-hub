package com.stoneiron.gamehub.game;

/**
 * 게임 세션이 알아야 하는 최소한의 플레이어 정보.
 * 인증 토큰처럼 게임과 무관한 값은 넘기지 않는다.
 */
public record PlayerInfo(String id, String nickname) {
}
