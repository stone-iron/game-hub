package com.stoneiron.gamehub.member;

import com.stoneiron.gamehub.member.AuthException.Reason;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 테스트용 메모리 회원 저장소. DB의 유니크 제약처럼 이메일·닉네임 중복 저장을 막는다. */
class InMemoryMemberStore implements MemberStore {

    private final Map<Long, Member> byId = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<Member> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return byId.values().stream().filter(m -> m.email().equals(email)).findFirst();
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return byId.values().stream().anyMatch(m -> m.nickname().equals(nickname));
    }

    @Override
    public synchronized Member save(Member member) {
        if (existsByEmail(member.email()) || existsByNickname(member.nickname())) {
            throw new AuthException(Reason.DUPLICATED, "이미 가입된 이메일 또는 사용 중인 닉네임입니다.");
        }
        Member saved = new Member(sequence.incrementAndGet(), member.email(), member.nickname(),
                member.passwordHash(), member.createdAt());
        byId.put(saved.id(), saved);
        return saved;
    }

    int size() {
        return byId.size();
    }
}
