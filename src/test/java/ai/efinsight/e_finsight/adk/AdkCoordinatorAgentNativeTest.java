package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.llm.LLMConfig;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRegistry;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs the real AdkConfig agents through AdkCoordinatorAgentNative against a scripted fake model (no Gemini calls)
class AdkCoordinatorAgentNativeTest {
    private static final String MODEL = "coordinator-test-model";

    enum Behaviour { ANSWER, MODEL_ERROR, NO_FINAL_TEXT }

    private static volatile Behaviour behaviour;
    // System instruction of the most recent coordinator model call
    private static volatile String coordinatorInstruction;

    private AdkCoordinatorAgentNative coordinator;

    static class ScriptedLlm extends BaseLlm {
        ScriptedLlm(String model) {
            super(model);
        }

        @Override
        public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            if (behaviour == Behaviour.MODEL_ERROR) {
                return Flowable.error(new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded"));
            }
            String instruction = String.join("\n", request.getSystemInstructions());
            boolean coordinator = instruction.contains("financial coordinator");
            if (coordinator) {
                coordinatorInstruction = instruction;
            }
            boolean hasToolResult = request.contents().stream()
                    .flatMap(content -> content.parts().orElse(List.of()).stream())
                    .anyMatch(part -> part.functionResponse().isPresent());

            if (coordinator && !hasToolResult) {
                return respond(Content.builder().role("model").parts(List.of(Part.builder().functionCall(
                        FunctionCall.builder().name("spending-analyst")
                                .args(Map.of("request", "Where do I spend the most?")).build()).build())).build());
            }
            if (coordinator && behaviour == Behaviour.NO_FINAL_TEXT) {
                return respond(Content.fromParts(Part.fromText("")));
            }
            return respond(Content.fromParts(Part.fromText(coordinator ? "You spend most at TESCO." : "TESCO: £412")));
        }

        private static Flowable<LlmResponse> respond(Content content) {
            return Flowable.just(LlmResponse.builder().content(content).build());
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

    @BeforeEach
    void buildAgents() {
        LLMConfig llmConfig = new LLMConfig();
        llmConfig.setChatModel(MODEL);
        TransactionAnalyticsTools tools = new TransactionAnalyticsTools(Mockito.mock(TransactionRepository.class));
        AdkConfig config = new AdkConfig();
        LlmAgent root = config.rootCoordinatorAgent(llmConfig,
                config.spendingLlmAgent(llmConfig, tools),
                config.budgetLlmAgent(llmConfig, tools),
                config.investmentLlmAgent(llmConfig, tools));
        coordinator = new AdkCoordinatorAgentNative(root);
    }

    @Test
    void returnsSummaryAndSpecialistResponses() {
        behaviour = Behaviour.ANSWER;

        AdkCoordinatorAgentNative.CoordinatorResult result = coordinator.run(1L, "Where do I spend the most?", "ctx", AdkCoordinatorAgentNative.NO_HISTORY);

        assertThat(result.summary()).isEqualTo("You spend most at TESCO.");
        assertThat(result.agentResponses()).containsExactly(Map.entry("spending_analysis", "TESCO: £412"));
    }

    @Test
    void conversationHistoryAndTodaysDateReachTheCoordinatorVerbatim() {
        behaviour = Behaviour.ANSWER;
        // $ and backslashes must survive placeholder substitution as literal text
        String history = "User: Did I spend over $50 at C:\\Tesco?\n\nAssistant: Yes, £62.10 in July.";

        coordinator.run(1L, "What about August?", "ctx", history);

        assertThat(coordinatorInstruction).contains(history);
        assertThat(coordinatorInstruction).contains("Today's date is " + java.time.LocalDate.now(java.time.ZoneOffset.UTC));
    }

    @Test
    void modelFailureThrowsInsteadOfReturningErrorAsSummary() {
        behaviour = Behaviour.MODEL_ERROR;

        assertThatThrownBy(() -> coordinator.run(1L, "Where do I spend the most?", "ctx", AdkCoordinatorAgentNative.NO_HISTORY))
                .isInstanceOf(PlanGenerationException.class)
                .hasMessageContaining("RESOURCE_EXHAUSTED");
    }

    @Test
    void runWithNoFinalAnswerIsAFailure() {
        behaviour = Behaviour.NO_FINAL_TEXT;

        assertThatThrownBy(() -> coordinator.run(1L, "Where do I spend the most?", "ctx", AdkCoordinatorAgentNative.NO_HISTORY))
                .isInstanceOf(PlanGenerationException.class)
                .hasMessageContaining("no final answer");
    }
}
