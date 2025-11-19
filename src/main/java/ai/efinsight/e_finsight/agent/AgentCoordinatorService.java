package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(AgentCoordinatorService.class);

    private final SpendingAnalyst spendingAnalyst;
    private final BudgetPlanner budgetPlanner;
    private final InvestmentAdvisor investmentAdvisor;
    private final RagService ragService;

    public AgentCoordinatorService(
            SpendingAnalyst spendingAnalyst,
            BudgetPlanner budgetPlanner,
            InvestmentAdvisor investmentAdvisor,
            RagService ragService) {
        this.spendingAnalyst = spendingAnalyst;
        this.budgetPlanner = budgetPlanner;
        this.investmentAdvisor = investmentAdvisor;
        this.ragService = ragService;
    }

    // Generate a comprehensive plan for the user
    public PlanResponse generatePlan(Long userId, String query) {
        log.info("Generating comprehensive plan for user: {} with query: {}", userId, query);
        
        List<RagService.RagContext> contexts = ragService.retrieveContext(userId, query, 15);
        List<String> activeAgents = determineActiveAgents(query);
        
        Map<String, String> agentResponses = new HashMap<>();
        List<String> citations = new ArrayList<>();
        
        for (RagService.RagContext ctx : contexts) {
            citations.add(String.format("Transaction ID: %d - %s", ctx.sourceId, ctx.text));
        }
        
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
        return new PlanResponse(plan, citations, agentResponses);
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
        //
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

