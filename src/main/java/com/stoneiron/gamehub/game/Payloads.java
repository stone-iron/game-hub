package com.stoneiron.gamehub.game;

import java.util.Map;

/**
 * 클라이언트가 보낸 JSON 값을 안전하게 꺼내는 도우미.
 * 값이 없거나 형식이 틀리면 사용자에게 보여줄 수 있는 {@link GameException}을 던진다.
 */
public final class Payloads {

    private Payloads() {
    }

    public static int intValue(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // 아래에서 공통 메시지로 처리
            }
        }
        throw new GameException("요청 값이 올바르지 않습니다: " + key);
    }

    public static String text(Map<String, Object> data, String key) {
        Object value = data == null ? null : data.get(key);
        if (value == null) {
            throw new GameException("요청 값이 비어 있습니다: " + key);
        }
        return value.toString();
    }
}
