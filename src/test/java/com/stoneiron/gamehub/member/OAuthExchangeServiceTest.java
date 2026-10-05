package com.stoneiron.gamehub.member;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthExchangeServiceTest {

    @Test
    void exchangeCodeCanBeUsedOnlyOnce() {
        AuthServiceTest.MutableClock clock = new AuthServiceTest.MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
        OAuthExchangeService exchanges = new OAuthExchangeService(clock);
        AuthResult expected = new AuthResult("token", new MemberProfile(1L, "user@example.com", "회원"));
        String code = exchanges.create(expected);

        assertEquals(expected, exchanges.exchange(code).orElseThrow());
        assertTrue(exchanges.exchange(code).isEmpty());
    }

    @Test
    void exchangeCodeExpires() {
        AuthServiceTest.MutableClock clock = new AuthServiceTest.MutableClock(Instant.parse("2026-10-05T00:00:00Z"));
        OAuthExchangeService exchanges = new OAuthExchangeService(clock);
        String code = exchanges.create(new AuthResult("token",
                new MemberProfile(1L, "user@example.com", "회원")));

        clock.advance(Duration.ofMinutes(3));

        assertTrue(exchanges.exchange(code).isEmpty());
    }
}
