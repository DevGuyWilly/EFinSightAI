package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.config.TrueLayerConfig;
import ai.efinsight.e_finsight.service.TrueLayerAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import jakarta.servlet.http.HttpServletRequest;
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

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public TrueLayerAuthController(TrueLayerConfig config, TrueLayerAuthService authService) {
        this.config = config;
        this.authService = authService;
    }

    @GetMapping("/auth/connect-bank")
    public RedirectView connectBank(Authentication authentication){
        // Get userId from Spring Security context (set by JWT filter)
        Long userId = (Long) authentication.getPrincipal();

        // Generate and store state parameter for user reconciliation
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

        log.info("Initiating bank connection for user ID: {}, state: {}", userId, state);
        log.info("Auth URL: {}", authUrl);

        return new RedirectView(authUrl);
    }

    @GetMapping("/callback")
    public String handleCallBack(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state,
                                 @RequestParam(required = false) String error,
                                 HttpServletRequest request) {
        // Log the full request URL and all parameters for debugging
        String fullUrl = request.getRequestURL().toString();
        String queryString = request.getQueryString();
        log.info("Callback received - Full URL: {}?{}", fullUrl, queryString != null ? queryString : "");
        log.info("Callback parameters - code: {}, state: {}, error: {}", code, state, error);
        
        // Handle error from TrueLayer
        if (error != null) {
            log.error("Bank authentication error from TrueLayer: {}", error);
            return "redirect:/auth/error?message=" + error;
        }

        // Validate required parameters
        if (code == null || code.isEmpty()) {
            log.error("Missing authorization code in callback. Full URL: {}?{}", fullUrl, queryString != null ? queryString : "");
            log.error("This usually means: 1) User cancelled authorization, 2) Redirect URI mismatch, or 3) OAuth flow error");
            return "redirect:/auth/error?message=missing_authorization_code";
        }

        if (state == null || state.isEmpty()) {
            log.error("Missing state parameter in callback. Full URL: {}?{}", fullUrl, queryString != null ? queryString : "");
            return "redirect:/auth/error?message=missing_state_parameter";
        }

        try {
            // Validate state and get user ID
            String userId = authService.validateStateAndGetUserId(state);
            if (userId == null) {
                log.error("Invalid state parameter: {}", state);
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
