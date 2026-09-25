package ai.efinsight.e_finsight.exception;

// The conversation doesn't exist or belongs to another user; both are reported as 404 so ids can't be probed
public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long conversationId) {
        super("Conversation " + conversationId + " not found");
    }
}
