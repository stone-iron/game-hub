package com.stoneiron.gamehub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 환경변수에 키가 들어온 제공자만 OAuth 로그인 대상으로 등록한다. */
@Configuration
public class OAuthClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${social.google.client-id:}") String googleId,
            @Value("${social.google.client-secret:}") String googleSecret,
            @Value("${social.kakao.client-id:}") String kakaoId,
            @Value("${social.kakao.client-secret:}") String kakaoSecret,
            @Value("${social.naver.client-id:}") String naverId,
            @Value("${social.naver.client-secret:}") String naverSecret) {
        Map<String, ClientRegistration> clients = new LinkedHashMap<>();
        if (present(googleId) && present(googleSecret)) clients.put("google", google(googleId, googleSecret));
        if (present(kakaoId)) clients.put("kakao", kakao(kakaoId, kakaoSecret));
        if (present(naverId) && present(naverSecret)) clients.put("naver", naver(naverId, naverSecret));
        return new OptionalClientRegistrationRepository(clients);
    }

    private static ClientRegistration google(String id, String secret) {
        return base("google", "Google", id, secret, ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();
    }

    private static ClientRegistration kakao(String id, String secret) {
        ClientAuthenticationMethod method = present(secret)
                ? ClientAuthenticationMethod.CLIENT_SECRET_POST : ClientAuthenticationMethod.NONE;
        return base("kakao", "Kakao", id, secret, method)
                .scope("profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .build();
    }

    private static ClientRegistration naver(String id, String secret) {
        return base("naver", "Naver", id, secret, ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("id")
                .build();
    }

    private static ClientRegistration.Builder base(String registrationId, String name, String id, String secret,
                                                    ClientAuthenticationMethod authenticationMethod) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientName(name)
                .clientId(id.strip())
                .clientSecret(secret == null ? "" : secret.strip())
                .clientAuthenticationMethod(authenticationMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static final class OptionalClientRegistrationRepository
            implements ClientRegistrationRepository, Iterable<ClientRegistration> {
        private final Map<String, ClientRegistration> clients;

        private OptionalClientRegistrationRepository(Map<String, ClientRegistration> clients) {
            this.clients = Map.copyOf(clients);
        }

        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return clients.get(registrationId);
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            List<ClientRegistration> ordered = List.of("google", "kakao", "naver").stream()
                    .map(clients::get).filter(java.util.Objects::nonNull).toList();
            return ordered.iterator();
        }
    }
}
