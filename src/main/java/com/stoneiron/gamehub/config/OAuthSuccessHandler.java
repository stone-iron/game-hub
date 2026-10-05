package com.stoneiron.gamehub.config;

import com.stoneiron.gamehub.member.AuthResult;
import com.stoneiron.gamehub.member.AuthException;
import com.stoneiron.gamehub.member.OAuthExchangeService;
import com.stoneiron.gamehub.member.SocialAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final SocialAuthService socialAuth;
    private final OAuthExchangeService exchanges;

    public OAuthSuccessHandler(SocialAuthService socialAuth, OAuthExchangeService exchanges) {
        this.socialAuth = socialAuth;
        this.exchanges = exchanges;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            String provider = token.getAuthorizedClientRegistrationId();
            SocialProfile profile = SocialProfile.from(provider, token.getPrincipal().getAttributes());
            AuthResult result = socialAuth.login(provider, profile.subject(), profile.email(), profile.nickname());
            String code = exchanges.create(result);
            clearTemporarySession(request);
            response.sendRedirect("/?oauth_code=" + encode(code));
        } catch (RuntimeException e) {
            clearTemporarySession(request);
            response.sendRedirect("/?oauth_error=" + encode(message(e)));
        }
    }

    private static void clearTemporarySession(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String message(RuntimeException e) {
        if ((e instanceof AuthException || e instanceof OAuth2AuthenticationException)
                && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return "소셜 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.";
    }
}
