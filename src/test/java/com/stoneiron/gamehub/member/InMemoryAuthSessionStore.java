package com.stoneiron.gamehub.member;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트용 메모리 로그인 상태 저장소. */
class InMemoryAuthSessionStore implements AuthSessionStore {

    final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AuthSession session) {
        sessions.put(session.tokenHash(), session);
    }

    @Override
    public Optional<AuthSession> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        sessions.remove(tokenHash);
    }

    @Override
    public int deleteExpired(Instant now) {
        int before = sessions.size();
        sessions.values().removeIf(s -> !now.isBefore(s.expiresAt()));
        return before - sessions.size();
    }
}
