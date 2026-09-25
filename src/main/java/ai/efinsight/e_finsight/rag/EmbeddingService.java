package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.llm.LLMConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    // Gemini's batchEmbedContents rejects batches larger than 100 requests
    static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final LLMConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    // Base delay for retry backoff; package-private so tests can shorten it
    long retryBaseDelayMs = 1000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingService(LLMConfig config) {
        this.config = config;
    }

    // Gemini embeds asymmetrically: stored content and search queries must use matching task types, or retrieval
    // quality drops. OpenAI embeddings have no task type, so this only affects the Gemini path.
    public enum TaskType {
        RETRIEVAL_DOCUMENT,
        RETRIEVAL_QUERY
    }

    // Embed a user's search query, for comparison against stored document embeddings
    public float[] generateQueryEmbedding(String query) {
        List<float[]> embeddings = generateEmbeddings(List.of(query), TaskType.RETRIEVAL_QUERY);
        // If the embeddings are empty, return null
        return embeddings.isEmpty() ? null : embeddings.get(0);
    }

    // Embed content to be stored and searched (e.g. transaction chunks) - using either OpenAI or Gemini
    public List<float[]> generateEmbeddings(List<String> texts) {
        return generateEmbeddings(texts, TaskType.RETRIEVAL_DOCUMENT);
    }

    // Embeds any number of texts in order, making one API call per MAX_BATCH_SIZE texts
    private List<float[]> generateEmbeddings(List<String> texts, TaskType taskType) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<float[]> embeddings = new ArrayList<>(texts.size());
            for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
                List<String> batch = texts.subList(start, Math.min(start + MAX_BATCH_SIZE, texts.size()));
                List<float[]> batchEmbeddings;
                if ("openai".equalsIgnoreCase(config.getProvider())) {
                    batchEmbeddings = generateOpenAIEmbeddings(batch);
                } else if ("gemini".equalsIgnoreCase(config.getProvider())) {
                    batchEmbeddings = generateGeminiEmbeddings(batch, taskType);
                } else {
                    throw new RuntimeException("Unsupported LLM provider: " + config.getProvider());
                }
                if (batchEmbeddings.size() != batch.size()) {
                    throw new RuntimeException("Expected " + batch.size() + " embeddings but got " + batchEmbeddings.size());
                }
                embeddings.addAll(batchEmbeddings);
            }
            return embeddings;
        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }

    // POSTs with retries on rate limiting (429) and transient server errors (500/503), backing off exponentially
    private ResponseEntity<String> postWithRetry(String url, HttpEntity<?> request) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 503;
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                long delayMs = retryBaseDelayMs * (1L << (attempt - 1));
                log.warn("Embedding API returned {} (attempt {}/{}), retrying in {}ms", status, attempt, MAX_ATTEMPTS, delayMs);
                Thread.sleep(delayMs);
            }
        }
    }

    private List<float[]> generateOpenAIEmbeddings(List<String> texts) {
        String url = (config.getOpenaiApiUrl() != null ? config.getOpenaiApiUrl() : "https://api.openai.com/v1") + "/embeddings";
        String model = config.getEmbeddingModel() != null ? config.getEmbeddingModel() : "text-embedding-3-small";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = postWithRetry(url, request);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode data = jsonNode.get("data");
                
                List<float[]> embeddings = new ArrayList<>();
                for (JsonNode item : data) {
                    JsonNode embedding = item.get("embedding");
                    float[] embeddingArray = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        embeddingArray[i] = (float) embedding.get(i).asDouble();
                    }
                    embeddings.add(embeddingArray);
                }
                
                log.info("Generated {} embeddings using OpenAI", embeddings.size());
                return embeddings;
            } else {
                throw new RuntimeException("OpenAI API returned: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI embeddings API", e);
            throw new RuntimeException("Failed to generate OpenAI embeddings: " + e.getMessage(), e);
        }
    }

    // One batchEmbedContents call for up to MAX_BATCH_SIZE texts; embeddings come back in request order
    private List<float[]> generateGeminiEmbeddings(List<String> texts, TaskType taskType) {
        String baseUrl = config.getGeminiApiUrl() != null 
            ? config.getGeminiApiUrl() 
            : "https://generativelanguage.googleapis.com/v1beta";
        
        String embeddingModel = config.getEmbeddingModel() != null 
            ? config.getEmbeddingModel() 
            : "text-embedding-004";
        
        String apiUrl = baseUrl;
        if (baseUrl.contains("/v1") && !baseUrl.contains("v1beta")) {
            apiUrl = baseUrl.replace("/v1", "/v1beta");
        }
        
        String url = apiUrl + "/models/" + embeddingModel + ":batchEmbedContents";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Header rather than ?key= so the key never ends up in logged URLs
        headers.set("x-goog-api-key", config.getApiKey());

        List<Map<String, Object>> requests = new ArrayList<>(texts.size());
        for (String text : texts) {
            requests.add(Map.of(
                    "model", "models/" + embeddingModel,
                    "content", Map.of("parts", List.of(Map.of("text", text))),
                    "taskType", taskType.name()));
        }
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("requests", requests), headers);

        try {
            ResponseEntity<String> response = postWithRetry(url, request);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("Gemini API returned: {}. Response body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Gemini API returned status: " + response.getStatusCode());
            }

            JsonNode embeddingsNode = objectMapper.readTree(response.getBody()).get("embeddings");
            if (embeddingsNode == null || !embeddingsNode.isArray()) {
                log.error("Gemini API response missing embeddings field. Response: {}", response.getBody());
                throw new RuntimeException("Invalid response structure from Gemini API");
            }

            List<float[]> embeddings = new ArrayList<>(embeddingsNode.size());
            for (JsonNode embedding : embeddingsNode) {
                JsonNode values = embedding.get("values");
                float[] embeddingArray = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    embeddingArray[i] = (float) values.get(i).asDouble();
                }
                embeddings.add(embeddingArray);
            }

            log.info("Generated {} embeddings using Gemini", embeddings.size());
            return embeddings;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying Gemini embedding call", e);
        } catch (Exception e) {
            log.error("Error generating {} Gemini embeddings: {}", texts.size(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }

    public String embeddingToString(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

