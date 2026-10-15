package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.service.ConnectTicketService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Authenticated (under /api, not /api/auth): the caller's JWT says who the ticket is for. The browser then navigates
// to the returned url, which can't carry an Authorization header, so the ticket stands in for the JWT there.
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final ConnectTicketService connectTicketService;

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public BankController(ConnectTicketService connectTicketService) {
        this.connectTicketService = connectTicketService;
    }

    /**
     * Returns a relative url, so it resolves against whichever origin the client is on (the web app proxies
     * /auth/connect-bank to this API). Navigate to it with window.location, not fetch: it ends in a redirect to the bank.
     */
    @PostMapping(value = "/connect-url", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> connectUrl(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        String ticket = connectTicketService.issue(userId);
        return ResponseEntity.ok(Map.of("url", "/auth/connect-bank?ticket=" + ticket));
    }
}
