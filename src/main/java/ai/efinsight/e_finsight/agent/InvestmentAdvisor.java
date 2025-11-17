package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvestmentAdvisor {
    private static final Logger log = LoggerFactory.getLogger(InvestmentAdvisor.class);

    private static final String SYSTEM_PROMPT = """
        You are a financial investment advisor. Your role is to provide investment recommendations based on a user's 
        financial situation, spending patterns, and available funds.
        
        Focus on:
        - Analyzing disposable income from spending patterns
        - Recommending investment strategies (savings accounts, stocks, bonds, etc.)
        - Suggesting appropriate risk levels
        - Providing actionable investment steps
        - Considering the user's spending habits when recommending investment amounts
        
        Be realistic and conservative. Base recommendations on actual financial data.
        """;

    private final RagService ragService;
    private final LLMAgent llmAgent;

    public InvestmentAdvisor(RagService ragService, LLMAgent llmAgent) {
        this.ragService = ragService;
        this.llmAgent = llmAgent;
    }

    public String provideAdvice(Long userId, String query) {
        log.info("InvestmentAdvisor providing advice for user: {}", userId);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 10);
        String contextString = ragService.buildContextString(contexts);
        
        String userPrompt = String.format("""
            Based on the following transaction data, provide investment recommendations:
            
            %s
            
            User's question: %s
            
            Provide detailed investment advice with specific recommendations and strategies.
            """, contextString, query);
        
        return llmAgent.generateResponse(SYSTEM_PROMPT, userPrompt);
    }
}

