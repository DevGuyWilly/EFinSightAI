package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.SpendingAnalyst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AdkSpendingAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(AdkSpendingAgent.class);

    private final SpendingAnalyst spendingAnalyst;

    public AdkSpendingAgent(SpendingAnalyst spendingAnalyst) {
        this.spendingAnalyst = spendingAnalyst;
    }

    @Override
    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkSpendingAgent running for user: {}", userId);
            String result = spendingAnalyst.analyzeSpending(userId, query);
            Map<String, Object> out = new HashMap<>();
            out.put("spending_analysis", result);
            return out;
        });
    }
}
