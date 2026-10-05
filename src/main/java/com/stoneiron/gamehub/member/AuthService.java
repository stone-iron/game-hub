package com.stoneiron.gamehub.member;

import com.stoneiron.gamehub.member.AuthException.Reason;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 회원가입·로그인·로그아웃·로그인 확인.
 *
 * <p>로그인하면 무작위 토큰을 발급하고, 서버에는 토큰의 SHA-256 해시만 저장한다.
 * 클라이언트는 요청마다 {@code Authorization: Bearer 토큰}으로 본인임을 알린다.</p>
 *
 * <p>Spring에 의존하지 않는다. 저장소는 인터페이스로 주입받아 테스트에서 메모리 구현으로 바꿀 수 있다.</p>
 */
public class AuthService {

    static final Duration SESSION_LENGTH = Duration.ofDays(7);
    static final Pattern USERNAME = Pattern.compile("^[a-z0-9]{4,20}$");
    static final int NICKNAME_MAX = 12;
    static final int PASSWORD_MIN = 8;
    static final int PASSWORD_MAX = 72;
    private static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final MemberStore members;
    private final AuthSessionStore sessions;
    private final PasswordHasher hasher;
    private final LoginAttemptGuard guard;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    /** 없는 아이디로 로그인할 때도 비밀번호 검사 시간을 똑같이 쓰기 위한 가짜 해시 */
    private final String dummyHash;

    public AuthService(MemberStore members, AuthSessionStore sessions, PasswordHasher hasher,
                       LoginAttemptGuard guard, Clock clock) {
        this.members = members;
        this.sessions = sessions;
        this.hasher = hasher;
        this.guard = guard;
        this.clock = clock;
        this.dummyHash = hasher.hash("dummy-password-for-timing");
    }

    // ---------------------------------------------------------------- 가입

    public AuthResult signup(String rawUsername, String rawNickname, String password) {
        String username = normalizeUsername(rawUsername);
        String nickname = rawNickname == null ? "" : rawNickname.strip();
        validateUsername(username);
        validateNickname(nickname);
        validatePassword(password);

        if (members.existsByUsername(username)) {
            throw new AuthException(Reason.DUPLICATED, "이미 사용 중인 아이디입니다.");
        }
        if (members.existsByNickname(nickname)) {
            throw new AuthException(Reason.DUPLICATED, "이미 사용 중인 닉네임입니다.");
        }
        Member saved = members.save(new Member(null, username, nickname, hasher.hash(password), clock.instant()));
        return issueSession(saved);
    }

    // ---------------------------------------------------------------- 로그인 · 로그아웃

    public AuthResult login(String rawUsername, String password) {
        String username = normalizeUsername(rawUsername);
        Instant now = clock.instant();
        Duration locked = guard.remainingLock(username, now);
        if (locked != null) {
            long minutes = Math.max(1, (locked.toSeconds() + 59) / 60);
            throw new AuthException(Reason.TOO_MANY_ATTEMPTS,
                    "로그인에 여러 번 실패했어요. " + minutes + "분 뒤에 다시 시도해 주세요.");
        }

        Optional<Member> member = members.findByUsername(username);
        // 아이디가 없어도 같은 시간을 들여 비교해서, 응답 속도로 가입 여부를 알 수 없게 한다.
        boolean ok = hasher.matches(password == null ? "" : password, member.map(Member::passwordHash).orElse(dummyHash));
        if (member.isEmpty() || !ok) {
            guard.recordFailure(username, now);
            // 아이디가 없는지 비밀번호가 틀렸는지 알려주지 않는다.
            throw new AuthException(Reason.UNAUTHORIZED, LOGIN_FAILED);
        }
        guard.recordSuccess(username);
        return issueSession(member.get());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByTokenHash(sha256(token));
        }
    }

    /** 토큰으로 로그인한 회원을 찾는다. 만료되었거나 없는 토큰이면 비어 있다. */
    public Optional<MemberProfile> currentMember(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256(token);
        Optional<AuthSession> session = sessions.findByTokenHash(hash);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(session.get().expiresAt())) {
            sessions.deleteByTokenHash(hash);
            return Optional.empty();
        }
        return members.findById(session.get().memberId()).map(Member::profile);
    }

    public MemberProfile requireMember(String token) {
        return currentMember(token)
                .orElseThrow(() -> new AuthException(Reason.UNAUTHORIZED, "로그인이 필요해요."));
    }

    /**
     * 게임에서 쓸 닉네임을 정한다.
     * 로그인한 사람은 회원 닉네임을 쓰고, 게스트는 회원이 쓰는 닉네임을 가져다 쓸 수 없다.
     */
    public String resolvePlayNickname(String token, String requested) {
        Optional<MemberProfile> me = currentMember(token);
        if (me.isPresent()) {
            return me.get().nickname();
        }
        String nickname = requested == null ? "" : requested.strip();
        if (!nickname.isEmpty() && members.existsByNickname(nickname)) {
            throw new AuthException(Reason.DUPLICATED, "회원이 사용 중인 닉네임이에요. 다른 닉네임을 쓰거나 로그인해 주세요.");
        }
        return nickname;
    }

    public int cleanupExpiredSessions() {
        Instant now = clock.instant();
        guard.purgeStale(now);
        return sessions.deleteExpired(now);
    }

    // ---------------------------------------------------------------- 내부 도우미

    private AuthResult issueSession(Member member) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new AuthSession(sha256(token), member.id(), clock.instant().plus(SESSION_LENGTH)));
        return new AuthResult(token, member.profile());
    }

    static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase();
    }

    private static void validateUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw new AuthException(Reason.INVALID_INPUT, "아이디는 영문 소문자와 숫자로 4~20자여야 해요.");
        }
    }

    private static void validateNickname(String nickname) {
        if (nickname.isEmpty() || nickname.length() > NICKNAME_MAX) {
            throw new AuthException(Reason.INVALID_INPUT, "닉네임은 1~" + NICKNAME_MAX + "자로 입력해 주세요.");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new AuthException(Reason.INVALID_INPUT,
                    "비밀번호는 " + PASSWORD_MIN + "자 이상 " + PASSWORD_MAX + "자 이하로 입력해 주세요.");
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
