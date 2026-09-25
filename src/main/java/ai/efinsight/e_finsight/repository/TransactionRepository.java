package ai.efinsight.e_finsight.repository;

import ai.efinsight.e_finsight.model.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdAndTimestampBetween(Long userId, Instant start, Instant end);

    Optional<Transaction> findByTransactionId(String transactionId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.chunked = false")
    List<Transaction> findUnchunkedByUserId(Long userId);

    long countByUserId(Long userId);

    // Which of these TrueLayer transaction ids are already stored (one query instead of one per transaction)
    @Query("SELECT t.transactionId FROM Transaction t WHERE t.transactionId IN :transactionIds")
    Set<String> findExistingTransactionIds(Collection<String> transactionIds);

    @Modifying
    @Query("UPDATE Transaction t SET t.chunked = true WHERE t.id IN :ids")
    int markChunked(Collection<Long> ids);

    @Modifying
    @Query("UPDATE Transaction t SET t.chunked = false WHERE t.userId = :userId")
    int markAllUnchunked(Long userId);

    // Aggregates over ALL of a user's transactions in [from, to), used by the ADK analytics tools.
    // TrueLayer amounts are signed: negative = money out (spending), positive = money in (income).

    @Query("""
            SELECT t.currency AS currency,
                   SUM(CASE WHEN t.amount < 0 THEN -t.amount ELSE 0 END) AS spent,
                   SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END) AS received,
                   COUNT(t) AS transactionCount,
                   MIN(t.timestamp) AS firstTransaction,
                   MAX(t.timestamp) AS lastTransaction
            FROM Transaction t
            WHERE t.userId = :userId AND t.timestamp >= :from AND t.timestamp < :to
            GROUP BY t.currency
            """)
    List<CashflowSummary> summarizeCashflow(Long userId, Instant from, Instant to);

    @Query("""
            SELECT COALESCE(t.transactionCategory, 'UNCATEGORISED') AS label, t.currency AS currency,
                   SUM(-t.amount) AS total, COUNT(t) AS transactionCount
            FROM Transaction t
            WHERE t.userId = :userId AND t.amount < 0 AND t.timestamp >= :from AND t.timestamp < :to
            GROUP BY COALESCE(t.transactionCategory, 'UNCATEGORISED'), t.currency
            ORDER BY SUM(-t.amount) DESC
            """)
    List<SpendingGroup> sumSpendingByCategory(Long userId, Instant from, Instant to);

    @Query("""
            SELECT COALESCE(t.merchantName, t.description, 'UNKNOWN') AS label, t.currency AS currency,
                   SUM(-t.amount) AS total, COUNT(t) AS transactionCount
            FROM Transaction t
            WHERE t.userId = :userId AND t.amount < 0 AND t.timestamp >= :from AND t.timestamp < :to
            GROUP BY COALESCE(t.merchantName, t.description, 'UNKNOWN'), t.currency
            ORDER BY SUM(-t.amount) DESC
            """)
    List<SpendingGroup> sumSpendingByMerchant(Long userId, Instant from, Instant to, Pageable pageable);

    // Raw rows for monthly bucketing, which is done in Java: YEAR()/MONTH() in the database follow the connection's
    // session time zone (the JVM default), so the same transaction could land in a different month per environment
    @Query("""
            SELECT t.timestamp AS timestamp, t.amount AS amount, t.currency AS currency
            FROM Transaction t
            WHERE t.userId = :userId AND t.timestamp >= :from AND t.timestamp < :to AND t.amount IS NOT NULL
            """)
    List<AmountAtTime> findAmountsInRange(Long userId, Instant from, Instant to);

    interface CashflowSummary {
        String getCurrency();
        BigDecimal getSpent();
        BigDecimal getReceived();
        Long getTransactionCount();
        Instant getFirstTransaction();
        Instant getLastTransaction();
    }

    interface SpendingGroup {
        String getLabel();
        String getCurrency();
        BigDecimal getTotal();
        Long getTransactionCount();
    }

    interface AmountAtTime {
        Instant getTimestamp();
        BigDecimal getAmount();
        String getCurrency();
    }
}

