package ai.efinsight.e_finsight.dto;

import java.time.Instant;
import java.util.List;

// GET /api/conversations/{id}: the conversation with every message, oldest first
public record ConversationDetailDto(Long id, String title, Instant createdAt, Instant updatedAt,
                                    List<ConversationMessageDto> messages) {
}
