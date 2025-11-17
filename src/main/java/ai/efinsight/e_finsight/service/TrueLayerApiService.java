package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.config.TrueLayerConfig;
import ai.efinsight.e_finsight.dto.TrueLayerAccountDto;
import ai.efinsight.e_finsight.dto.TrueLayerResponse;
import ai.efinsight.e_finsight.dto.TrueLayerTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class TrueLayerApiService {
    private static final Logger log = LoggerFactory.getLogger(TrueLayerApiService.class);

    private final TrueLayerConfig config;
    private final TrueLayerAuthService authService;
    private final RestTemplate restTemplate = new RestTemplate();

    public TrueLayerApiService(TrueLayerConfig config, TrueLayerAuthService authService) {
        this.config = config;
        this.authService = authService;
    }

    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Get all accounts for a user
     */
    public List<TrueLayerAccountDto> getAccounts(String userId) {
        try {
            String accessToken = authService.getValidAccessToken(userId);
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = config.getApiBaseUrl() + "/data/v1/accounts";
            ResponseEntity<TrueLayerResponse<TrueLayerAccountDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<TrueLayerResponse<TrueLayerAccountDto>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getResults() != null) {
                List<TrueLayerAccountDto> accounts = response.getBody().getResults();
                log.info("Successfully fetched {} accounts for user: {}", accounts.size(), userId);
                return accounts;
            } else {
                throw new RuntimeException("Failed to fetch accounts: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching accounts for user: {}", userId, e);
            throw new RuntimeException("Failed to fetch accounts: " + e.getMessage(), e);
        }
    }

    /**
     * Get transactions for a specific account
     */
    public List<TrueLayerTransactionDto> getAccountTransactions(String userId, String accountId, String from, String to) {
        try {
            String accessToken = authService.getValidAccessToken(userId);
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            StringBuilder urlBuilder = new StringBuilder(config.getApiBaseUrl() + "/data/v1/accounts/" + accountId + "/transactions");
            if (from != null || to != null) {
                urlBuilder.append("?");
                if (from != null) {
                    urlBuilder.append("from=").append(from);
                }
                if (to != null) {
                    if (from != null) {
                        urlBuilder.append("&");
                    }
                    urlBuilder.append("to=").append(to);
                }
            }

            ResponseEntity<TrueLayerResponse<TrueLayerTransactionDto>> response = restTemplate.exchange(
                    urlBuilder.toString(),
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<TrueLayerResponse<TrueLayerTransactionDto>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().getResults() != null) {
                List<TrueLayerTransactionDto> transactions = response.getBody().getResults();
                log.info("Successfully fetched {} transactions for account: {} (user: {})", 
                    transactions.size(), accountId, userId);
                return transactions;
            } else {
                throw new RuntimeException("Failed to fetch transactions: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching transactions for account: {} (user: {})", accountId, userId, e);
            throw new RuntimeException("Failed to fetch transactions: " + e.getMessage(), e);
        }
    }
}

