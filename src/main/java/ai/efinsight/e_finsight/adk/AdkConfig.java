package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.llm.LLMConfig;
import com.google.adk.agents.LlmAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdkConfig {
    private static final Logger log = LoggerFactory.getLogger(AdkConfig.class);

    private static final String DEFAULT_SPENDING_INSTRUCTION = "You are a financial spending analyst. Your role is to analyze transaction data and identify spending patterns, trends, and actionable recommendations. Use provided context and data to produce concise, data-driven insights.";

    @Bean(name = "spendingLlmAgent")
    public LlmAgent spendingLlmAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for spending with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("spending-analyst")
                .description("Spending Analyst Agent")
                .model(model)
                .instruction(DEFAULT_SPENDING_INSTRUCTION)
                .build();

        return agent;
    }

    @Bean(name = "synthesizerLlmAgent")
    public LlmAgent synthesizerLlmAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for synthesizer with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("synthesizer")
                .description("Synthesis Agent")
                .model(model)
                .instruction("You are an expert synthesizer that merges multiple agent outputs into a coherent, actionable financial plan.")
                .build();

        return agent;
    }
}
