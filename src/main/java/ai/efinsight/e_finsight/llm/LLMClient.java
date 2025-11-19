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
        // URL is the URL to the LLM
        String url = (config.getOpenaiApiUrl() != null ? config.getOpenaiApiUrl() : "https://api.openai.com/v1") + "/chat/completions";

        // Model is the model to use for the LLM
        String model = config.getChatModel() != null ? config.getChatModel() : "gpt-4o-mini";

        // Headers is the headers to the LLM
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        // Body is the request body to the LLM
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        
        // Messages is the list of messages to the LLM
        List<Map<String, String>> messages = new ArrayList<>();

        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {

            // System message is the system prompt
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
        }
        
        // User message is the user's input
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        
        // Body is the request body to the LLM
        body.put("messages", messages);

        // Temperature just means the randomness of the response
        body.put("temperature", 0.7);

        // Request is the request to the LLM
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                // Parse the response body to a JSON node
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                // Get the content from the JSON node
                String content = jsonNode.get("choices").get(0).get("message").get("content").asText();
                // Logs for debugging
                log.debug("OpenAI chat completion successful");
                // Return the content
                return content;
            } else {
                // If the response is not OK, throw an error
                throw new RuntimeException("OpenAI API returned: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI chat API", e);
            throw new RuntimeException("Failed to generate OpenAI chat completion: " + e.getMessage(), e);
        }
    }

    private String geminiChatCompletion(String systemPrompt, String userMessage) {
        
        // Base URL is the base URL to the Gemini API
        String baseUrl = config.getGeminiApiUrl() != null ? config.getGeminiApiUrl() : "https://generativelanguage.googleapis.com/v1beta";

        // Model is the model to use for the Gemini API
        String model = config.getChatModel() != null ? config.getChatModel() : "gemini-2.5-flash";
        
        // API Key is the API key to the Gemini API
        String apiKey = config.getApiKey();

        // If the API key is not configured, throw an error
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured. Please set llm.api-key in application.properties");
        }
        
        // URL is the URL to the Gemini API
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
        log.info("Calling Gemini API with model: {} at URL: {}", model, url.replace("?key=" + apiKey, "?key=***"));

        // Headers is the headers to the Gemini API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Full prompt (System prompt + User message) is the full prompt to the Gemini API
        String fullPrompt = userMessage;

        // If the system prompt is not configured, throw an error
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            fullPrompt = systemPrompt + "\n\n" + userMessage;
        }   
        
        //construct the request body to the Gemini API
        Map<String, Object> body = new HashMap<>();

        // Contents is the list of contents to the Gemini API
        List<Map<String, Object>> contents = new ArrayList<>();

        // Content entry is the content entry to the Gemini API, it contains the message parts
        Map<String, Object> contentEntry = new HashMap<>();

        // Message parts is the list of message parts to the Gemini API
        List<Map<String, String>> messageParts = new ArrayList<>();
        // Text part is the text part to the Gemini API
        Map<String, String> textPart = new HashMap<>();
        // Put the full prompt into the text part
        textPart.put("text", fullPrompt);
        // Add the text part to the message parts
        messageParts.add(textPart);
        // Add the message parts to the content entry
        contentEntry.put("parts", messageParts);
        // Add the content entry to the contents
        contents.add(contentEntry);


        // Add the contents to the body
        body.put("contents", contents);


        // Generation config is the generation config to the Gemini API
        Map<String, Object> generationConfig = new HashMap<>();
        
        // Temperature is the temperature to the generation config
        generationConfig.put("temperature", 0.7);
        body.put("generationConfig", generationConfig);


        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Max retries is the maximum number of retries to the Gemini API
        int maxRetries = 3;
        // Base delay is the base delay to the Gemini API
        long baseDelayMs = 1000;
        // Attempt is the attempt number to the Gemini API
        
        // Try to call the Gemini API
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Try to call the Gemini API
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

