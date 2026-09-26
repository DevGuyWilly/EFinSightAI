package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.config.TrueLayerConfig;
import ai.efinsight.e_finsight.service.TrueLayerAuthService;
import ai.efinsight.e_finsight.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;


/**
 *
 * - Controller to manage Auth via TrueLayerAPI
 * -- Connect Bank Account via TrueLayer API --- GET("/connect")
 * --
 * **/
@Controller
public class TrueLayerAuthController {
    // Manual logger (Lombok @Slf4j should generate this, but adding manually as workaround)
    private static final Logger log = LoggerFactory.getLogger(TrueLayerAuthController.class);
    
    private final TrueLayerConfig config;
    private final TrueLayerAuthService authService;
    private final JwtUtil jwtUtil;

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public TrueLayerAuthController(TrueLayerConfig config, TrueLayerAuthService authService, JwtUtil jwtUtil) {
        this.config = config;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    // This endpoint is permitAll in SecurityConfig since it must be reachable via plain
    // browser navigation (e.g. a link opened on a phone), which cannot carry an
    // Authorization header. The JWT is validated manually here instead of via the
    // header-based JwtAuthenticationFilter/Authentication principal used everywhere else.
    @GetMapping("/auth/connect-bank")
    public RedirectView connectBank(@RequestParam("token") String token){
        if (!jwtUtil.validateToken(token)) {
            log.warn("Rejected connect-bank request with invalid or expired token");
            return new RedirectView("/auth/error?message=invalid_token");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);

        // Signed, short-lived state that ties the callback back to this user
        String state = authService.generateAndStoreState(String.valueOf(userId));

        // Build the auth URL dynamically using config values (matching TrueLayer official format)
        String redirectUri = java.net.URLEncoder.encode(config.getRedirectUri(), java.nio.charset.StandardCharsets.UTF_8);
        String scope = "info%20accounts%20balance%20cards%20transactions%20direct_debits%20standing_orders%20offline_access";
        // Using official TrueLayer format: https://auth.truelayer.com/?response_type=code&...
        String authUrl = String.format(
            "https://auth.truelayer.com/?response_type=code&client_id=%s&scope=%s&redirect_uri=%s&providers=uk-ob-all%%20uk-oauth-all&state=%s",
            config.getClientId(),
            scope,
            redirectUri,
            state
        );

        // The URL carries the state token, so it isn't logged
        log.info("Initiating bank connection for user ID: {}", userId);

        return new RedirectView(authUrl);
    }

    @GetMapping("/callback")
    public String handleCallBack(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error) {
        // The authorization code and state are credentials, so only their presence is logged
        log.info("Callback received - code present: {}, state present: {}, error: {}",
                code != null && !code.isEmpty(), state != null && !state.isEmpty(), error);

        // Handle error from TrueLayer
        if (error != null) {
            log.error("Bank authentication error from TrueLayer: {}", error);
            return "redirect:/auth/error?message=" + java.net.URLEncoder.encode(error, java.nio.charset.StandardCharsets.UTF_8);
        }

        // Validate required parameters
        if (code == null || code.isEmpty()) {
            log.error("Missing authorization code in callback");
            log.error("This usually means: 1) User cancelled authorization, 2) Redirect URI mismatch, or 3) OAuth flow error");
            return "redirect:/auth/error?message=missing_authorization_code";
        }

        if (state == null || state.isEmpty()) {
            log.error("Missing state parameter in callback");
            return "redirect:/auth/error?message=missing_state_parameter";
        }

        try {
            // Validate state and get user ID
            String userId = authService.validateStateAndGetUserId(state);
            if (userId == null) {
                log.error("Invalid or expired state parameter in callback");
                return "redirect:/auth/error?message=invalid_state";
            }
            // Exchange authorization code for tokens
            authService.exchangeCodeForTokens(code, userId);

            log.info("Successfully connected bank for user: {}", userId);

            // Redirect to success page
            return "redirect:/auth/success";

        } catch (Exception e) {
            log.error("Error during token exchange", e);
            return "redirect:/auth/error?message=token_exchange_failed";
        }
    }

    @GetMapping("/auth/success")
    public String authSuccess() {
        return "bank-connected-success";
    }

    @GetMapping("/auth/error")
    public  String authError(@RequestParam String message){
        log.error("Bank connection error: {}", message);
        return "bank-connection-error";
    }
}
