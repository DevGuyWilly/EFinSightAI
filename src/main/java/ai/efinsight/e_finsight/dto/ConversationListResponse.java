package ai.efinsight.e_finsight.dto;

import java.util.List;

// GET /api/conversations, most recently used first
public record ConversationListResponse(List<ConversationSummaryDto> conversations) {
}
