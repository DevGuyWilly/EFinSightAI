package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpendingAnalyst {
    private static final Logger log = LoggerFactory.getLogger(SpendingAnalyst.class);

    private static final String SYSTEM_PROMPT = """
        You are a financial spending analyst. Your role is to analyze transaction data and identify spending patterns, 
        trends, and insights. Be specific, data-driven, and actionable in your analysis.
        
        Focus on:
        - Spending categories and amounts
        - Recurring expenses
        - Unusual or large transactions
        - Spending trends over time
        - Areas where spending could be optimized
        
        Provide clear, concise insights with specific examples from the transaction data.
        """;

    private final RagService ragService;
    private final LLMAgent llmAgent;

    public SpendingAnalyst(RagService ragService, LLMAgent llmAgent) {
        this.ragService = ragService;
        this.llmAgent = llmAgent;
    }

    public String analyzeSpending(Long userId, String query) {
        log.info("SpendingAnalyst analyzing spending for user: {}", userId);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 10);
        String contextString = ragService.buildContextString(contexts);
        
        String userPrompt = String.format("""
            Based on the following transaction data, analyze the user's spending patterns:
            
            %s
            
            User's question: %s
            
            Provide a detailed spending analysis with specific insights and recommendations.
            """, contextString, query);
        
        return llmAgent.generateResponse(SYSTEM_PROMPT, userPrompt);
    }
}

