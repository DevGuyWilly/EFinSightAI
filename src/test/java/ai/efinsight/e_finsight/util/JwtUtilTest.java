package ai.efinsight.e_finsight.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {
    private static final String SECRET = "test-jwt-secret-that-is-long-enough-for-hs256";

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);
    }

    @Test
    void oauthStateRoundTripsToTheUser() {
        String state = jwtUtil.generateOAuthState(42L);

        assertThat(jwtUtil.getUserIdFromOAuthState(state)).isEqualTo(42L);
    }

    @Test
    void oauthStatesAreUniqueAndUrlSafe() {
        String a = jwtUtil.generateOAuthState(42L);
        String b = jwtUtil.generateOAuthState(42L);

        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_.-]+");
    }

    @Test
    void aStateIsNeverAcceptedAsALoginToken() {
        assertThat(jwtUtil.validateToken(jwtUtil.generateOAuthState(42L))).isFalse();
    }

    @Test
    void aLoginTokenIsNeverAcceptedAsAState() {
        String login = jwtUtil.generateToken("user@example.com", 42L);

        assertThat(jwtUtil.validateToken(login)).isTrue();
        assertThat(jwtUtil.getUserIdFromOAuthState(login)).isNull();
    }

    @Test
    void expiredTamperedOrGarbageStatesAreRejected() {
        String expired = Jwts.builder()
                .subject("42")
                .claim(JwtUtil.PURPOSE_CLAIM, JwtUtil.OAUTH_STATE_PURPOSE)
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        String state = jwtUtil.generateOAuthState(42L);
        String tampered = state.substring(0, state.length() - 2) + (state.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtUtil.getUserIdFromOAuthState(expired)).isNull();
        assertThat(jwtUtil.getUserIdFromOAuthState(tampered)).isNull();
        assertThat(jwtUtil.getUserIdFromOAuthState("not-a-jwt")).isNull();
    }
}
