package ai.efinsight.e_finsight.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

// One turn half in a Conversation: the user's question, or the advisor's answer
@Entity
@Table(name = "conversation_messages",
        indexes = @Index(name = "idx_conversation_messages_conversation", columnList = "conversation_id, id"))
public class ConversationMessage {
    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a conversation deletes its messages in the database (ON DELETE CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    // The question for USER messages; the answer's summary for ASSISTANT messages (used as conversation history)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // ASSISTANT only: the full answer (sections, citations, agent responses) as JSON, to re-render it later
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessage() {
    }

    public ConversationMessage(Conversation conversation, Role role, String content, String payload) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
