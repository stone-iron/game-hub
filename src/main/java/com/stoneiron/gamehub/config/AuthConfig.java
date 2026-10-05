package com.stoneiron.gamehub.config;

import com.stoneiron.gamehub.member.AuthService;
import com.stoneiron.gamehub.member.AuthSessionStore;
import com.stoneiron.gamehub.member.LoginAttemptGuard;
import com.stoneiron.gamehub.member.MemberStore;
import com.stoneiron.gamehub.member.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 회원 기능 조립. AuthService는 Spring에 의존하지 않도록 여기서만 빈으로 등록한다.
 */
@Configuration
public class AuthConfig {

    @Bean
    public AuthService authService(MemberStore members, AuthSessionStore sessions, Clock clock) {
        return new AuthService(members, sessions, new PasswordHasher(), new LoginAttemptGuard(), clock);
    }
}
