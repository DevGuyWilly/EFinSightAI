package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.adk.PlanGenerationException;
import ai.efinsight.e_finsight.agent.AgentCoordinatorService;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanControllerTest {
    private final AgentCoordinatorService coordinatorService = mock(AgentCoordinatorService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlanController(coordinatorService)).build();
    }

    @Test
    void successfulPlanIsReturnedWithSuccessTrue() throws Exception {
        PlanResponseDto plan = new PlanResponseDto(true, "q", "You spend most at TESCO.",
                new PlanResponseDto.PlanSections(), List.of(), Map.of());
        when(coordinatorService.generateStructuredPlan(eq(1L), anyString(), any())).thenReturn(plan);

        mockMvc.perform(ask("Where do I spend the most?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.summary").value("You spend most at TESCO."));
    }

    @Test
    void agentFailureIs503WithSuccessFalseAndNoInternalDetails() throws Exception {
        when(coordinatorService.generateStructuredPlan(eq(1L), anyString(), any()))
                .thenThrow(new PlanGenerationException("ADK coordinator execution failed: 429 RESOURCE_EXHAUSTED"));

        mockMvc.perform(ask("Where do I spend the most?"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.error").value(not(containsString("RESOURCE_EXHAUSTED"))));
    }

    @Test
    void unexpectedFailureIs500WithSuccessFalse() throws Exception {
        when(coordinatorService.generateStructuredPlan(eq(1L), anyString(), any()))
                .thenThrow(new IllegalStateException("connection refused to db-host:5432"));

        mockMvc.perform(ask("Where do I spend the most?"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(not(containsString("db-host"))));
    }

    @Test
    void passesTheConversationIdThroughAndReturnsIt() throws Exception {
        PlanResponseDto plan = new PlanResponseDto(true, "q", "Less than July.", null, List.of(), Map.of());
        plan.setConversationId(42L);
        plan.setConversationTitle("Where do I spend the most?");
        when(coordinatorService.generateStructuredPlan(1L, "What about August?", 42L)).thenReturn(plan);

        mockMvc.perform(post("/api/plan")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  What about August?  \", \"conversationId\": 42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(42))
                .andExpect(jsonPath("$.conversationTitle").value("Where do I spend the most?"));
    }

    @Test
    void omittedConversationIdStartsANewConversation() throws Exception {
        when(coordinatorService.generateStructuredPlan(eq(1L), anyString(), isNull()))
                .thenReturn(new PlanResponseDto(true, "q", "ok", null, List.of(), Map.of()));

        mockMvc.perform(ask("Where do I spend the most?")).andExpect(status().isOk());

        verify(coordinatorService).generateStructuredPlan(1L, "Where do I spend the most?", null);
    }

    @Test
    void unknownOrForeignConversationIs404() throws Exception {
        when(coordinatorService.generateStructuredPlan(eq(1L), anyString(), eq(99L)))
                .thenThrow(new ConversationNotFoundException(99L));

        mockMvc.perform(post("/api/plan")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null, List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"hi\", \"conversationId\": 99}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void overlongQuestionIs400() throws Exception {
        mockMvc.perform(ask("x".repeat(PlanController.MAX_QUESTION_LENGTH + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void blankQuestionIs400() throws Exception {
        mockMvc.perform(ask(" "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static MockHttpServletRequestBuilder ask(String question) {
        return post("/api/plan")
                .principal(new UsernamePasswordAuthenticationToken(1L, null, List.of()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"" + question + "\"}");
    }
}
