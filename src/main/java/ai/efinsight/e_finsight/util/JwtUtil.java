package ai.efinsight.e_finsight.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    // Manual logger (Lombok @Slf4j should generate this, but adding manually as workaround)
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    static final String PURPOSE_CLAIM = "purpose";
    static final String OAUTH_STATE_PURPOSE = "truelayer_oauth_state";
    // Long enough to pick a bank and sign in, short enough to limit replay of a leaked state
    static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(15);
    static final String CONNECT_TICKET_PURPOSE = "truelayer_connect_ticket";
    // Only has to survive the hop from POST /api/bank/connect-url to the browser navigation that follows it
    static final Duration CONNECT_TICKET_TTL = Duration.ofSeconds(60);

    /** What a valid connect ticket says: who it was issued to, its unique id, and when it stops working. */
    public record ConnectTicket(Long userId, String id, Instant expiresAt) {
    }

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email, Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("userId", Long.class);
    }

    // Valid login token: signed, unexpired, and not a special-purpose token (such as an OAuth state)
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.get(PURPOSE_CLAIM) != null) {
                log.warn("Rejected a {} token used as a login token", claims.get(PURPOSE_CLAIM));
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Signed, short-lived OAuth `state` for the TrueLayer bank-connection flow. Stateless, so the callback works on
     * any instance and after restarts. Carries a purpose claim so it's never accepted as a login token (it travels
     * through the bank's redirect URLs) and a login token is never accepted as a state.
     */
    public String generateOAuthState(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(PURPOSE_CLAIM, OAUTH_STATE_PURPOSE)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + OAUTH_STATE_TTL.toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    // The user id a state was issued for, or null if it's invalid, expired, or not an OAuth state
    public Long getUserIdFromOAuthState(String state) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(state)
                    .getPayload();
            if (!OAUTH_STATE_PURPOSE.equals(claims.get(PURPOSE_CLAIM))) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid OAuth state: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Short-lived ticket that lets a browser navigation start the bank-connection flow. The navigation to
     * /auth/connect-bank can't carry an Authorization header, so before this the login JWT itself went in the query
     * string (browser history, server logs, Referer). A ticket is only good for starting that flow, for about a
     * minute, and a login token is never accepted in its place. The purpose claim keeps it from working as a login
     * token or as an OAuth state, and the reverse.
     */
    public String generateConnectTicket(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(PURPOSE_CLAIM, CONNECT_TICKET_PURPOSE)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + CONNECT_TICKET_TTL.toMillis()))
                .signWith(getSigningKey())
                .compact();
    }

    // The ticket's contents, or null if it's invalid, expired, or not a connect ticket
    public ConnectTicket parseConnectTicket(String ticket) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(ticket)
                    .getPayload();
            // No id or no expiry can't be tracked for single use, so it isn't a ticket this service ever issued
            if (!CONNECT_TICKET_PURPOSE.equals(claims.get(PURPOSE_CLAIM)) || claims.getId() == null
                    || claims.getExpiration() == null) {
                return null;
            }
            return new ConnectTicket(Long.valueOf(claims.getSubject()), claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid connect ticket: {}", e.getMessage());
            return null;
        }
    }
}
