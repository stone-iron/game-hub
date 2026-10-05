package com.stoneiron.gamehub.config;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

record SocialProfile(String subject, String email, String nickname) {

    @SuppressWarnings("unchecked")
    static SocialProfile from(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> {
                if (!Boolean.TRUE.equals(attributes.get("email_verified"))) {
                    throw new OAuth2AuthenticationException("확인된 Google 이메일이 필요합니다.");
                }
                yield new SocialProfile(text(attributes.get("sub")), text(attributes.get("email")),
                        text(attributes.get("name")));
            }
            case "kakao" -> {
                Map<String, Object> account = map(attributes.get("kakao_account"));
                if (!Boolean.TRUE.equals(account.get("is_email_valid"))
                        || !Boolean.TRUE.equals(account.get("is_email_verified"))) {
                    throw new OAuth2AuthenticationException("확인된 카카오 이메일 제공에 동의해 주세요.");
                }
                Map<String, Object> profile = map(account.get("profile"));
                yield new SocialProfile(text(attributes.get("id")), text(account.get("email")),
                        text(profile.get("nickname")));
            }
            case "naver" -> new SocialProfile(text(attributes.get("id")), text(attributes.get("email")),
                    text(attributes.get("nickname")));
            default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다.");
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
