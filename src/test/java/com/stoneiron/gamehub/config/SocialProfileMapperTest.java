package com.stoneiron.gamehub.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialProfileMapperTest {

    @Test
    void mapsGoogleVerifiedEmail() {
        SocialProfile profile = SocialProfile.from("google", Map.of(
                "sub", "g-1", "email", "google@example.com", "email_verified", true, "name", "구글회원"));

        assertEquals("g-1", profile.subject());
        assertEquals("google@example.com", profile.email());
        assertEquals("구글회원", profile.nickname());
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        assertThrows(OAuth2AuthenticationException.class, () -> SocialProfile.from("google", Map.of(
                "sub", "g-1", "email", "google@example.com", "email_verified", false)));
    }

    @Test
    void mapsKakaoNestedProfile() {
        SocialProfile profile = SocialProfile.from("kakao", Map.of(
                "id", 1234L,
                "kakao_account", Map.of(
                        "email", "kakao@example.com",
                        "is_email_valid", true,
                        "is_email_verified", true,
                        "profile", Map.of("nickname", "카카오회원"))));

        assertEquals("1234", profile.subject());
        assertEquals("kakao@example.com", profile.email());
        assertEquals("카카오회원", profile.nickname());
    }
}
