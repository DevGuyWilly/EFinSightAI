package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.adk.AdkSpendingAgentNative;
import ai.efinsight.e_finsight.adk.AdkBudgetAgentNative;
import ai.efinsight.e_finsight.adk.AdkInvestmentAgentNative;
import ai.efinsight.e_finsight.dto.CitationDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(AgentCoordinatorService.class);

    private final AdkSpendingAgentNative adkSpendingAgent;
    private final AdkBudgetAgentNative adkBudgetAgent;
    private final AdkInvestmentAgentNative adkInvestmentAgent;
    private final RagService ragService;
    private final TransactionRepository transactionRepository;

    // Pattern to parse transaction text: "Transaction: MERCHANT | Amount: -5.00 GBP | Category: PURCHASE | Date: 2025-11-14T00:00:00Z"
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "Transaction: ([^|]+) \\| Amount: ([^|]+) \\| Category: ([^|]+) \\| Date: (.+)"
    );

    public AgentCoordinatorService(
            AdkSpendingAgentNative adkSpendingAgent,
            AdkBudgetAgentNative adkBudgetAgent,
            AdkInvestmentAgentNative adkInvestmentAgent,
            RagService ragService,
            TransactionRepository transactionRepository) {
        this.adkSpendingAgent = adkSpendingAgent;
        this.adkBudgetAgent = adkBudgetAgent;
        this.adkInvestmentAgent = adkInvestmentAgent;
        this.ragService = ragService;
        this.transactionRepository = transactionRepository;
    }

    // Generate a comprehensive plan for the user
    public PlanResponse generatePlan(Long userId, String query) {
        log.info("Generating comprehensive plan for user: {} with query: {}", userId, query);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 15);
        Map<String, String> agentResponses = new HashMap<>();
        List<CitationDto> citations = buildStructuredCitations(contexts);
        
        try {
            log.info("Running ADK SpendingAgent");
            Map<String, Object> adkResult = adkSpendingAgent.run(userId, query).get();
            agentResponses.put("spending_analysis", 
                (String) adkResult.getOrDefault("spending_analysis_json", 
                    adkResult.getOrDefault("spending_analysis", "No analysis available")));
        } catch (Exception e) {
            log.error("Error in ADK SpendingAgent", e);
            agentResponses.put("spending_analysis", "Unable to analyze spending at this time. Error: " + e.getMessage());
        }
        
        try {
            log.info("Running ADK BudgetAgent");
            Map<String, Object> adkResult = adkBudgetAgent.run(userId, query).get();
            agentResponses.put("budget_plan", 
                (String) adkResult.getOrDefault("budget_plan_json", 
                    adkResult.getOrDefault("budget_plan", "No plan available")));
        } catch (Exception e) {
            log.error("Error in ADK BudgetAgent", e);
            agentResponses.put("budget_plan", "Unable to create budget plan at this time. Error: " + e.getMessage());
        }
        
        try {
            log.info("Running ADK InvestmentAgent");
            Map<String, Object> adkResult = adkInvestmentAgent.run(userId, query).get();
            agentResponses.put("investment_advice", 
                (String) adkResult.getOrDefault("investment_advice_json", 
                    adkResult.getOrDefault("investment_advice", "No advice available")));
        } catch (Exception e) {
            log.error("Error in ADK InvestmentAgent", e);
            agentResponses.put("investment_advice", "Unable to provide investment advice at this time. Error: " + e.getMessage());
        }
        
        String plan = combineAgentResponses(agentResponses, query);
        List<String> citationStrings = new ArrayList<>();
        for (CitationDto citation : citations) {
            citationStrings.add(String.format("Transaction ID: %d - %s", 
                citation.getTransactionId(), citation.getDescription()));
        }
        return new PlanResponse(plan, citationStrings, agentResponses);
    }

    // Build structured citations from RAG contexts
    private List<CitationDto> buildStructuredCitations(List<RagService.RagContext> contexts) {
        List<CitationDto> citations = new ArrayList<>();
        
        for (RagService.RagContext ctx : contexts) {
            CitationDto citation = new CitationDto();
            citation.setTransactionId(ctx.sourceId);
            
            // Try to fetch actual transaction for accurate data
            Transaction transaction = transactionRepository.findById(ctx.sourceId).orElse(null);
            
            if (transaction != null) {
                // Use actual transaction data
                citation.setMerchant(transaction.getMerchantName());
                citation.setAmount(transaction.getAmount() != null ? transaction.getAmount().toString() : null);
                citation.setCurrency(transaction.getCurrency());
                citation.setCategory(transaction.getTransactionCategory());
                citation.setDate(transaction.getTimestamp() != null ? 
                    transaction.getTimestamp().toString() : null);
                citation.setDescription(transaction.getDescription());
            } else {
                // Fallback: Parse from chunk text
                parseCitationFromText(ctx.text, citation);
            }
            
            citations.add(citation);
        }
        
        return citations;
    }

    // Parse transaction details from chunk text
    private void parseCitationFromText(String text, CitationDto citation) {
        Matcher matcher = TRANSACTION_PATTERN.matcher(text);
        if (matcher.find()) {
            citation.setMerchant(matcher.group(1).trim());
            String amountStr = matcher.group(2).trim();
            // Extract amount and currency
            String[] amountParts = amountStr.split("\\s+");
            if (amountParts.length >= 2) {
                citation.setAmount(amountParts[0]);
                citation.setCurrency(amountParts[1]);
            } else {
                citation.setAmount(amountStr);
            }
            citation.setCategory(matcher.group(3).trim());
            citation.setDate(matcher.group(4).trim());
            citation.setDescription(matcher.group(1).trim());
        } else {
            // Fallback: use full text as description
            citation.setDescription(text);
        }
    }

    // Combine the responses from the agents
    private String combineAgentResponses(Map<String, String> responses, String query) {
        StringBuilder plan = new StringBuilder();
        plan.append("# Financial Plan\n\n");
        plan.append("Based on your question: \"").append(query).append("\"\n\n");
        
        // If the spending analysis is present, add it to the plan
        if (responses.containsKey("spending_analysis")) {
            plan.append("## Spending Analysis\n\n");
            plan.append(responses.get("spending_analysis")).append("\n\n");
        }
        
        // If the budget plan is present, add it to the plan
        if (responses.containsKey("budget_plan")) {
            plan.append("## Budget Recommendations\n\n");
            plan.append(responses.get("budget_plan")).append("\n\n");
        }
        
        // If the investment advice is present, add it to the plan
        if (responses.containsKey("investment_advice")) {
            plan.append("## Investment Advice\n\n");
            plan.append(responses.get("investment_advice")).append("\n\n");
        }
        
        return plan.toString();
    }

    // Plan response is the response from the agent coordinator service
    public static class PlanResponse {
        private final String plan;
        private final List<String> citations;
        private final Map<String, String> agentResponses;

        public PlanResponse(String plan, List<String> citations, Map<String, String> agentResponses) {
            this.plan = plan;
            this.citations = citations;
            this.agentResponses = agentResponses;
        }

        public String getPlan() {
            return plan;
        }

        public List<String> getCitations() {
            return citations;
        }

        public Map<String, String> getAgentResponses() {
            return agentResponses;
        }
    }
}

