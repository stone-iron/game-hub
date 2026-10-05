package com.stoneiron.gamehub.member;

import java.util.Optional;

/**
 * 회원 저장소.
 *
 * <p>AuthService는 이 인터페이스에만 의존한다. 실제 서비스에서는 JPA 구현을 쓰고,
 * 단위 테스트에서는 메모리 구현으로 바꿔 끼워 DB 없이 가입·로그인 규칙을 검증한다.</p>
 */
public interface MemberStore {

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /**
     * 새 회원을 저장한다. 동시에 같은 이메일·닉네임으로 가입하면 한 명만 성공하고
     * 나머지는 {@link AuthException}(DUPLICATED)을 받는다.
     */
    Member save(Member member);
}
