package com.stoneiron.gamehub.member;

import java.time.Instant;

/**
 * 회원. 비밀번호는 원문이 아니라 {@link PasswordHasher}로 만든 해시만 가진다.
 *
 * @param id 저장 전에는 null
 */
public record Member(Long id, String username, String nickname, String passwordHash, Instant createdAt) {

    public MemberProfile profile() {
        return new MemberProfile(id, username, nickname);
    }
}
