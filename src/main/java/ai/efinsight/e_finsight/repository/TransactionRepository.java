package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);
    
    List<Transaction> findByUserIdAndTimestampBetween(Long userId, Instant start, Instant end);
    
    Optional<Transaction> findByTransactionId(String transactionId);
    
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.chunked = false")
    List<Transaction> findUnchunkedByUserId(Long userId);
    
    long countByUserId(Long userId);
}

