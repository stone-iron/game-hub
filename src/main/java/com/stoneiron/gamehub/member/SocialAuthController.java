package com.stoneiron.gamehub.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/social")
public class SocialAuthController {

    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final OAuthExchangeService exchanges;

    public SocialAuthController(ObjectProvider<ClientRegistrationRepository> registrations,
                                OAuthExchangeService exchanges) {
        this.registrations = registrations;
        this.exchanges = exchanges;
    }

    public record ExchangeRequest(@NotBlank String code) {
    }

    @GetMapping("/providers")
    public List<Map<String, String>> providers() {
        ClientRegistrationRepository repository = registrations.getIfAvailable();
        if (!(repository instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : iterable) {
            ClientRegistration registration = (ClientRegistration) item;
            result.add(Map.of("id", registration.getRegistrationId(), "name", registration.getClientName()));
        }
        return result;
    }

    @PostMapping("/exchange")
    public AuthResult exchange(@Valid @RequestBody ExchangeRequest request) {
        return exchanges.exchange(request.code())
                .orElseThrow(() -> new AuthException(AuthException.Reason.UNAUTHORIZED,
                        "소셜 로그인 정보가 만료되었어요. 다시 로그인해 주세요."));
    }
}
