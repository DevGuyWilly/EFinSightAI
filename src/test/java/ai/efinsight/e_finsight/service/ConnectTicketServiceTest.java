package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectTicketServiceTest {
    private final JwtUtil jwtUtil = new JwtUtil();
    private ConnectTicketService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-jwt-secret-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);
        service = new ConnectTicketService(jwtUtil);
    }

    @Test
    void anIssuedTicketRedeemsToTheUserItWasIssuedTo() {
        assertThat(service.redeem(service.issue(42L))).isEqualTo(42L);
    }

    @Test
    void aTicketWorksOnlyOnce() {
        String ticket = service.issue(42L);

        assertThat(service.redeem(ticket)).isEqualTo(42L);
        assertThat(service.redeem(ticket)).isNull();
    }

    @Test
    void separateTicketsEachWorkOnce() {
        String first = service.issue(42L);
        String second = service.issue(7L);

        assertThat(service.redeem(first)).isEqualTo(42L);
        assertThat(service.redeem(second)).isEqualTo(7L);
    }

    @Test
    void aFreshServiceInstanceStillAcceptsATicketItHasNotSeen() {
        // Single use is only an extra guard held in memory: validity comes from the signature, so a restart or a
        // second instance can't reject a legitimate ticket.
        String ticket = service.issue(42L);

        assertThat(new ConnectTicketService(jwtUtil).redeem(ticket)).isEqualTo(42L);
    }

    @Test
    void missingBlankAndGarbageTicketsAreRejected() {
        assertThat(service.redeem(null)).isNull();
        assertThat(service.redeem("")).isNull();
        assertThat(service.redeem("   ")).isNull();
        assertThat(service.redeem("not-a-ticket")).isNull();
    }

    @Test
    void aLoginTokenIsNotATicket() {
        assertThat(service.redeem(jwtUtil.generateToken("user@example.com", 42L))).isNull();
    }
}
