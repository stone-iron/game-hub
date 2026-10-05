package com.stoneiron.gamehub.member;

/**
 * 회원 관련 오류. 메시지는 그대로 화면에 표시된다.
 *
 * @see Reason
 */
public class AuthException extends RuntimeException {

    public enum Reason {
        /** 입력값이 규칙에 맞지 않음 (400) */
        INVALID_INPUT,
        /** 이미 사용 중인 아이디·닉네임 (409) */
        DUPLICATED,
        /** 아이디·비밀번호가 틀렸거나 로그인이 필요함 (401) */
        UNAUTHORIZED,
        /** 로그인 시도를 너무 많이 함 (429) */
        TOO_MANY_ATTEMPTS
    }

    private final Reason reason;

    public AuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
