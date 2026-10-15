package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.util.JwtUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and redeems the short-lived tickets that start the TrueLayer bank-connection flow.
 *
 * A ticket is a signed token (see JwtUtil.generateConnectTicket), so any instance can validate it and a restart
 * doesn't lose one in flight. On top of that each ticket can be redeemed once per instance: ids that have been used
 * are remembered until the ticket would have expired anyway. That memory is deliberately only an extra guard and
 * not what makes a ticket valid, so it can never reject a legitimate redemption. With several instances a replay
 * could still land on an instance that hasn't seen the id, but the ticket only lasts about a minute and only starts
 * the flow for the user it was issued to.
 */
@Service
public class ConnectTicketService {

    private final JwtUtil jwtUtil;
    // ticket id -> when that ticket expires (so entries can be dropped once they can no longer be replayed)
    private final Map<String, Instant> redeemed = new ConcurrentHashMap<>();

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public ConnectTicketService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public String issue(Long userId) {
        return jwtUtil.generateConnectTicket(userId);
    }

    /** The user id the ticket was issued to, or null if it's invalid, expired, not a ticket, or already used. */
    public Long redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        JwtUtil.ConnectTicket parsed = jwtUtil.parseConnectTicket(ticket);
        if (parsed == null) {
            return null;
        }
        forgetExpired();
        if (redeemed.putIfAbsent(parsed.id(), parsed.expiresAt()) != null) {
            return null;
        }
        return parsed.userId();
    }

    private void forgetExpired() {
        Instant now = Instant.now();
        redeemed.values().removeIf(expiresAt -> expiresAt.isBefore(now));
    }
}
