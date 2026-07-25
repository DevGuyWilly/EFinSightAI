package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.BudgetPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AdkBudgetAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(AdkBudgetAgent.class);

    private final BudgetPlanner budgetPlanner;

    public AdkBudgetAgent(BudgetPlanner budgetPlanner) {
        this.budgetPlanner = budgetPlanner;
    }

    @Override
    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkBudgetAgent running for user: {}", userId);
            String result = budgetPlanner.createBudget(userId, query);
            Map<String, Object> out = new HashMap<>();
            out.put("budget_plan", result);
            return out;
        });
    }
}
