package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.BudgetPlanner;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runtime.AgentExecutionResult;
import com.google.adk.runtime.AgentRunner;
import com.google.adk.runtime.ExecutionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AdkBudgetAgentNative {
    private static final Logger log = LoggerFactory.getLogger(AdkBudgetAgentNative.class);

    private final LlmAgent spendingLlmAgent;
    private final BudgetPlanner budgetPlanner; // fallback

    public AdkBudgetAgentNative(LlmAgent spendingLlmAgent, BudgetPlanner budgetPlanner) {
        this.spendingLlmAgent = spendingLlmAgent;
        this.budgetPlanner = budgetPlanner;
    }

    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkBudgetAgentNative running ADK LlmAgent for user: {}", userId);

            String prompt = "You are a financial budget planning expert. Given the user's question and the retrieved transaction context, produce a JSON object with keys: budgets (map of category->monthly_limit), recommendations (list), savings_goals (list). Only output valid JSON." +
                    "\n\nUser question: " + query + "\n\nProvide output as JSON only.";

            try {
                AgentRunner runner = AgentRunner.create();
                AgentExecutionResult result = runner.run(spendingLlmAgent, prompt, ExecutionOptions.defaultOptions());

                String text = result.getOutputText();
                log.debug("ADK LlmAgent output (budget): {}", text);

                int start = text.indexOf('{');
                int end = text.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    String json = text.substring(start, end + 1);
                    Map<String, Object> out = new HashMap<>();
                    out.put("budget_plan_json", json);
                    return out;
                } else {
                    log.warn("ADK response did not include JSON for budget, falling back to BudgetPlanner");
                    String fallback = budgetPlanner.createBudget(userId, query);
                    Map<String, Object> out = new HashMap<>();
                    out.put("budget_plan", fallback);
                    return out;
                }
            } catch (Exception e) {
                log.error("ADK LlmAgent execution failed for budget; using fallback", e);
                String fallback = budgetPlanner.createBudget(userId, query);
                Map<String, Object> out = new HashMap<>();
                out.put("budget_plan", fallback);
                return out;
            }
        });
    }
}
