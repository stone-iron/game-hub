package com.stoneiron.gamehub.member;

/**
 * 로그인(또는 가입 직후 자동 로그인) 결과. token은 본인에게 한 번만 전달되고 서버에는 해시로만 저장된다.
 */
public record AuthResult(String token, MemberProfile member) {
}
