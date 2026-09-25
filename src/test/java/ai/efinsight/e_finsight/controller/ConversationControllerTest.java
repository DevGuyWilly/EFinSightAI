package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.dto.ConversationDetailDto;
import ai.efinsight.e_finsight.dto.ConversationMessageDto;
import ai.efinsight.e_finsight.dto.ConversationSummaryDto;
import ai.efinsight.e_finsight.dto.PlanResponseDto;
import ai.efinsight.e_finsight.exception.ConversationNotFoundException;
import ai.efinsight.e_finsight.exception.GlobalExceptionHandler;
import ai.efinsight.e_finsight.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {
    private static final Instant CREATED = Instant.parse("2026-09-25T10:00:00Z");

    private final ConversationService conversationService = mock(ConversationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Same date format as Spring Boot's auto-configured ObjectMapper: ISO-8601 strings, not epoch numbers
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(
                Jackson2ObjectMapperBuilder.json().featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(json)
                .build();
    }

    @Test
    void listsTheUsersConversations() throws Exception {
        when(conversationService.list(1L)).thenReturn(List.of(
                new ConversationSummaryDto(7L, "Where do I spend the most?", CREATED, CREATED, 4)));

        mockMvc.perform(as(get("/api/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations[0].id").value(7))
                .andExpect(jsonPath("$.conversations[0].title").value("Where do I spend the most?"))
                .andExpect(jsonPath("$.conversations[0].updatedAt").value("2026-09-25T10:00:00Z"))
                .andExpect(jsonPath("$.conversations[0].messageCount").value(4));
    }

    @Test
    void returnsAConversationWithItsMessages() throws Exception {
        PlanResponseDto answer = new PlanResponseDto(true, "Where do I spend the most?", "Most at TESCO.",
                new PlanResponseDto.PlanSections("TESCO analysis", null, null), List.of(), Map.of());
        when(conversationService.get(1L, 7L)).thenReturn(new ConversationDetailDto(7L, "Where do I spend the most?",
                CREATED, CREATED, List.of(
                new ConversationMessageDto(1L, "user", "Where do I spend the most?", CREATED, null),
                new ConversationMessageDto(2L, "assistant", "Most at TESCO.", CREATED, answer))));

        mockMvc.perform(as(get("/api/conversations/7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].response").doesNotExist())
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].response.summary").value("Most at TESCO."))
                .andExpect(jsonPath("$.messages[1].response.sections.spendingAnalysis").value("TESCO analysis"));
    }

    @Test
    void unknownOrForeignConversationIs404() throws Exception {
        when(conversationService.get(1L, 99L)).thenThrow(new ConversationNotFoundException(99L));
        doThrow(new ConversationNotFoundException(99L)).when(conversationService).delete(1L, 99L);

        mockMvc.perform(as(get("/api/conversations/99"))).andExpect(status().isNotFound());
        mockMvc.perform(as(delete("/api/conversations/99"))).andExpect(status().isNotFound());
    }

    @Test
    void deletesOneConversation() throws Exception {
        mockMvc.perform(as(delete("/api/conversations/7"))).andExpect(status().isNoContent());
        verify(conversationService).delete(1L, 7L);
    }

    @Test
    void clearsAllConversations() throws Exception {
        mockMvc.perform(as(delete("/api/conversations"))).andExpect(status().isNoContent());
        verify(conversationService).deleteAll(1L);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.principal(new UsernamePasswordAuthenticationToken(1L, null, List.of()))
                .accept(MediaType.APPLICATION_JSON);
    }
}
