package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.llm.LLMClient;
import org.springframework.stereotype.Component;

@Component
public class LLMAgent {
    private final LLMClient llmClient;

    public LLMAgent(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public String generateResponse(String systemPrompt, String userMessage) {
        return llmClient.chatCompletion(systemPrompt, userMessage);
    }
}

