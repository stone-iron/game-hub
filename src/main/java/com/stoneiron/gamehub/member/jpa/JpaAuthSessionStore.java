package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.AuthSession;
import com.stoneiron.gamehub.member.AuthSessionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * AuthSessionStore의 JPA 구현.
 */
@Component
public class JpaAuthSessionStore implements AuthSessionStore {

    private final AuthSessionJpaRepository repository;

    public JpaAuthSessionStore(AuthSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(AuthSession session) {
        repository.save(AuthSessionEntity.from(session));
    }

    @Override
    public Optional<AuthSession> findByTokenHash(String tokenHash) {
        return repository.findById(tokenHash).map(AuthSessionEntity::toSession);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        repository.deleteById(tokenHash);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return repository.deleteExpired(now);
    }
}
