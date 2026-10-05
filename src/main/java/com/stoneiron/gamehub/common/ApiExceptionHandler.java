package com.stoneiron.gamehub.common;

import com.stoneiron.gamehub.game.GameException;
import com.stoneiron.gamehub.member.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * REST API 오류를 {"message": "..."} 형태로 통일한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(GameException.class)
    public ResponseEntity<Map<String, String>> handleGame(GameException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthException e) {
        HttpStatus status = switch (e.reason()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case DUPLICATED -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case TOO_MANY_ATTEMPTS -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "입력값을 확인해 주세요."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "요청 형식이 올바르지 않습니다."));
    }
}
