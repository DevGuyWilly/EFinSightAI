package ai.efinsight.e_finsight.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {
    private String provider;
    private String apiKey;
    private String geminiApiUrl;
    private String openaiApiUrl;
    private String embeddingModel;
    private String chatModel;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getGeminiApiUrl() {
        return geminiApiUrl;
    }

    public void setGeminiApiUrl(String geminiApiUrl) {
        this.geminiApiUrl = geminiApiUrl;
    }

    public String getOpenaiApiUrl() {
        return openaiApiUrl;
    }

    public void setOpenaiApiUrl(String openaiApiUrl) {
        this.openaiApiUrl = openaiApiUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }
}

