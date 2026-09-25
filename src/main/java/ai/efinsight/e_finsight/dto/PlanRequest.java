package ai.efinsight.e_finsight.dto;

// POST /api/plan body; conversationId is omitted to start a new conversation
public record PlanRequest(String question, Long conversationId) {
}
