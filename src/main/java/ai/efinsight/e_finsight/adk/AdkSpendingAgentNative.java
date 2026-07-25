package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.SpendingAnalyst;
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
public class AdkSpendingAgentNative {
    private static final Logger log = LoggerFactory.getLogger(AdkSpendingAgentNative.class);

    private final LlmAgent spendingLlmAgent;
    private final SpendingAnalyst spendingAnalyst; // fallback

    public AdkSpendingAgentNative(LlmAgent spendingLlmAgent, SpendingAnalyst spendingAnalyst) {
        this.spendingLlmAgent = spendingLlmAgent;
        this.spendingAnalyst = spendingAnalyst;
    }

    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkSpendingAgentNative running ADK LlmAgent for user: {}", userId);

            String prompt = "You are a financial spending analyst. Given the user's question and the retrieved transaction context, produce a JSON object with keys: categories (map of category->amount), recurring_expenses (list), anomalies (list), insights (list). Only output valid JSON." +
                    "\n\nUser question: " + query + "\n\nProvide output as JSON only.";

            try {
                // Use ADK's AgentRunner to run the LLM agent with a simple prompt
                AgentRunner runner = AgentRunner.create();
                AgentExecutionResult result = runner.run(spendingLlmAgent, prompt, ExecutionOptions.defaultOptions());

                String text = result.getOutputText();
                log.debug("ADK LlmAgent output: {}", text);

                // Try to parse JSON-ish output by naive approach: look for first { ... }
                int start = text.indexOf('{');
                int end = text.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    String json = text.substring(start, end + 1);
                    Map<String, Object> out = new HashMap<>();
                    out.put("spending_analysis_json", json);
                    return out;
                } else {
                    log.warn("ADK response did not include JSON, falling back to SpendingAnalyst");
                    String fallback = spendingAnalyst.analyzeSpending(userId, query);
                    Map<String, Object> out = new HashMap<>();
                    out.put("spending_analysis", fallback);
                    return out;
                }
            } catch (Exception e) {
                log.error("ADK LlmAgent execution failed; using fallback", e);
                String fallback = spendingAnalyst.analyzeSpending(userId, query);
                Map<String, Object> out = new HashMap<>();
                out.put("spending_analysis", fallback);
                return out;
            }
        });
    }
}
