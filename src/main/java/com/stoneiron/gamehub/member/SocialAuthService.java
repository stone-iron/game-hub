package com.stoneiron.gamehub.member;

import com.stoneiron.gamehub.member.AuthException.Reason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/** OAuth 제공자의 검증된 사용자 정보를 겜허브 회원과 연결한다. */
@Service
public class SocialAuthService {

    private final MemberStore members;
    private final SocialAccountStore socialAccounts;
    private final AuthService authService;
    private final Clock clock;

    public SocialAuthService(MemberStore members, SocialAccountStore socialAccounts,
                             AuthService authService, Clock clock) {
        this.members = members;
        this.socialAccounts = socialAccounts;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional
    public synchronized AuthResult login(String rawProvider, String rawSubject,
                                         String rawEmail, String preferredNickname) {
        String provider = cleanRequired(rawProvider, "소셜 로그인 제공자 정보가 없습니다.").toLowerCase(Locale.ROOT);
        String subject = cleanRequired(rawSubject, "소셜 계정 식별 정보가 없습니다.");

        var linked = socialAccounts.find(provider, subject);
        if (linked.isPresent()) {
            Member member = members.findById(linked.get().memberId())
                    .orElseThrow(() -> new AuthException(Reason.UNAUTHORIZED, "연결된 회원을 찾을 수 없습니다."));
            return authService.issueSession(member);
        }

        String email = AuthService.normalizeEmail(rawEmail);
        AuthService.validateEmail(email);
        Member member = members.findByEmail(email)
                .orElseGet(() -> members.save(new Member(null, email, availableNickname(preferredNickname, email),
                        null, clock.instant())));
        socialAccounts.save(new SocialAccount(provider, subject, member.id()));
        return authService.issueSession(member);
    }

    private String availableNickname(String preferred, String email) {
        String base = preferred == null ? "" : preferred.strip();
        if (base.isBlank()) base = email.substring(0, email.indexOf('@'));
        base = base.replaceAll("[\\p{Cc}\\p{Cf}]", "");
        if (base.isBlank()) base = "플레이어";
        if (base.length() > AuthService.NICKNAME_MAX) base = base.substring(0, AuthService.NICKNAME_MAX);
        if (!members.existsByNickname(base)) return base;

        for (int suffix = 2; suffix < 10_000; suffix++) {
            String number = String.valueOf(suffix);
            int maxBase = AuthService.NICKNAME_MAX - number.length();
            String candidate = base.substring(0, Math.min(base.length(), maxBase)) + number;
            if (!members.existsByNickname(candidate)) return candidate;
        }
        throw new AuthException(Reason.DUPLICATED, "사용할 수 있는 닉네임을 만들지 못했습니다.");
    }

    private static String cleanRequired(String value, String message) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty()) throw new AuthException(Reason.INVALID_INPUT, message);
        return cleaned;
    }
}
