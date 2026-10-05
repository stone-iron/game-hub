package com.stoneiron.gamehub.member;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 비밀번호 해시. JDK 표준 PBKDF2(HMAC-SHA256)를 사용해 외부 라이브러리 없이 동작한다.
 *
 * <ul>
 *     <li>회원마다 무작위 솔트를 써서, 같은 비밀번호라도 저장되는 값이 모두 다르다.</li>
 *     <li>반복 횟수를 크게 잡아 무차별 대입 비용을 높인다.</li>
 *     <li>비교는 {@link MessageDigest#isEqual}로 해서 응답 시간 차이로 정보가 새지 않게 한다.</li>
 * </ul>
 *
 * <p>저장 형식: {@code pbkdf2$반복횟수$솔트(Base64)$해시(Base64)}.
 * 반복 횟수를 함께 저장하므로 나중에 횟수를 올려도 기존 회원은 그대로 로그인할 수 있다.</p>
 */
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher() {
        this(210_000);
    }

    /** 테스트에서는 반복 횟수를 줄여 빠르게 돌린다. */
    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            Base64.Decoder b64 = Base64.getDecoder();
            byte[] salt = b64.decode(parts[2]);
            byte[] expected = b64.decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, storedIterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("비밀번호 해시를 만들 수 없습니다.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
