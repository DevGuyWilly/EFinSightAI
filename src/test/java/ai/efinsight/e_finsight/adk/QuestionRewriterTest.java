package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.llm.LLMConfig;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRegistry;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The real question-rewriter agent from AdkConfig against a scripted fake model (no Gemini calls)
class QuestionRewriterTest {
    private static final String MODEL = "rewriter-test-model";

    // What the fake model answers: text, or null to fail the call
    private static volatile String reply;
    private static volatile String lastInstruction;

    static class ScriptedLlm extends BaseLlm {
        ScriptedLlm(String model) {
            super(model);
        }

        @Override
        public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            lastInstruction = String.join("\n", request.getSystemInstructions());
            if (reply == null) {
                return Flowable.error(new RuntimeException("503 UNAVAILABLE"));
            }
            return Flowable.just(LlmResponse.builder().content(Content.fromParts(Part.fromText(reply))).build());
        }

        @Override
        public BaseLlmConnection connect(LlmRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeAll
    static void registerFakeModel() {
        LlmRegistry.registerLlm(MODEL, ScriptedLlm::new);
    }

    private final QuestionRewriter rewriter;

    QuestionRewriterTest() {
        LLMConfig llmConfig = new LLMConfig();
        llmConfig.setChatModel(MODEL);
        rewriter = new QuestionRewriter(new AdkConfig().questionRewriterAgent(llmConfig));
    }

    @Test
    void returnsTheStandaloneQuestionAndGivesTheModelTheConversation() {
        reply = "  How much did I spend at Tesco in August 2026?\n";
        String history = "User: How much did I spend at Tesco in July?\n\nAssistant: £120.40.";

        String rewritten = rewriter.rewrite(1L, history, "What about August?");

        assertThat(rewritten).isEqualTo("How much did I spend at Tesco in August 2026?");
        assertThat(lastInstruction).contains(history).contains("Today's date is");
    }

    @Test
    void fallsBackToTheOriginalQuestionWhenTheModelFails() {
        reply = null;

        assertThat(rewriter.rewrite(1L, "User: hi", "What about August?")).isEqualTo("What about August?");
    }

    @Test
    void fallsBackToTheOriginalQuestionWhenTheModelReturnsNothing() {
        reply = "   ";

        assertThat(rewriter.rewrite(1L, "User: hi", "What about August?")).isEqualTo("What about August?");
    }
}
