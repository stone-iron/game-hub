package com.stoneiron.gamehub.member;

import com.stoneiron.gamehub.member.AuthException.Reason;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 테스트용 메모리 회원 저장소. DB의 유니크 제약처럼 아이디·닉네임 중복 저장을 막는다. */
class InMemoryMemberStore implements MemberStore {

    private final Map<Long, Member> byId = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Optional<Member> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return byId.values().stream().filter(m -> m.username().equals(username)).findFirst();
    }

    @Override
    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return byId.values().stream().anyMatch(m -> m.nickname().equals(nickname));
    }

    @Override
    public synchronized Member save(Member member) {
        if (existsByUsername(member.username()) || existsByNickname(member.nickname())) {
            throw new AuthException(Reason.DUPLICATED, "이미 사용 중인 아이디 또는 닉네임입니다.");
        }
        Member saved = new Member(sequence.incrementAndGet(), member.username(), member.nickname(),
                member.passwordHash(), member.createdAt());
        byId.put(saved.id(), saved);
        return saved;
    }

    int size() {
        return byId.size();
    }
}
