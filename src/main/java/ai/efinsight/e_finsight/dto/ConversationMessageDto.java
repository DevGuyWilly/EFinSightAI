package ai.efinsight.e_finsight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// role is "user" or "assistant"; response (the full answer) is only present on assistant messages
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationMessageDto(Long id, String role, String content, Instant createdAt, PlanResponseDto response) {
}
