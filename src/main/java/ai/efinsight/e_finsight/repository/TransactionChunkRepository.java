package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.TransactionChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionChunkRepository extends JpaRepository<TransactionChunk, Long> {
    List<TransactionChunk> findByUserId(Long userId);
    
    List<TransactionChunk> findByTransactionId(Long transactionId);
    
    // Exact cosine nearest-neighbour search in pgvector over one user's chunks (index-free: per-user scans are small,
    // and 3072-dim embeddings exceed pgvector's 2000-dim ANN index limit). Chunks embedded with a different model
    // (different dimensions) are skipped rather than erroring.
    @Query(value = """
            SELECT c.id AS chunkId, 1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM transaction_chunks c
            WHERE c.user_id = :userId AND c.embedding IS NOT NULL
              AND vector_dims(c.embedding) = vector_dims(CAST(:queryEmbedding AS vector))
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkMatch> findNearestByCosine(Long userId, String queryEmbedding, int topK);

    interface ChunkMatch {
        Long getChunkId();
        Double getSimilarity();
    }
    
    Optional<TransactionChunk> findByVertexDatapointId(String vertexDatapointId);
    
    void deleteByTransactionId(Long transactionId);
    
    void deleteByUserId(Long userId);
}

