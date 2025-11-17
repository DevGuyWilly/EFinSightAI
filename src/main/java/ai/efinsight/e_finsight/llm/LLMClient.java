package ai.efinsight.e_finsight.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LLMClient {
    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);

    private final LLMConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMClient(LLMConfig config) {
        this.config = config;
    }

    public String chatCompletion(String systemPrompt, String userMessage) {
        try {
            if ("openai".equalsIgnoreCase(config.getProvider())) {
                return openAIChatCompletion(systemPrompt, userMessage);
            } else if ("gemini".equalsIgnoreCase(config.getProvider())) {
                return geminiChatCompletion(systemPrompt, userMessage);
            } else {
                throw new RuntimeException("Unsupported LLM provider: " + config.getProvider());
            }
        } catch (Exception e) {
            log.error("Error generating chat completion", e);
            throw new RuntimeException("Failed to generate chat completion: " + e.getMessage(), e);
        }
    }

    private String openAIChatCompletion(String systemPrompt, String userMessage) {
        String url = (config.getOpenaiApiUrl() != null ? config.getOpenaiApiUrl() : "https://api.openai.com/v1") + "/chat/completions";
        String model = config.getChatModel() != null ? config.getChatModel() : "gpt-4o-mini";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
        }
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        
        body.put("messages", messages);
        body.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String content = jsonNode.get("choices").get(0).get("message").get("content").asText();
                log.debug("OpenAI chat completion successful");
                return content;
            } else {
                throw new RuntimeException("OpenAI API returned: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI chat API", e);
            throw new RuntimeException("Failed to generate OpenAI chat completion: " + e.getMessage(), e);
        }
    }

    private String geminiChatCompletion(String systemPrompt, String userMessage) {
        String baseUrl = config.getGeminiApiUrl() != null ? config.getGeminiApiUrl() : "https://generativelanguage.googleapis.com/v1beta";
        String model = config.getChatModel() != null ? config.getChatModel() : "gemini-2.5-flash";
        String apiKey = config.getApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured. Please set llm.api-key in application.properties");
        }
        
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        log.info("Calling Gemini API with model: {} at URL: {}", model, url.replace("?key=" + apiKey, "?key=***"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String fullPrompt = userMessage;
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            fullPrompt = systemPrompt + "\n\n" + userMessage;
        }
        
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> contentEntry = new HashMap<>();
        List<Map<String, String>> messageParts = new ArrayList<>();
        Map<String, String> textPart = new HashMap<>();
        textPart.put("text", fullPrompt);
        messageParts.add(textPart);
        contentEntry.put("parts", messageParts);
        contents.add(contentEntry);
        
        body.put("contents", contents);
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        body.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        int maxRetries = 3;
        long baseDelayMs = 1000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    if (jsonNode.has("error")) {
                        JsonNode errorNode = jsonNode.get("error");
                        String errorMsg = errorNode.get("message").asText();
                        int errorCode = errorNode.has("code") ? errorNode.get("code").asInt() : 0;
                        String status = errorNode.has("status") ? errorNode.get("status").asText() : "";
                        
                        if ((errorCode == 503 || "UNAVAILABLE".equals(status) || "RESOURCE_EXHAUSTED".equals(status)) 
                            && attempt < maxRetries) {
                            long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                            log.warn("Gemini API returned retryable error (attempt {}/{}): {}. Retrying in {}ms...", 
                                attempt, maxRetries, errorMsg, delayMs);
                            Thread.sleep(delayMs);
                            continue;
                        }
                        
                        throw new RuntimeException("Gemini API error: " + errorMsg);
                    }
                    
                    JsonNode candidates = jsonNode.get("candidates");
                    if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                        throw new RuntimeException("No candidates in Gemini response");
                    }
                    
                    String responseText = candidates.get(0).get("content").get("parts").get(0).get("text").asText();
                    log.debug("Gemini chat completion successful");
                    return responseText;
                } else {
                    if ((response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE 
                         || response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) 
                        && attempt < maxRetries) {
                        long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                        log.warn("Gemini API returned {} (attempt {}/{}). Retrying in {}ms...", 
                            response.getStatusCode(), attempt, maxRetries, delayMs);
                        Thread.sleep(delayMs);
                        continue;
                    }
                    
                    String errorBody = response.getBody() != null ? response.getBody() : "No response body";
                    log.error("Gemini API returned: {} - Body: {}", response.getStatusCode(), errorBody);
                    throw new RuntimeException("Gemini API returned: " + response.getStatusCode() + " - " + errorBody);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while retrying Gemini API call", e);
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                if (attempt < maxRetries && (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE 
                    || e.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)) {
                    long delayMs = baseDelayMs * (long) Math.pow(2, attempt - 1);
                    log.warn("Gemini API server error (attempt {}/{}): {}. Retrying in {}ms...", 
                        attempt, maxRetries, e.getMessage(), delayMs);
                    try {
                        Thread.sleep(delayMs);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while retrying", ie);
                    }
                }
                log.error("Error calling Gemini API: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate Gemini chat completion: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("Error calling Gemini API: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate Gemini chat completion: " + e.getMessage(), e);
            }
        }
        
        throw new RuntimeException("Failed to generate Gemini chat completion after " + maxRetries + " attempts");
    }
}

