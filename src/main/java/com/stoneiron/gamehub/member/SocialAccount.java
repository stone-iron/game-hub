package com.stoneiron.gamehub.member;

/** 소셜 제공자의 계정과 겜허브 회원을 연결한다. */
public record SocialAccount(String provider, String subject, Long memberId) {
}
