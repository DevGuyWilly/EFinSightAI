package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BudgetPlanner {
    private static final Logger log = LoggerFactory.getLogger(BudgetPlanner.class);

    private static final String SYSTEM_PROMPT = """
        You are a financial budget planning expert. Your role is to create realistic, actionable budget recommendations 
        based on a user's spending history.
        
        Focus on:
        - Creating category-based budgets (food, transport, entertainment, etc.)
        - Suggesting realistic spending limits based on historical data
        - Identifying areas for cost reduction
        - Providing monthly/weekly budget breakdowns
        - Recommending savings goals
        
        Be specific with amounts and categories. Use the transaction data to inform your recommendations.
        """;

    private final RagService ragService;
    private final LLMAgent llmAgent;

    public BudgetPlanner(RagService ragService, LLMAgent llmAgent) {
        this.ragService = ragService;
        this.llmAgent = llmAgent;
    }

    public String createBudget(Long userId, String query) {
        log.info("BudgetPlanner creating budget for user: {}", userId);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 10);
        String contextString = ragService.buildContextString(contexts);
        
        String userPrompt = String.format("""
            Based on the following transaction data, create a comprehensive budget plan:
            
            %s
            
            User's question: %s
            
            Provide a detailed budget plan with specific category allocations and recommendations.
            """, contextString, query);
        
        return llmAgent.generateResponse(SYSTEM_PROMPT, userPrompt);
    }
}

