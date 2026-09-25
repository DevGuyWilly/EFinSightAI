package ai.efinsight.e_finsight.adk;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Turns a follow-up ("what about last month?") into a standalone question for RAG retrieval, since embedding the
 * follow-up on its own would find the wrong transactions. Best effort: on any failure the original question is used.
 */
@Component
public class QuestionRewriter {
    private static final Logger log = LoggerFactory.getLogger(QuestionRewriter.class);

    static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final LlmAgent questionRewriterAgent;

    public QuestionRewriter(@Qualifier("questionRewriterAgent") LlmAgent questionRewriterAgent) {
        this.questionRewriterAgent = questionRewriterAgent;
    }

    public String rewrite(Long userId, String conversationHistory, String question) {
        try {
            InMemoryRunner runner = new InMemoryRunner(questionRewriterAgent);
            String sessionUserId = String.valueOf(userId);
            ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
            state.put(AdkConfig.CONVERSATION_HISTORY_STATE_KEY, conversationHistory);
            state.put(AdkConfig.CURRENT_DATE_STATE_KEY, LocalDate.now(ZoneOffset.UTC).toString());
            Session session = runner.sessionService()
                    .createSession(runner.appName(), sessionUserId, state, null)
                    .blockingGet();

            List<Event> events = runner.runAsync(sessionUserId, session.id(), Content.fromParts(Part.fromText(question)))
                    .toList()
                    .timeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .blockingGet();
            String rewritten = events.stream()
                    .filter(Event::finalResponse)
                    .reduce((first, second) -> second)
                    .map(Event::stringifyContent)
                    .map(String::strip)
                    .orElse("");
            if (rewritten.isBlank()) {
                log.warn("Question rewriter returned nothing; using the original question for retrieval");
                return question;
            }
            log.debug("Rewrote follow-up '{}' as '{}'", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("Question rewriting failed; using the original question for retrieval", e);
            return question;
        }
    }
}
