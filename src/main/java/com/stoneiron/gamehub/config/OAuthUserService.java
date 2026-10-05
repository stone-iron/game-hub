package com.stoneiron.gamehub.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** 응답 본문이 response 객체 안에 들어오는 네이버 사용자 정보를 표준 형태로 펼친다. */
@Component
public class OAuthUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        if (!"naver".equals(request.getClientRegistration().getRegistrationId())) return user;
        Object response = user.getAttributes().get("response");
        if (!(response instanceof Map<?, ?> map) || map.get("id") == null) {
            throw new OAuth2AuthenticationException("네이버 회원 정보를 확인할 수 없습니다.");
        }
        return new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                (Map<String, Object>) map, "id");
    }
}
