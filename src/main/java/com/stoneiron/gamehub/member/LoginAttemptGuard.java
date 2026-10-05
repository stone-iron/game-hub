package com.stoneiron.gamehub.member;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아이디별 로그인 실패를 세어, 연속으로 여러 번 틀리면 잠시 로그인을 막는다.
 * 비밀번호를 하나씩 대입해 보는 공격을 느리게 만든다.
 *
 * <p>서버 메모리에 기록하므로 서버를 다시 켜면 초기화된다.
 * 서버를 여러 대로 늘릴 때는 Redis 같은 공용 저장소로 옮기면 된다.</p>
 */
public class LoginAttemptGuard {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(5);

    /** 마지막 실패 후 이 시간이 지나면 실패 횟수를 잊는다. */
    static final Duration FORGET_AFTER = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant lockedUntil, Instant lastFailureAt) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** 잠겨 있으면 남은 시간을, 아니면 null을 돌려준다. */
    public Duration remainingLock(String username, Instant now) {
        Attempts a = attempts.get(key(username));
        if (a == null || a.lockedUntil() == null || !now.isBefore(a.lockedUntil())) {
            return null;
        }
        return Duration.between(now, a.lockedUntil());
    }

    public void recordFailure(String username, Instant now) {
        attempts.compute(key(username), (k, a) -> {
            boolean fresh = a == null
                    || (a.lockedUntil() != null && !now.isBefore(a.lockedUntil()))
                    || !now.isBefore(a.lastFailureAt().plus(FORGET_AFTER));
            int failures = fresh ? 1 : a.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK_TIME) : null;
            return new Attempts(failures, lockedUntil, now);
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    /** 잠금이 풀렸고 오래된 기록을 지워 메모리가 계속 늘어나지 않게 한다. */
    public void purgeStale(Instant now) {
        attempts.values().removeIf(a ->
                (a.lockedUntil() == null || !now.isBefore(a.lockedUntil()))
                        && !now.isBefore(a.lastFailureAt().plus(FORGET_AFTER)));
    }

    int trackedCount() {
        return attempts.size();
    }

    private static String key(String username) {
        return username == null ? "" : username.toLowerCase();
    }
}
