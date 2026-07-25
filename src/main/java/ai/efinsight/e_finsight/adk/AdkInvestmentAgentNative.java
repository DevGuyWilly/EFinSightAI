package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.InvestmentAdvisor;
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
public class AdkInvestmentAgentNative {
    private static final Logger log = LoggerFactory.getLogger(AdkInvestmentAgentNative.class);

    private final LlmAgent investmentLlmAgent;
    private final InvestmentAdvisor investmentAdvisor; // fallback

    public AdkInvestmentAgentNative(LlmAgent investmentLlmAgent, InvestmentAdvisor investmentAdvisor) {
        this.investmentLlmAgent = investmentLlmAgent;
        this.investmentAdvisor = investmentAdvisor;
    }

    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkInvestmentAgentNative running ADK LlmAgent for user: {}", userId);

            String prompt = "You are a financial investment advisor. Given the user's question and the retrieved transaction context, " +
                    "produce a JSON object with " +
                    "keys: recommendations (list of {instrument, amount, rationale}), risk_profile (string), steps (list). " +
                    "Only output valid JSON." +
                    "\n\nUser question: " + query + "\n\nProvide output as JSON only.";

            try {
                AgentRunner runner = AgentRunner.create();
                AgentExecutionResult result = runner.run(investmentLlmAgent, prompt, ExecutionOptions.defaultOptions());

                String text = result.getOutputText();
                log.debug("ADK LlmAgent output (investment): {}", text);

                int start = text.indexOf('{');
                int end = text.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    String json = text.substring(start, end + 1);
                    Map<String, Object> out = new HashMap<>();
                    out.put("investment_advice_json", json);
                    return out;
                } else {
                    log.warn("ADK response did not include JSON for investment, falling back to InvestmentAdvisor");
                    String fallback = investmentAdvisor.provideAdvice(userId, query);
                    Map<String, Object> out = new HashMap<>();
                    out.put("investment_advice", fallback);
                    return out;
                }
            } catch (Exception e) {
                log.error("ADK LlmAgent execution failed for investment; using fallback", e);
                String fallback = investmentAdvisor.provideAdvice(userId, query);
                Map<String, Object> out = new HashMap<>();
                out.put("investment_advice", fallback);
                return out;
            }
        });
    }
}
