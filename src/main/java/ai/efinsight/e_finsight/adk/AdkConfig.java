package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.llm.LLMConfig;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdkConfig {
    private static final Logger log = LoggerFactory.getLogger(AdkConfig.class);

    private static final String SPENDING_INSTRUCTION =
            "You are a financial spending analyst. " +
                    "Your role is to analyze transaction data and identify spending patterns, trends, and actionable recommendations. " +
                    "Use provided context and data to produce concise, data-driven insights.";
    
    private static final String BUDGET_INSTRUCTION =
            "You are a financial budget planning expert. " +
                    "Your role is to create realistic, actionable budget recommendations based on spending history. " +
                    "Focus on category-based budgets, realistic spending limits, cost reduction opportunities, and savings goals.";
    
    private static final String INVESTMENT_INSTRUCTION =
            "You are a financial investment advisor. " +
                    "Your role is to provide investment recommendations based on financial situation and spending patterns. " +
                    "Analyze disposable income, recommend investment strategies, suggest appropriate risk levels, and provide actionable investment steps. " +
                    "Be realistic and conservative.";

    private static final String COORDINATOR_INSTRUCTION =
            "You are a financial coordinator agent. "
            + "You have three specialist tools available: "
            + "'spending-analyst' (analyzes spending patterns, transaction trends, and anomalies), "
            + "'budget-planner' (creates category budgets, spending limits, and savings goals), and "
            + "'investment-advisor' (gives investment recommendations and risk guidance). "
            + "Read the user's question and the account context provided, then call only the specialist tool(s) "
            + "whose domain the question actually touches -- do not call a tool for a domain the question does not "
            + "concern. Call more than one tool if the question spans multiple domains. Never call the same tool "
            + "more than once. After the specialist(s) respond, write a single, concise, coherent answer for the "
            + "user that combines their findings.";

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

    @Bean(name = "rootCoordinatorAgent")
    public LlmAgent rootCoordinatorAgent(
            LLMConfig llmConfig,
            @Qualifier("spendingLlmAgent") LlmAgent spendingLlmAgent,
            @Qualifier("budgetLlmAgent") LlmAgent budgetLlmAgent,
            @Qualifier("investmentLlmAgent") LlmAgent investmentLlmAgent) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK root coordinator LlmAgent with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("financial-coordinator")
                .description("Routes financial questions to the relevant specialist agent(s) and combines their answers")
                .model(model)
                .instruction(COORDINATOR_INSTRUCTION)
                .tools(
                        AgentTool.create(spendingLlmAgent),
                        AgentTool.create(budgetLlmAgent),
                        AgentTool.create(investmentLlmAgent))
                .build();

        return agent;
    }
}

