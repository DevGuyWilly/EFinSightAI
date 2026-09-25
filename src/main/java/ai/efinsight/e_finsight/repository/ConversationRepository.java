package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    // Scoped by user so one user can never load another user's conversation
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT c.id AS id, c.title AS title, c.createdAt AS createdAt, c.updatedAt AS updatedAt,
                   COUNT(m) AS messageCount
            FROM Conversation c LEFT JOIN ConversationMessage m ON m.conversation = c
            WHERE c.userId = :userId
            GROUP BY c.id, c.title, c.createdAt, c.updatedAt
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<ConversationSummary> summarizeByUserId(Long userId);

    // Messages go with them via the ON DELETE CASCADE foreign key
    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.userId = :userId")
    int deleteAllByUserId(Long userId);

    interface ConversationSummary {
        Long getId();
        String getTitle();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        Long getMessageCount();
    }
}
