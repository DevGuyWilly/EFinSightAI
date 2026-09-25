package ai.efinsight.e_finsight.model;

import jakarta.persistence.*;

import java.time.Instant;

// An advisor chat: an ordered series of ConversationMessages belonging to one user
@Entity
@Table(name = "conversations", indexes = @Index(name = "idx_conversations_user_updated", columnList = "user_id, updated_at"))
public class Conversation {
    public static final int MAX_TITLE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Bumped on every new message, so the most recently used conversations list first
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Conversation() {
    }

    public Conversation(Long userId, String title) {
        this.userId = userId;
        this.title = title;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
