package com.stoneiron.gamehub.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·로그인·로그아웃·내 정보 API.
 * 로그인 후에는 요청 헤더 {@code Authorization: Bearer 토큰}으로 본인을 확인한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record SignupRequest(@NotBlank @Size(max = 20) String username,
                                @NotBlank @Size(max = 12) String nickname,
                                @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Size(max = 20) String username,
                               @NotBlank @Size(max = 72) String password) {
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResult signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.username(), request.nickname(), request.password());
    }

    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(bearer(authorization));
    }

    @GetMapping("/me")
    public MemberProfile me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.requireMember(bearer(authorization));
    }

    /** 만료된 로그인 상태를 한 시간마다 정리한다. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void cleanupExpiredSessions() {
        authService.cleanupExpiredSessions();
    }

    public static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
