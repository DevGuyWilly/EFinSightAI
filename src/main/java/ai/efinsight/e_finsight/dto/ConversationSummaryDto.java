package ai.efinsight.e_finsight.dto;

import java.time.Instant;

public record ConversationSummaryDto(Long id, String title, Instant createdAt, Instant updatedAt, long messageCount) {
}
