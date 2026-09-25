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

    // Session state key holding the RAG-retrieved transaction context. AgentTool seeds each specialist's session
    // with the coordinator's state, so every agent resolves the {transaction_context} placeholder below directly
    // instead of relying on the coordinator to copy the data into its tool-call request.
    public static final String TRANSACTION_CONTEXT_STATE_KEY = "transaction_context";
    // Earlier turns of the conversation (oldest first), for interpreting follow-up questions
    public static final String CONVERSATION_HISTORY_STATE_KEY = "conversation_history";
    // Today's date (YYYY-MM-DD), so relative dates like "last month" can be turned into tool date ranges
    public static final String CURRENT_DATE_STATE_KEY = "current_date";

    private static final String CURRENT_DATE_SECTION =
            "\n\nToday's date is {" + CURRENT_DATE_STATE_KEY + "}.";

    private static final String CONVERSATION_SECTION =
            "\n\nThe conversation so far (oldest first) is below. Use it to understand follow-up questions such as "
                    + "'what about last month?' or 'why is that?'. Specialists do NOT see the conversation, so every "
                    + "request you send a specialist must be self-contained: restate exactly what the user is asking "
                    + "about (merchants, categories, date ranges) instead of words like 'that' or 'it'.\n\n{"
                    + CONVERSATION_HISTORY_STATE_KEY + "}";

    private static final String TRANSACTION_CONTEXT_SECTION =
            "\n\nThe user's bank transactions most relevant to this question are below. They are a relevance-ranked "
                    + "sample, NOT the complete history. Use them to cite specific merchants, amounts and dates, and "
                    + "never invent transactions.\n\n{" + TRANSACTION_CONTEXT_STATE_KEY + "}";

    // Specialists get TransactionAnalyticsTools, which aggregate over the full history rather than the RAG sample
    private static final String ANALYTICS_TOOLS_SECTION =
            "\n\nYou also have tools that compute exact figures over ALL of the user's transactions: "
                    + "get_spending_summary, get_spending_by_category, get_top_merchants and get_monthly_cashflow "
                    + "(dates are YYYY-MM-DD and optional). For any total, ranking, average, comparison or trend, call "
                    + "the relevant tool(s) and use their figures -- never add up or rank the sample transactions "
                    + "yourself, because the sample is incomplete. If the tools and the sample are not enough to answer "
                    + "fully, say so."
                    + CURRENT_DATE_SECTION;

    private static final String SPENDING_INSTRUCTION =
            "You are a financial spending analyst. " +
                    "Your role is to analyze transaction data and identify spending patterns, trends, and actionable recommendations. " +
                    "Use provided context and data to produce concise, data-driven insights." +
                    ANALYTICS_TOOLS_SECTION +
                    TRANSACTION_CONTEXT_SECTION;

    private static final String BUDGET_INSTRUCTION =
            "You are a financial budget planning expert. " +
                    "Your role is to create realistic, actionable budget recommendations based on spending history. " +
                    "Focus on category-based budgets, realistic spending limits, cost reduction opportunities, and savings goals." +
                    ANALYTICS_TOOLS_SECTION +
                    TRANSACTION_CONTEXT_SECTION;

    private static final String INVESTMENT_INSTRUCTION =
            "You are a financial investment advisor. " +
                    "Your role is to provide investment recommendations based on financial situation and spending patterns. " +
                    "Analyze disposable income, recommend investment strategies, suggest appropriate risk levels, and provide actionable investment steps. " +
                    "Be realistic and conservative." +
                    ANALYTICS_TOOLS_SECTION +
                    TRANSACTION_CONTEXT_SECTION;

    private static final String COORDINATOR_INSTRUCTION =
            "You are a financial coordinator agent. "
            + "You have three specialist tools available: "
            + "'spending-analyst' (analyzes spending patterns, transaction trends, and anomalies), "
            + "'budget-planner' (creates category budgets, spending limits, and savings goals), and "
            + "'investment-advisor' (gives investment recommendations and risk guidance). "
            + "Read the user's question and the account context provided, then call only the specialist tool(s) "
            + "whose domain the question actually touches -- do not call a tool for a domain the question does not "
            + "concern. Call more than one tool if the question spans multiple domains. Never call the same tool "
            + "more than once. Every specialist already receives the same transaction data you see, so the request "
            + "you send a specialist should state the user's question and what to focus on -- do not paste "
            + "transaction data into it. The specialists can also compute exact totals, rankings and trends over the "
            + "user's full transaction history, so send questions that need those to the relevant specialist rather "
            + "than answering from the sample below yourself. After the specialist(s) respond, write a single, "
            + "concise, coherent answer for the user that combines their findings."
            + CURRENT_DATE_SECTION
            + CONVERSATION_SECTION
            + TRANSACTION_CONTEXT_SECTION;

    private static final String QUESTION_REWRITER_INSTRUCTION =
            "You rewrite a user's latest question from a conversation with a personal finance assistant into a "
            + "standalone question that makes sense without the conversation. Resolve references such as 'that', "
            + "'it', 'them' or 'the same' using the conversation, and turn relative dates into explicit ones using "
            + "today's date. Keep the user's wording and intent; do not answer the question or add anything new. If "
            + "the question is already standalone, return it unchanged. Reply with the rewritten question only."
            + CURRENT_DATE_SECTION
            + "\n\nConversation so far (oldest first):\n\n{" + CONVERSATION_HISTORY_STATE_KEY + "}";

    @Bean(name = "spendingLlmAgent")
    public LlmAgent spendingLlmAgent(LLMConfig llmConfig, TransactionAnalyticsTools analyticsTools) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for spending with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("spending-analyst")
                .description("Spending Analyst Agent")
                .model(model)
                .instruction(SPENDING_INSTRUCTION)
                .tools(analyticsTools.asTools())
                .build();

        return agent;
    }

    @Bean(name = "budgetLlmAgent")
    public LlmAgent budgetLlmAgent(LLMConfig llmConfig, TransactionAnalyticsTools analyticsTools) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for budgeting with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("budget-planner")
                .description("Budget Planner Agent")
                .model(model)
                .instruction(BUDGET_INSTRUCTION)
                .tools(analyticsTools.asTools())
                .build();

        return agent;
    }

    @Bean(name = "investmentLlmAgent")
    public LlmAgent investmentLlmAgent(LLMConfig llmConfig, TransactionAnalyticsTools analyticsTools) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK LlmAgent for investment with model: {}", model);

        LlmAgent agent = LlmAgent.builder()
                .name("investment-advisor")
                .description("Investment Advisor Agent")
                .model(model)
               .instruction(INVESTMENT_INSTRUCTION)
               .tools(analyticsTools.asTools())
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

    // Used only for retrieval: turns a follow-up into a standalone question before it is embedded for RAG search
    @Bean(name = "questionRewriterAgent")
    public LlmAgent questionRewriterAgent(LLMConfig llmConfig) {
        String model = llmConfig.getChatModel() != null ? llmConfig.getChatModel() : "gemini-2.5-flash";
        log.info("Configuring ADK question rewriter LlmAgent with model: {}", model);

        return LlmAgent.builder()
                .name("question-rewriter")
                .description("Rewrites a follow-up question into a standalone question")
                .model(model)
                .instruction(QUESTION_REWRITER_INSTRUCTION)
                .build();
    }
}
