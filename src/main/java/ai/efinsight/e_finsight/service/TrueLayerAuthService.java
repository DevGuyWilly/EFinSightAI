package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.config.TrueLayerConfig;
import ai.efinsight.e_finsight.model.TokenResponse;
import ai.efinsight.e_finsight.model.User;
import ai.efinsight.e_finsight.repository.UserRepository;
import ai.efinsight.e_finsight.repository.UserTokenRepository;
import ai.efinsight.e_finsight.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Service
public class TrueLayerAuthService {
    // Manual logger (Lombok @Slf4j should generate this, but adding manually as workaround)
    private static final Logger log = LoggerFactory.getLogger(TrueLayerAuthService.class);

    private final TrueLayerConfig config;
    private final UserTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    // Manual constructor (Lombok @RequiredArgsConstructor should generate this, but adding manually as workaround)
    public TrueLayerAuthService(TrueLayerConfig config, UserTokenRepository tokenRepository, UserRepository userRepository,
                                JwtUtil jwtUtil) {
        this.config = config;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    // OAuth state is a signed, short-lived JWT (see JwtUtil), so no server-side storage is needed
    public String generateAndStoreState(String userId) {
        return jwtUtil.generateOAuthState(Long.valueOf(userId));
    }

    // The user id the state was issued for, or null if the state is invalid or expired
    public String validateStateAndGetUserId(String state) {
        Long userId = jwtUtil.getUserIdFromOAuthState(state);
        return userId != null ? String.valueOf(userId) : null;
    }

    public void exchangeCodeForTokens(String code, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());

        log.info("Exchanging authorization code for tokens (user: {}, redirect URI: {})", userId, config.getRedirectUri());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", config.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    config.getTokenUri(),
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse tokens = response.getBody();

                // Store tokens in database
                tokenRepository.saveTokens(
                        userId,
                        tokens.getAccessToken(),
                        tokens.getRefreshToken(),
                        LocalDateTime.now(),
                        tokens.getExpiresIn()
                );

                // Update user's bankConnected flag
                User user = userRepository.findById(Long.parseLong(userId))
                        .orElseThrow(() -> new RuntimeException("User not found"));
                user.setBankConnected(true);
                userRepository.save(user);

                log.info("Bank successfully connected for user: {}", userId);
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to exchange code for tokens - HTTP Error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("Redirect URI used: {}", config.getRedirectUri());
            throw new RuntimeException("Token exchange failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to exchange code for tokens", e);
            throw new RuntimeException("Token exchange failed", e);
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public String refreshAccessToken(String userId) {
        ai.efinsight.e_finsight.model.UserToken userToken = tokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No tokens found for user: " + userId));

        if (userToken.getRefreshToken() == null || userToken.getRefreshToken().isEmpty()) {
            throw new RuntimeException("No refresh token available for user: " + userId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", userToken.getRefreshToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                    config.getTokenUri(),
                    HttpMethod.POST,
                    request,
                    TokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse tokens = response.getBody();

                // Update tokens in database
                tokenRepository.saveTokens(
                        userId,
                        tokens.getAccessToken(),
                        tokens.getRefreshToken() != null ? tokens.getRefreshToken() : userToken.getRefreshToken(),
                        userToken.getConsentCreatedAt(),
                        tokens.getExpiresIn()
                );

                log.info("Access token refreshed successfully for user: {}", userId);
                return tokens.getAccessToken();
            } else {
                throw new RuntimeException("Failed to refresh token: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Failed to refresh token - HTTP Error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Token refresh failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new RuntimeException("Token refresh failed", e);
        }
    }

    /**
     * Get valid access token (refresh if expired)
     */
    public String getValidAccessToken(String userId) {
        ai.efinsight.e_finsight.model.UserToken userToken = tokenRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No tokens found for user: " + userId));

        // Check if token is expired (with 5 minute buffer)
        if (userToken.getLastRefreshedAt() != null && userToken.getExpiresIn() != null) {
            LocalDateTime expiryTime = userToken.getLastRefreshedAt()
                    .plusSeconds(userToken.getExpiresIn() - 300); // 5 minute buffer

            if (LocalDateTime.now().isAfter(expiryTime)) {
                log.info("Access token expired for user: {}, refreshing...", userId);
                return refreshAccessToken(userId);
            }
        }

        return userToken.getAccessToken();
    }
}
