package ai.efinsight.e_finsight.agent;

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

    private final SpendingAnalyst spendingAnalyst;
    private final BudgetPlanner budgetPlanner;
    private final InvestmentAdvisor investmentAdvisor;
    private final RagService ragService;
    private final TransactionRepository transactionRepository;

    // Pattern to parse transaction text: "Transaction: MERCHANT | Amount: -5.00 GBP | Category: PURCHASE | Date: 2025-11-14T00:00:00Z"
    private static final Pattern TRANSACTION_PATTERN = Pattern.compile(
        "Transaction: ([^|]+) \\| Amount: ([^|]+) \\| Category: ([^|]+) \\| Date: (.+)"
    );

    public AgentCoordinatorService(
            SpendingAnalyst spendingAnalyst,
            BudgetPlanner budgetPlanner,
            InvestmentAdvisor investmentAdvisor,
            RagService ragService,
            TransactionRepository transactionRepository) {
        this.spendingAnalyst = spendingAnalyst;
        this.budgetPlanner = budgetPlanner;
        this.investmentAdvisor = investmentAdvisor;
        this.ragService = ragService;
        this.transactionRepository = transactionRepository;
    }

    // Generate a comprehensive plan for the user
    public PlanResponse generatePlan(Long userId, String query) {
        log.info("Generating comprehensive plan for user: {} with query: {}", userId, query);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 15);
        List<String> activeAgents = determineActiveAgents(query);
        
        Map<String, String> agentResponses = new HashMap<>();
        List<CitationDto> citations = buildStructuredCitations(contexts);
        
        if (activeAgents.contains("spending")) {
            try {
                agentResponses.put("spending_analysis", spendingAnalyst.analyzeSpending(userId, query));
            } catch (Exception e) {
                log.error("Error in SpendingAnalyst", e);
                agentResponses.put("spending_analysis", "Unable to analyze spending at this time. Error: " + e.getMessage());
            }
        }
        
        if (activeAgents.contains("budget")) {
            try {
                agentResponses.put("budget_plan", budgetPlanner.createBudget(userId, query));
            } catch (Exception e) {
                log.error("Error in BudgetPlanner", e);
                agentResponses.put("budget_plan", "Unable to create budget plan at this time. Error: " + e.getMessage());
            }
        }
        
        if (activeAgents.contains("investment")) {
            try {
                agentResponses.put("investment_advice", investmentAdvisor.provideAdvice(userId, query));
            } catch (Exception e) {
                log.error("Error in InvestmentAdvisor", e);
                agentResponses.put("investment_advice", "Unable to provide investment advice at this time. Error: " + e.getMessage());
            }
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

    // Determine the active agents based on the query
    private List<String> determineActiveAgents(String query) {
        String lowerQuery = query.toLowerCase();
        List<String> agents = new ArrayList<>();
        
        if (lowerQuery.contains("spending") || lowerQuery.contains("expense") || 
            lowerQuery.contains("where") || lowerQuery.contains("how much")) {
            agents.add("spending");
        }
        
        if (lowerQuery.contains("budget") || lowerQuery.contains("plan") || 
            lowerQuery.contains("allocate") || lowerQuery.contains("limit")) {
            agents.add("budget");
        }
        
        if (lowerQuery.contains("invest") || lowerQuery.contains("save") || 
            lowerQuery.contains("grow") || lowerQuery.contains("return")) {
            agents.add("investment");
        }
        
        if (agents.isEmpty()) {
            agents.add("spending");
            agents.add("budget");
            agents.add("investment");
        }
        
        return agents;
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

    // Generate structured plan response DTO
    public PlanResponseDto generateStructuredPlan(Long userId, String query) {
        log.info("Generating structured plan for user: {} with query: {}", userId, query);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 15);
        List<String> activeAgents = determineActiveAgents(query);
        
        Map<String, String> agentResponses = new HashMap<>();
        List<CitationDto> citations = buildStructuredCitations(contexts);
        
        // Execute agents
        String spendingAnalysis = null;
        String budgetPlan = null;
        String investmentAdvice = null;
        
        if (activeAgents.contains("spending")) {
            try {
                spendingAnalysis = spendingAnalyst.analyzeSpending(userId, query);
                agentResponses.put("spending_analysis", spendingAnalysis);
            } catch (Exception e) {
                log.error("Error in SpendingAnalyst", e);
                spendingAnalysis = "Unable to analyze spending at this time.";
            }
        }
        
        if (activeAgents.contains("budget")) {
            try {
                budgetPlan = budgetPlanner.createBudget(userId, query);
                agentResponses.put("budget_plan", budgetPlan);
            } catch (Exception e) {
                log.error("Error in BudgetPlanner", e);
                budgetPlan = "Unable to create budget plan at this time.";
            }
        }
        
        if (activeAgents.contains("investment")) {
            try {
                investmentAdvice = investmentAdvisor.provideAdvice(userId, query);
                agentResponses.put("investment_advice", investmentAdvice);
            } catch (Exception e) {
                log.error("Error in InvestmentAdvisor", e);
                investmentAdvice = "Unable to provide investment advice at this time.";
            }
        }
        
        // Extract summary from spending analysis (first paragraph)
        String summary = extractSummary(spendingAnalysis, budgetPlan, investmentAdvice);
        
        // Organize sections
        PlanResponseDto.PlanSections sections = new PlanResponseDto.PlanSections(
            spendingAnalysis,
            budgetPlan,
            investmentAdvice
        );
        
        return new PlanResponseDto(true, query, summary, sections, citations, agentResponses);
    }

    // Extract a concise summary from agent responses
    private String extractSummary(String spendingAnalysis, String budgetPlan, String investmentAdvice) {
        if (spendingAnalysis != null && !spendingAnalysis.isEmpty()) {
            // Try to extract first paragraph or executive summary
            String[] lines = spendingAnalysis.split("\n");
            for (String line : lines) {
                if (line.contains("Executive Summary") || line.contains("Summary")) {
                    // Find the next non-empty line
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].equals(line) && i + 1 < lines.length) {
                            String summary = lines[i + 1].trim();
                            if (!summary.isEmpty() && summary.length() < 500) {
                                return summary;
                            }
                        }
                    }
                }
            }
            // Fallback: first 200 characters
            return spendingAnalysis.length() > 200 
                ? spendingAnalysis.substring(0, 200) + "..." 
                : spendingAnalysis;
        }
        return "Financial analysis based on your transaction history.";
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

