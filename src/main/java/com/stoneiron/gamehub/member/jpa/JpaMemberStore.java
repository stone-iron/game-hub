package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.AuthException;
import com.stoneiron.gamehub.member.Member;
import com.stoneiron.gamehub.member.MemberStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * MemberStore의 JPA 구현.
 */
@Component
public class JpaMemberStore implements MemberStore {

    private final MemberJpaRepository repository;

    public JpaMemberStore(MemberJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Member> findById(Long id) {
        return repository.findById(id).map(MemberEntity::toMember);
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return repository.findByUsername(username).map(MemberEntity::toMember);
    }

    @Override
    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return repository.existsByNickname(nickname);
    }

    @Override
    public Member save(Member member) {
        try {
            return repository.saveAndFlush(MemberEntity.from(member)).toMember();
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 다른 사람이 먼저 가입한 경우. DB 유니크 제약이 마지막으로 막아 준다.
            throw new AuthException(AuthException.Reason.DUPLICATED, "이미 사용 중인 아이디 또는 닉네임입니다.");
        }
    }
}
