package ai.efinsight.e_finsight.adk;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class AdkCoordinatorAgentNative {
    private static final Logger log = LoggerFactory.getLogger(AdkCoordinatorAgentNative.class);

    // Maps a specialist agent's tool name to the response key consumers expect
    private static final Map<String, String> AGENT_NAME_TO_RESPONSE_KEY = Map.of(
            "spending-analyst", "spending_analysis",
            "budget-planner", "budget_plan",
            "investment-advisor", "investment_advice"
    );

    private final LlmAgent rootCoordinatorAgent;

    public AdkCoordinatorAgentNative(@Qualifier("rootCoordinatorAgent") LlmAgent rootCoordinatorAgent) {
        this.rootCoordinatorAgent = rootCoordinatorAgent;
    }

    public CompletableFuture<CoordinatorResult> run(Long userId, String query, String contextText) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("AdkCoordinatorAgentNative routing query for user: {}", userId);

            String prompt = "User question: " + query
                    + "\n\n" + contextText
                    + "\n\nDecide which specialist tool(s) are relevant to this question and call them, "
                    + "then give the user a concise combined answer.";

            try {
                InMemoryRunner runner = new InMemoryRunner(rootCoordinatorAgent);
                String sessionUserId = String.valueOf(userId);
                Session session = runner.sessionService()
                        .createSession(runner.appName(), sessionUserId)
                        .blockingGet();

                Content message = Content.fromParts(Part.fromText(prompt));
                List<Event> events = runner.runAsync(sessionUserId, session.id(), message)
                        .toList()
                        .blockingGet();

                Map<String, String> agentResponses = new HashMap<>();
                for (Event event : events) {
                    for (FunctionResponse response : event.functionResponses()) {
                        String toolName = response.name().orElse("");
                        String responseKey = AGENT_NAME_TO_RESPONSE_KEY.get(toolName);
                        if (responseKey != null) {
                            agentResponses.put(responseKey, extractResponseText(response));
                        }
                    }
                }

                String summary = events.stream()
                        .filter(Event::finalResponse)
                        .reduce((first, second) -> second)
                        .map(Event::stringifyContent)
                        .orElse("");

                log.debug("Coordinator invoked specialists: {}", agentResponses.keySet());
                return new CoordinatorResult(summary, agentResponses);
            } catch (Exception e) {
                log.error("ADK coordinator execution failed", e);
                return new CoordinatorResult("Unable to generate a plan at this time. Error: " + e.getMessage(), Map.of());
            }
        });
    }

    private String extractResponseText(FunctionResponse response) {
        return response.response()
                .map(map -> {
                    Object result = map.get("result");
                    return result != null ? String.valueOf(result) : String.valueOf(map);
                })
                .orElse("");
    }

    public record CoordinatorResult(String summary, Map<String, String> agentResponses) {
    }
}
