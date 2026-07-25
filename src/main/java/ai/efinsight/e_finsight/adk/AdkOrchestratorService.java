package ai.efinsight.e_finsight.adk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class AdkOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(AdkOrchestratorService.class);

    private final AdkRouterAgent router;
    private final AdkSpendingAgent spendingAgent;
    private final AdkBudgetAgent budgetAgent;
    private final AdkInvestmentAgent investmentAgent;
    private final AdkSynthesizer synthesizer;

    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public AdkOrchestratorService(
            AdkRouterAgent router,
            AdkSpendingAgent spendingAgent,
            AdkBudgetAgent budgetAgent,
            AdkInvestmentAgent investmentAgent,
            AdkSynthesizer synthesizer) {
        this.router = router;
        this.spendingAgent = spendingAgent;
        this.budgetAgent = budgetAgent;
        this.investmentAgent = investmentAgent;
        this.synthesizer = synthesizer;
    }

    public PlanResult generatePlan(Long userId, String query) {
        log.info("ADK Orchestrator generating plan for user: {}", userId);

        List<String> agentsToRun = router.decideAgents(userId, query);
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

        for (String name : agentsToRun) {
            switch (name.toLowerCase()) {
                case "spending":
                    futures.add(spendingAgent.run(userId, query));
                    break;
                case "budget":
                    futures.add(budgetAgent.run(userId, query));
                    break;
                case "investment":
                    futures.add(investmentAgent.run(userId, query));
                    break;
                default:
                    log.warn("Unknown agent requested: {}", name);
            }
        }

        // Wait for all to complete (with timeout)
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
            for (CompletableFuture<Map<String, Object>> f : futures) {
                try {
                    Map<String, Object> r = f.getNow(null);
                    if (r == null) r = f.get();
                    results.add(r);
                } catch (Exception e) {
                    log.warn("Agent future failed", e);
                }
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error waiting for agents", e);
        }

        // Synthesize
        String planText = synthesizer.synthesize(query, results);

        // Build citations by collecting any 'citations' keys from agents
        List<String> citations = results.stream()
                .flatMap(m -> m.entrySet().stream())
                .filter(e -> e.getKey().toLowerCase().contains("citation"))
                .map(e -> e.getValue().toString())
                .collect(Collectors.toList());

        // Combine agentResponses map
        Map<String, Object> agentResponses = results.stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));

        return new PlanResult(planText, citations, agentResponses);
    }

    public static class PlanResult {
        public final String plan;
        public final List<String> citations;
        public final Map<String, Object> agentResponses;

        public PlanResult(String plan, List<String> citations, Map<String, Object> agentResponses) {
            this.plan = plan;
            this.citations = citations;
            this.agentResponses = agentResponses;
        }
    }
}
