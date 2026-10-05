package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 회원 테이블. 아이디와 닉네임에 유니크 제약을 걸어,
 * 동시에 같은 값으로 가입하는 요청이 와도 DB가 한 건만 받아들인다.
 * (MySQL에서 member는 예약어라 테이블 이름을 members로 했다.)
 */
@Entity
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_members_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_members_nickname", columnNames = "nickname")
})
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false, length = 12)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemberEntity() {
    }

    static MemberEntity from(Member member) {
        MemberEntity entity = new MemberEntity();
        entity.id = member.id();
        entity.username = member.username();
        entity.nickname = member.nickname();
        entity.passwordHash = member.passwordHash();
        entity.createdAt = member.createdAt();
        return entity;
    }

    Member toMember() {
        return new Member(id, username, nickname, passwordHash, createdAt);
    }
}
