package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.InvestmentAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AdkInvestmentAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(AdkInvestmentAgent.class);

    private final InvestmentAdvisor investmentAdvisor;

    public AdkInvestmentAgent(InvestmentAdvisor investmentAdvisor) {
        this.investmentAdvisor = investmentAdvisor;
    }

    @Override
    public CompletableFuture<Map<String, Object>> run(Long userId, String query) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkInvestmentAgent running for user: {}", userId);
            String result = investmentAdvisor.provideAdvice(userId, query);
            Map<String, Object> out = new HashMap<>();
            out.put("investment_advice", result);
            return out;
        });
    }
}
