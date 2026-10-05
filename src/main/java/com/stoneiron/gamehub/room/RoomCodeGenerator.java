package com.stoneiron.gamehub.room;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * 친구에게 알려 주는 6자리 입장 코드를 만든다.
 * 헷갈리기 쉬운 글자(0/O, 1/I/L)는 빼서 말로 불러 줘도 틀리지 않게 했다.
 */
public class RoomCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate(Predicate<String> inUse) {
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder code = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            if (!inUse.test(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("입장 코드를 만들지 못했습니다.");
    }
}
