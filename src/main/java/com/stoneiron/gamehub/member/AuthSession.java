package com.stoneiron.gamehub.member;

import java.time.Instant;

/**
 * 로그인 상태. 토큰 원문이 아니라 SHA-256 해시를 저장하므로,
 * DB가 유출되더라도 저장된 값만으로는 다른 사람으로 로그인할 수 없다.
 */
public record AuthSession(String tokenHash, Long memberId, Instant expiresAt) {
}
