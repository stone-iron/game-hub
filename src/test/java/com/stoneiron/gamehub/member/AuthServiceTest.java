package com.stoneiron.gamehub.member;

import com.stoneiron.gamehub.member.AuthException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private InMemoryMemberStore members;
    private InMemoryAuthSessionStore sessions;
    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        members = new InMemoryMemberStore();
        sessions = new InMemoryAuthSessionStore();
        clock = new MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
        auth = new AuthService(members, sessions, new PasswordHasher(1_000), new LoginAttemptGuard(), clock);
    }

    @Test
    @DisplayName("가입하면 바로 로그인되고, 토큰으로 내 정보를 확인할 수 있다")
    void signupLogsIn() {
        AuthResult result = auth.signup("  Player1 ", "돌쇠", "abcd1234");

        assertEquals("player1", result.member().username());
        assertEquals("돌쇠", result.member().nickname());
        assertEquals("돌쇠", auth.requireMember(result.token()).nickname());
    }

    @Test
    @DisplayName("서버에는 토큰 원문이 아닌 해시만 저장된다")
    void storesOnlyTokenHash() {
        AuthResult result = auth.signup("player1", "돌쇠", "abcd1234");

        assertEquals(1, sessions.sessions.size());
        assertFalse(sessions.sessions.containsKey(result.token()));
        assertTrue(sessions.sessions.containsKey(AuthService.sha256(result.token())));
    }

    @Test
    @DisplayName("아이디·닉네임이 겹치면 가입할 수 없다")
    void rejectsDuplicates() {
        auth.signup("player1", "돌쇠", "abcd1234");

        AuthException sameId = assertThrows(AuthException.class, () -> auth.signup("PLAYER1", "다른이름", "abcd1234"));
        AuthException sameNick = assertThrows(AuthException.class, () -> auth.signup("player2", "돌쇠", "abcd1234"));

        assertEquals(Reason.DUPLICATED, sameId.reason());
        assertEquals(Reason.DUPLICATED, sameNick.reason());
        assertEquals(1, members.size());
    }

    @Test
    @DisplayName("아이디·닉네임·비밀번호 규칙을 지키지 않으면 가입할 수 없다")
    void validatesInput() {
        String[][] bad = {
                {"abc", "돌쇠", "abcd1234"},          // 아이디 너무 짧음
                {"player_1", "돌쇠", "abcd1234"},     // 허용하지 않는 문자
                {"player1", " ", "abcd1234"},         // 닉네임 비어 있음
                {"player1", "열세글자가넘는아주긴닉네임", "abcd1234"}, // 닉네임 너무 김
                {"player1", "돌쇠", "abc123"},        // 비밀번호 너무 짧음
                {"player1", "돌쇠", "a".repeat(73)},  // 비밀번호 너무 김
        };
        for (String[] input : bad) {
            AuthException e = assertThrows(AuthException.class, () -> auth.signup(input[0], input[1], input[2]));
            assertEquals(Reason.INVALID_INPUT, e.reason());
        }
        assertEquals(0, members.size());
    }

    @Test
    @DisplayName("8~72자이면 문자 조합이나 아이디 포함 여부와 관계없이 가입할 수 있다")
    void acceptsSamePasswordRuleAsGroupBuy() {
        assertEquals("문자회원", auth.signup("letters1", "문자회원", "abcdefgh").member().nickname());
        assertEquals("숫자회원", auth.signup("digits1", "숫자회원", "12345678").member().nickname());
        assertEquals("아이디회원", auth.signup("player1", "아이디회원", "player1abc").member().nickname());
        assertEquals("최대회원", auth.signup("maximum1", "최대회원", "가".repeat(72)).member().nickname());
    }

    @Test
    @DisplayName("아이디가 없을 때와 비밀번호가 틀릴 때 같은 메시지를 준다")
    void sameMessageForUnknownUserAndWrongPassword() {
        auth.signup("player1", "돌쇠", "abcd1234");

        AuthException wrongPassword = assertThrows(AuthException.class, () -> auth.login("player1", "abcd9999"));
        AuthException unknownUser = assertThrows(AuthException.class, () -> auth.login("nobody1", "abcd1234"));

        assertEquals(Reason.UNAUTHORIZED, wrongPassword.reason());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    @Test
    @DisplayName("5번 틀리면 맞는 비밀번호로도 잠시 로그인할 수 없다")
    void locksAfterRepeatedFailures() {
        auth.signup("player1", "돌쇠", "abcd1234");
        for (int i = 0; i < LoginAttemptGuard.MAX_FAILURES; i++) {
            assertThrows(AuthException.class, () -> auth.login("player1", "wrong9999"));
        }

        AuthException locked = assertThrows(AuthException.class, () -> auth.login("player1", "abcd1234"));
        assertEquals(Reason.TOO_MANY_ATTEMPTS, locked.reason());
        assertTrue(locked.getMessage().contains("5분"), locked.getMessage());

        clock.advance(LoginAttemptGuard.LOCK_TIME);
        assertEquals("돌쇠", auth.login("player1", "abcd1234").member().nickname());
    }

    @Test
    @DisplayName("로그아웃하면 그 토큰은 더 이상 쓸 수 없다")
    void logoutInvalidatesToken() {
        String token = auth.login(auth.signup("player1", "돌쇠", "abcd1234").member().username(), "abcd1234").token();

        auth.logout(token);

        assertTrue(auth.currentMember(token).isEmpty());
        assertEquals(Reason.UNAUTHORIZED, assertThrows(AuthException.class, () -> auth.requireMember(token)).reason());
    }

    @Test
    @DisplayName("7일이 지나면 로그인이 만료되고 정리 작업에서 지워진다")
    void sessionExpires() {
        String token = auth.signup("player1", "돌쇠", "abcd1234").token();
        clock.advance(AuthService.SESSION_LENGTH.minusSeconds(1));
        assertTrue(auth.currentMember(token).isPresent());

        clock.advance(Duration.ofSeconds(1));
        assertTrue(auth.currentMember(token).isEmpty());

        auth.signup("player2", "마당쇠", "abcd1234");
        clock.advance(AuthService.SESSION_LENGTH);
        assertEquals(1, auth.cleanupExpiredSessions());
        assertTrue(sessions.sessions.isEmpty());
    }

    @Test
    @DisplayName("게임 닉네임: 회원은 회원 닉네임, 게스트는 회원 닉네임을 쓸 수 없다")
    void resolvesPlayNickname() {
        String token = auth.signup("player1", "돌쇠", "abcd1234").token();

        assertEquals("돌쇠", auth.resolvePlayNickname(token, "아무거나"));
        assertEquals("손님", auth.resolvePlayNickname(null, " 손님 "));
        assertEquals("손님", auth.resolvePlayNickname("가짜토큰", "손님"));
        AuthException taken = assertThrows(AuthException.class, () -> auth.resolvePlayNickname(null, "돌쇠"));
        assertEquals(Reason.DUPLICATED, taken.reason());
    }

    @Test
    @DisplayName("같은 아이디로 동시에 가입해도 한 명만 성공한다")
    void concurrentSignup() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    auth.signup("player1", "닉" + n, "abcd1234");
                    return true;
                } catch (AuthException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int success = 0;
        for (Future<Boolean> f : results) {
            if (f.get(10, TimeUnit.SECONDS)) {
                success++;
            }
        }
        pool.shutdown();

        assertEquals(1, success);
        assertEquals(1, members.size());
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
