package ai.efinsight.e_finsight.agent;

import ai.efinsight.e_finsight.adk.AdkCoordinatorAgentNative;
import ai.efinsight.e_finsight.adk.PlanGenerationException;
import ai.efinsight.e_finsight.adk.QuestionRewriter;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import ai.efinsight.e_finsight.model.Conversation;
import ai.efinsight.e_finsight.model.ConversationMessage;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import ai.efinsight.e_finsight.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentCoordinatorServiceTest {
    private static final long USER = 1L;

    private final AdkCoordinatorAgentNative coordinator = mock(AdkCoordinatorAgentNative.class);
    private final QuestionRewriter rewriter = mock(QuestionRewriter.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final RagService rag = mock(RagService.class);
    private final AgentCoordinatorService service = new AgentCoordinatorService(
            coordinator, rewriter, conversations, rag, mock(TransactionRepository.class));

    private final Conversation conversation = mock(Conversation.class);

    @BeforeEach
    void setUp() {
        when(conversation.getId()).thenReturn(7L);
        when(conversation.getTitle()).thenReturn("Where do I spend the most?");
        when(rag.retrieveContext(eq(USER), anyString(), anyInt())).thenReturn(List.of());
        when(rag.buildContextString(any())).thenReturn("ctx");
        when(coordinator.run(eq(USER), anyString(), anyString(), anyString()))
                .thenReturn(new AdkCoordinatorAgentNative.CoordinatorResult("You spend most at TESCO.",
                        Map.of("spending_analysis", "TESCO: £412")));
        when(conversations.recordTurn(eq(USER), any(), anyString(), any())).thenReturn(conversation);
    }

    @Test
    void firstQuestionStartsAConversationWithoutHistoryOrRewriting() {
        PlanResponseDto response = service.generateStructuredPlan(USER, "Where do I spend the most?", null);

        verifyNoInteractions(rewriter);
        verify(rag).retrieveContext(USER, "Where do I spend the most?", 15);
        verify(coordinator).run(USER, "Where do I spend the most?", "ctx", AdkCoordinatorAgentNative.NO_HISTORY);
        verify(conversations).recordTurn(eq(USER), isNull(), eq("Where do I spend the most?"), any());
        assertThat(response.getConversationId()).isEqualTo(7L);
        assertThat(response.getConversationTitle()).isEqualTo("Where do I spend the most?");
        assertThat(response.getSections().getSpendingAnalysis()).isEqualTo("TESCO: £412");
    }

    @Test
    void followUpUsesHistoryAndARewrittenQuestionForRetrieval() {
        when(conversations.requireOwned(USER, 7L)).thenReturn(conversation);
        when(conversations.recentMessages(7L, AgentCoordinatorService.HISTORY_MESSAGES)).thenReturn(List.of(
                new ConversationMessage(conversation, ConversationMessage.Role.USER, "Where do I spend the most?", null),
                new ConversationMessage(conversation, ConversationMessage.Role.ASSISTANT, "Most at TESCO.", "{}")));
        String history = "User: Where do I spend the most?\n\nAssistant: Most at TESCO.";
        when(rewriter.rewrite(USER, history, "What about August?")).thenReturn("Where did I spend the most in August 2026?");

        service.generateStructuredPlan(USER, "What about August?", 7L);

        verify(rag).retrieveContext(USER, "Where did I spend the most in August 2026?", 15);
        verify(coordinator).run(USER, "What about August?", "ctx", history);
        verify(conversations).recordTurn(eq(USER), eq(conversation), eq("What about August?"), any());
    }

    @Test
    void someoneElsesConversationIsRejectedBeforeAnyAiWork() {
        when(conversations.requireOwned(USER, 99L)).thenThrow(new ConversationNotFoundException(99L));

        assertThatThrownBy(() -> service.generateStructuredPlan(USER, "hi", 99L))
                .isInstanceOf(ConversationNotFoundException.class);
        verifyNoInteractions(rag, rewriter, coordinator);
    }

    @Test
    void failedAnswerIsNotSaved() {
        when(coordinator.run(eq(USER), anyString(), anyString(), anyString()))
                .thenThrow(new PlanGenerationException("model down"));

        assertThatThrownBy(() -> service.generateStructuredPlan(USER, "hi", null))
                .isInstanceOf(PlanGenerationException.class);
        verify(conversations, never()).recordTurn(any(), any(), any(), any());
    }

    @Test
    void longEarlierAnswersAreTruncatedInTheHistory() {
        String longAnswer = "x".repeat(AgentCoordinatorService.MAX_HISTORY_ANSWER_CHARS + 500);
        String history = AgentCoordinatorService.formatHistory(List.of(
                new ConversationMessage(conversation, ConversationMessage.Role.USER, "Q", null),
                new ConversationMessage(conversation, ConversationMessage.Role.ASSISTANT, longAnswer, "{}")));

        assertThat(history).startsWith("User: Q\n\nAssistant: x")
                .hasSize("User: Q\n\nAssistant: ".length() + AgentCoordinatorService.MAX_HISTORY_ANSWER_CHARS + 1)
                .endsWith("…");
    }
}
