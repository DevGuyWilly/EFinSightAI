package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.id = :conversationId ORDER BY m.id")
    List<ConversationMessage> findAllInOrder(Long conversationId);

    // Newest first; callers reverse it for chronological order
    @Query("SELECT m FROM ConversationMessage m WHERE m.conversation.id = :conversationId ORDER BY m.id DESC")
    List<ConversationMessage> findLatest(Long conversationId, Pageable pageable);
}
