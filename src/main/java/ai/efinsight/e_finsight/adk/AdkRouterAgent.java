package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
import ai.efinsight.e_finsight.agent.SpendingAnalyst;
import ai.efinsight.e_finsight.llm.LLMAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdkRouterAgent {
    private static final Logger log = LoggerFactory.getLogger(AdkRouterAgent.class);

    private final LLMAgent llmAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdkRouterAgent(LLMAgent llmAgent) {
        this.llmAgent = llmAgent;
    }

    /**
     * Decide which agents to run for a given query. Tries LLM-based routing first,
     * falls back to simple keyword rules.
     */
    public List<String> decideAgents(Long userId, String query) {
        List<String> agents = new ArrayList<>();

        String systemPrompt = "You are a router that decides which specialized financial agents (spending, budget, investment) should run for a user's query. Return a JSON array of agent names, e.g. [\"spending\", \"budget\"] and nothing else.";
        String response = null;
        try {
            response = llmAgent.generateResponse(systemPrompt, query);
            log.debug("Router LLM response: {}", response);

            // Try to parse JSON array
            JsonNode node = objectMapper.readTree(response);
            if (node.isArray()) {
                for (JsonNode n : node) {
                    String name = n.asText();
                    if (name != null && !name.isBlank()) agents.add(name.toLowerCase());
                }
            }
        } catch (Exception e) {
            log.warn("LLM routing failed or returned non-JSON; falling back to keyword router", e);
        }

        if (agents.isEmpty()) {
            // Fallback: simple heuristic (keeps compat with previous behavior)
            String lower = query.toLowerCase();
            if (lower.contains("spend") || lower.contains("expense") || lower.contains("where")) agents.add("spending");
            if (lower.contains("budget") || lower.contains("plan") || lower.contains("allocate")) agents.add("budget");
            if (lower.contains("invest") || lower.contains("save") || lower.contains("grow")) agents.add("investment");

            if (agents.isEmpty()) {
                agents.add("spending");
                agents.add("budget");
                agents.add("investment");
            }
        }

        return agents;
    }
}
