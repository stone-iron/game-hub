package com.stoneiron.gamehub.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialAuthServiceTest {

    private InMemoryMemberStore members;
    private InMemorySocialAccountStore socialAccounts;
    private AuthService auth;
    private SocialAuthService socialAuth;

    @BeforeEach
    void setUp() {
        members = new InMemoryMemberStore();
        socialAccounts = new InMemorySocialAccountStore();
        AuthServiceTest.MutableClock clock = new AuthServiceTest.MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
        auth = new AuthService(members, new InMemoryAuthSessionStore(), new PasswordHasher(1_000),
                new LoginAttemptGuard(), clock);
        socialAuth = new SocialAuthService(members, socialAccounts, auth, clock);
    }

    @Test
    @DisplayName("처음 소셜 로그인하면 회원과 소셜 계정 연결을 만들고 로그인한다")
    void createsMemberAndLink() {
        AuthResult result = socialAuth.login("google", "google-1", "USER@example.com", "게임왕");

        assertEquals("user@example.com", result.member().email());
        assertEquals("게임왕", result.member().nickname());
        assertNotNull(result.token());
        assertEquals(result.member().id(), socialAccounts.find("google", "google-1").orElseThrow().memberId());
    }

    @Test
    @DisplayName("같은 소셜 계정은 제공자가 돌려준 이메일이 바뀌어도 기존 회원으로 로그인한다")
    void reusesLinkedMember() {
        AuthResult first = socialAuth.login("google", "google-1", "first@example.com", "첫이름");
        AuthResult second = socialAuth.login("google", "google-1", "changed@example.com", "바뀐이름");

        assertEquals(first.member().id(), second.member().id());
        assertEquals("first@example.com", second.member().email());
        assertEquals(1, members.size());
    }

    @Test
    @DisplayName("같은 이메일의 일반 회원이 있으면 새 회원 대신 소셜 계정을 연결한다")
    void linksExistingEmailMember() {
        AuthResult local = auth.signup("same@example.com", "기존회원", "abcdefgh");
        AuthResult social = socialAuth.login("naver", "naver-1", "same@example.com", "다른닉네임");

        assertEquals(local.member().id(), social.member().id());
        assertEquals("기존회원", social.member().nickname());
        assertEquals(1, members.size());
    }

    @Test
    @DisplayName("소셜 전용 회원은 비밀번호 로그인을 할 수 없다")
    void socialOnlyMemberCannotPasswordLogin() {
        socialAuth.login("kakao", "kakao-1", "social@example.com", "소셜회원");

        assertThrows(AuthException.class, () -> auth.login("social@example.com", "abcdefgh"));
    }
}
