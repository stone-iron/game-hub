package com.stoneiron.gamehub.member;

/**
 * 화면에 돌려주는 회원 정보. 비밀번호 해시는 절대 담지 않는다.
 */
public record MemberProfile(Long id, String username, String nickname) {
}
