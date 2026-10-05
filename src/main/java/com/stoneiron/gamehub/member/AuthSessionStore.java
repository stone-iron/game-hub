package com.stoneiron.gamehub.member;

import java.time.Instant;
import java.util.Optional;

/**
 * 로그인 상태 저장소. {@link MemberStore}와 마찬가지로 테스트에서는 메모리 구현으로 바꿔 끼운다.
 */
public interface AuthSessionStore {

    void save(AuthSession session);

    Optional<AuthSession> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    /** 만료된 로그인 상태를 정리하고 지운 개수를 돌려준다. */
    int deleteExpired(Instant now);
}
