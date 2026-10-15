package ai.efinsight.e_finsight.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    @Test
    void connectTicketRoundTripsToTheUserAndCarriesAnIdAndExpiry() {
        JwtUtil.ConnectTicket ticket = jwtUtil.parseConnectTicket(jwtUtil.generateConnectTicket(42L));

        assertThat(ticket).isNotNull();
        assertThat(ticket.userId()).isEqualTo(42L);
        assertThat(ticket.id()).isNotBlank();
        assertThat(ticket.expiresAt()).isAfter(Instant.now()).isBeforeOrEqualTo(Instant.now().plus(JwtUtil.CONNECT_TICKET_TTL));
    }

    @Test
    void connectTicketsAreUniqueAndUrlSafe() {
        String a = jwtUtil.generateConnectTicket(42L);
        String b = jwtUtil.generateConnectTicket(42L);

        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_.-]+");
    }

    @Test
    void aTicketIsNeverAcceptedAsALoginToken() {
        assertThat(jwtUtil.validateToken(jwtUtil.generateConnectTicket(42L))).isFalse();
    }

    @Test
    void aLoginTokenOrAStateIsNeverAcceptedAsATicket() {
        assertThat(jwtUtil.parseConnectTicket(jwtUtil.generateToken("user@example.com", 42L))).isNull();
        assertThat(jwtUtil.parseConnectTicket(jwtUtil.generateOAuthState(42L))).isNull();
    }

    @Test
    void aTicketIsNeverAcceptedAsAState() {
        assertThat(jwtUtil.getUserIdFromOAuthState(jwtUtil.generateConnectTicket(42L))).isNull();
    }

    @Test
    void expiredTamperedOrGarbageTicketsAreRejected() {
        String expired = Jwts.builder()
                .subject("42")
                .claim(JwtUtil.PURPOSE_CLAIM, JwtUtil.CONNECT_TICKET_PURPOSE)
                .id("some-id")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        String ticket = jwtUtil.generateConnectTicket(42L);
        String tampered = ticket.substring(0, ticket.length() - 2) + (ticket.endsWith("AA") ? "BB" : "AA");

        assertThat(jwtUtil.parseConnectTicket(expired)).isNull();
        assertThat(jwtUtil.parseConnectTicket(tampered)).isNull();
        assertThat(jwtUtil.parseConnectTicket("not-a-jwt")).isNull();
    }

    @Test
    void aTicketSignedWithAnotherKeyIsRejected() {
        String foreign = Jwts.builder()
                .subject("42")
                .claim(JwtUtil.PURPOSE_CLAIM, JwtUtil.CONNECT_TICKET_PURPOSE)
                .id("some-id")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-secret-for-hs256-signing".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtUtil.parseConnectTicket(foreign)).isNull();
    }
}
