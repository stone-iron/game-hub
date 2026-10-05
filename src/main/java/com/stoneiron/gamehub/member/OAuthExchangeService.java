package com.stoneiron.gamehub.member;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** OAuth 콜백과 브라우저 사이에서 앱 로그인 토큰을 일회성으로 전달한다. */
@Service
public class OAuthExchangeService {

    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OAuthExchangeService(Clock clock) {
        this.clock = clock;
    }

    public String create(AuthResult result) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(code, new Pending(result, clock.instant().plus(LIFETIME)));
        return code;
    }

    public Optional<AuthResult> exchange(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        Pending value = pending.remove(code);
        if (value == null || !clock.instant().isBefore(value.expiresAt())) return Optional.empty();
        return Optional.of(value.result());
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        Instant now = clock.instant();
        pending.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record Pending(AuthResult result, Instant expiresAt) {
    }
}
