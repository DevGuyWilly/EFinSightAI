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

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class AdkCoordinatorAgentNative {
    private static final Logger log = LoggerFactory.getLogger(AdkCoordinatorAgentNative.class);

    // Maps a specialist agent's tool name to the response key consumers expect
    private static final Map<String, String> AGENT_NAME_TO_RESPONSE_KEY = Map.of(
            "spending-analyst", "spending_analysis",
            "budget-planner", "budget_plan",
            "investment-advisor", "investment_advice"
    );

    // Upper bound on a whole coordinator run (all model and tool calls), so a hung call can't hold a request thread
    static final Duration RUN_TIMEOUT = Duration.ofSeconds(120);

    // Conversation history value for the first question of a conversation
    public static final String NO_HISTORY = "(This is the first question in the conversation.)";

    private final LlmAgent rootCoordinatorAgent;

    public AdkCoordinatorAgentNative(@Qualifier("rootCoordinatorAgent") LlmAgent rootCoordinatorAgent) {
        this.rootCoordinatorAgent = rootCoordinatorAgent;
    }

    // Runs the coordinator synchronously; throws PlanGenerationException rather than returning a failed result
    // conversationHistory: earlier turns formatted for the prompt, or NO_HISTORY for a new conversation
    public CoordinatorResult run(Long userId, String query, String contextText, String conversationHistory) {
        log.info("AdkCoordinatorAgentNative routing query for user: {}", userId);

        // The transaction context lives in session state (not the prompt) so the specialists see it too
        String prompt = "User question: " + query
                + "\n\nDecide which specialist tool(s) are relevant to this question and call them, "
                + "then give the user a concise combined answer.";

        List<Event> events;
        try {
            InMemoryRunner runner = new InMemoryRunner(rootCoordinatorAgent);
            String sessionUserId = String.valueOf(userId);
            ConcurrentMap<String, Object> initialState = new ConcurrentHashMap<>();
            initialState.put(AdkConfig.TRANSACTION_CONTEXT_STATE_KEY, contextText);
            initialState.put(AdkConfig.CONVERSATION_HISTORY_STATE_KEY, conversationHistory);
            initialState.put(AdkConfig.CURRENT_DATE_STATE_KEY, LocalDate.now(ZoneOffset.UTC).toString());
            // Trusted user id for TransactionAnalyticsTools, so tools never take it from model arguments
            initialState.put(TransactionAnalyticsTools.USER_ID_STATE_KEY, sessionUserId);
            Session session = runner.sessionService()
                    .createSession(runner.appName(), sessionUserId, initialState, null)
                    .blockingGet();

            Content message = Content.fromParts(Part.fromText(prompt));
            events = runner.runAsync(sessionUserId, session.id(), message)
                    .toList()
                    .timeout(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .blockingGet();
        } catch (Exception e) {
            log.error("ADK coordinator execution failed for user: {}", userId, e);
            throw new PlanGenerationException("ADK coordinator execution failed: " + e.getMessage(), e);
        }

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
        if (summary.isBlank()) {
            log.error("ADK coordinator produced no final answer for user: {} ({} events)", userId, events.size());
            throw new PlanGenerationException("ADK coordinator produced no final answer");
        }

        log.debug("Coordinator invoked specialists: {}", agentResponses.keySet());
        return new CoordinatorResult(summary, agentResponses);
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
