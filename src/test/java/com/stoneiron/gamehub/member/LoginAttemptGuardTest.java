package com.stoneiron.gamehub.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptGuardTest {

    private final LoginAttemptGuard guard = new LoginAttemptGuard();
    private final Instant t0 = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    @DisplayName("5번 연속 실패하면 5분 동안 잠기고, 시간이 지나면 풀린다")
    void locksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("player1", t0);
        }
        assertTrue(guard.remainingLock("player1", t0) == null);

        guard.recordFailure("player1", t0);
        Duration remaining = guard.remainingLock("player1", t0.plusSeconds(60));
        assertNotNull(remaining);
        assertEquals(Duration.ofMinutes(4), remaining);

        assertTrue(guard.remainingLock("player1", t0.plus(LoginAttemptGuard.LOCK_TIME)) == null);
    }

    @Test
    @DisplayName("성공하면 실패 횟수가 초기화된다")
    void successResets() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("player1", t0);
        }
        guard.recordSuccess("player1");
        guard.recordFailure("player1", t0);

        assertTrue(guard.remainingLock("player1", t0) == null);
    }

    @Test
    @DisplayName("오래된 실패 기록은 잊고, 정리하면 메모리에서도 지운다")
    void forgetsOldFailures() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("player1", t0);
        }
        Instant later = t0.plus(LoginAttemptGuard.FORGET_AFTER);
        guard.recordFailure("player1", later);
        assertTrue(guard.remainingLock("player1", later) == null);

        guard.recordFailure("player2", t0);
        guard.purgeStale(later.plus(LoginAttemptGuard.FORGET_AFTER));
        assertEquals(0, guard.trackedCount());
    }
}
