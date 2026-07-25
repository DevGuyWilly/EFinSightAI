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

    private static final String SPENDING_INSTRUCTION = "You are a financial spending analyst. Your role is to analyze transaction data and identify spending patterns, trends, and actionable recommendations. Use provided context and data to produce concise, data-driven insights.";
    
    private static final String BUDGET_INSTRUCTION = "You are a financial budget planning expert. Your role is to create realistic, actionable budget recommendations based on spending history. Focus on category-based budgets, realistic spending limits, cost reduction opportunities, and savings goals.";
    
    private static final String INVESTMENT_INSTRUCTION = "You are a financial investment advisor. Your role is to provide investment recommendations based on financial situation and spending patterns. Analyze disposable income, recommend investment strategies, suggest appropriate risk levels, and provide actionable investment steps. Be realistic and conservative.";

    @Bean(name = "spendingLlmAgent")
    public LlmAgent spendingLlmAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for spending with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("spending-analyst")
                .description("Spending Analyst Agent")
                .model(model)
                .instruction(SPENDING_INSTRUCTION)
                .build();

        return agent;
    }

    @Bean(name = "budgetLlmAgent")
    public LlmAgent budgetLlmAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for budgeting with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("budget-planner")
                .description("Budget Planner Agent")
                .model(model)
                .instruction(BUDGET_INSTRUCTION)
                .build();

        return agent;
    }

    @Bean(name = "investmentLlmAgent")
    public LlmAgent investmentLlmAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for investment with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("investment-advisor")
                .description("Investment Advisor Agent")
                .model(model)
               .instruction(INVESTMENT_INSTRUCTION)
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

