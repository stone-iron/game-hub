package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.AuthSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 로그인 상태 테이블. 토큰 원문이 아니라 SHA-256 해시(64자)를 기본키로 저장한다.
 */
@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_sessions_expires_at", columnList = "expires_at"))
public class AuthSessionEntity {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthSessionEntity() {
    }

    static AuthSessionEntity from(AuthSession session) {
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.tokenHash = session.tokenHash();
        entity.memberId = session.memberId();
        entity.expiresAt = session.expiresAt();
        return entity;
    }

    AuthSession toSession() {
        return new AuthSession(tokenHash, memberId, expiresAt);
    }
}
