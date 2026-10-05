package com.stoneiron.gamehub.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    @DisplayName("맞는 비밀번호만 통과하고 해시에 원문이 들어가지 않는다")
    void matchesOnlyCorrectPassword() {
        String stored = hasher.hash("secret123");

        assertTrue(hasher.matches("secret123", stored));
        assertFalse(hasher.matches("secret124", stored));
        assertFalse(stored.contains("secret123"));
    }

    @Test
    @DisplayName("같은 비밀번호라도 솔트가 달라 해시가 매번 다르다")
    void samePasswordDifferentHash() {
        String a = hasher.hash("secret123");
        String b = hasher.hash("secret123");

        assertFalse(a.equals(b));
        assertTrue(hasher.matches("secret123", a));
        assertTrue(hasher.matches("secret123", b));
    }

    @Test
    @DisplayName("반복 횟수가 저장되어 있어 횟수를 바꿔도 기존 해시를 검증할 수 있다")
    void verifiesWithStoredIterations() {
        String old = new PasswordHasher(500).hash("secret123");

        assertTrue(hasher.matches("secret123", old));
    }

    @Test
    @DisplayName("형식이 깨진 해시는 예외 없이 실패로 처리한다")
    void malformedHashFails() {
        assertFalse(hasher.matches("secret123", "plain-text"));
        assertFalse(hasher.matches("secret123", "pbkdf2$abc$$"));
        assertFalse(hasher.matches("secret123", "pbkdf2$1000$!!!$!!!"));
        assertFalse(hasher.matches(null, hasher.hash("secret123")));
    }
}
