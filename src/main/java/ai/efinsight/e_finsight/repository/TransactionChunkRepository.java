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
    
    @Query("SELECT c FROM TransactionChunk c WHERE c.userId = :userId AND c.embedding IS NOT NULL")
    List<TransactionChunk> findEmbeddedChunksByUserId(Long userId);
    
    Optional<TransactionChunk> findByVertexDatapointId(String vertexDatapointId);
    
    void deleteByTransactionId(Long transactionId);
    
    void deleteByUserId(Long userId);
}

