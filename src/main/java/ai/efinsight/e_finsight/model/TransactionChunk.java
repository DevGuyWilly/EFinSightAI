package ai.efinsight.e_finsight.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_chunks", indexes = @Index(name = "idx_transaction_chunks_user_id", columnList = "user_id"))
public class TransactionChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId; // Foreign key to transactions table

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    // pgvector column (see PgVectorSchemaInitializer), mapped as its text form "[0.1,0.2,...]"; the cast lets
    // Postgres accept the string parameter the JDBC driver sends
    @Column(name = "embedding", columnDefinition = "vector")
    @ColumnTransformer(write = "CAST(? AS vector)")
    private String embedding;

    @Column(name = "chunk_index")
    private Integer chunkIndex; // If transaction is split into multiple chunks

    @Column(name = "vertex_datapoint_id")
    private String vertexDatapointId; // Vertex AI Vector Search datapoint ID

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getVertexDatapointId() {
        return vertexDatapointId;
    }

    public void setVertexDatapointId(String vertexDatapointId) {
        this.vertexDatapointId = vertexDatapointId;
    }
}

