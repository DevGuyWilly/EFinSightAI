package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.llm.LLMConfig;
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
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final LLMConfig config;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingService(LLMConfig config) {
        this.config = config;
    }

    public float[] generateEmbedding(String text) {
        List<String> texts = new ArrayList<>();
        texts.add(text);
        List<float[]> embeddings = generateEmbeddings(texts);
        return embeddings.isEmpty() ? null : embeddings.get(0);
    }

    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            if ("openai".equalsIgnoreCase(config.getProvider())) {
                return generateOpenAIEmbeddings(texts);
            } else if ("gemini".equalsIgnoreCase(config.getProvider())) {
                return generateGeminiEmbeddings(texts);
            } else {
                throw new RuntimeException("Unsupported LLM provider: " + config.getProvider());
            }
        } catch (Exception e) {
            log.error("Error generating embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
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
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

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

    private List<float[]> generateGeminiEmbeddings(List<String> texts) {
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
        
        String url = apiUrl + "/models/" + embeddingModel + ":embedContent";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<float[]> embeddings = new ArrayList<>();
        
        for (String text : texts) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", "models/" + embeddingModel);
                Map<String, Object> content = new HashMap<>();
                List<Map<String, String>> parts = new ArrayList<>();
                Map<String, String> part = new HashMap<>();
                part.put("text", text);
                parts.add(part);
                content.put("parts", parts);
                body.put("content", content);

                String fullUrl = url + "?key=" + config.getApiKey();
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    JsonNode embedding = jsonNode.get("embedding").get("values");
                    
                    float[] embeddingArray = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        embeddingArray[i] = (float) embedding.get(i).asDouble();
                    }
                    embeddings.add(embeddingArray);
                } else {
                    log.warn("Gemini API returned: {} for text: {}", response.getStatusCode(), text.substring(0, Math.min(50, text.length())));
                    embeddings.add(new float[768]);
                }
            } catch (Exception e) {
                log.warn("Error generating embedding for text, using fallback", e);
                embeddings.add(new float[768]);
            }
        }

        log.info("Generated {} embeddings using Gemini", embeddings.size());
        return embeddings;
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

    public float[] stringToEmbedding(String embeddingStr) {
        if (embeddingStr == null || embeddingStr.isEmpty()) {
            return null;
        }
        try {
            String cleaned = embeddingStr.trim().replaceAll("^\\[|\\]$", "");
            String[] parts = cleaned.split(",");
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            return embedding;
        } catch (Exception e) {
            log.error("Error parsing embedding string", e);
            return null;
        }
    }
}

