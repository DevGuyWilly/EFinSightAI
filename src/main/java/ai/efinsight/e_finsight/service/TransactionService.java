package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.dto.TrueLayerAccountDto;
import ai.efinsight.e_finsight.dto.TrueLayerTransactionDto;
import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import ai.efinsight.e_finsight.rag.ChunkingService;
import ai.efinsight.e_finsight.rag.EmbeddingService;
import ai.efinsight.e_finsight.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ingestion pipeline: fetch from TrueLayer, store new transactions, then chunk + embed + store chunks.
 *
 * External calls (TrueLayer, embedding API) run outside database transactions, so no connection is held open while
 * waiting on the network. Embedding is batched: each group of transactions costs one embedding API call per
 * {@link #EMBEDDING_GROUP_SIZE} chunks rather than one per transaction, and each group's chunks are stored and the
 * transactions marked chunked in one short database transaction. A group whose embedding fails stays unchunked and
 * is picked up again by {@link #processUnprocessedTransactions}.
 */
@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    // Transactions per embed-and-store group (one chunk each in practice, so one embedding API call per group)
    static final int EMBEDDING_GROUP_SIZE = 100;

    private final TrueLayerApiService apiService;
    private final TransactionRepository transactionRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            TrueLayerApiService apiService,
            TransactionRepository transactionRepository,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            TransactionTemplate transactionTemplate) {
        this.apiService = apiService;
        this.transactionRepository = transactionRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.transactionTemplate = transactionTemplate;
    }

    // Ingest transactions for a user; returns the number of newly stored transactions
    public int ingestTransactions(Long userId) {
        log.info("Starting transaction ingestion for user: {}", userId);
        String userIdStr = String.valueOf(userId);

        try {
            // Get the accounts for the user
            List<TrueLayerAccountDto> accounts = apiService.getAccounts(userIdStr);
            log.info("Found {} accounts for user: {}", accounts.size(), userId);

            String from = LocalDate.now().minusDays(90).toString();

            // Keyed by TrueLayer transaction id, so a transaction returned twice is only stored once
            Map<String, Transaction> fetched = new LinkedHashMap<>();
            for (TrueLayerAccountDto account : accounts) {
                try {
                    List<TrueLayerTransactionDto> transactions = apiService.getAccountTransactions(
                            userIdStr, account.getAccountId(), from, null);
                    for (TrueLayerTransactionDto txnDto : transactions) {
                        fetched.putIfAbsent(txnDto.getTransactionId(),
                                convertToEntity(userId, account.getAccountId(), txnDto));
                    }
                    log.info("Fetched {} transactions for account: {} (user: {})",
                        transactions.size(), account.getAccountId(), userId);
                } catch (Exception e) {
                    log.warn("Failed to ingest transactions for account: {}", account.getAccountId(), e);
                }
            }

            List<Transaction> newTransactions = new ArrayList<>();
            if (!fetched.isEmpty()) {
                Set<String> existing = transactionRepository.findExistingTransactionIds(fetched.keySet());
                fetched.forEach((transactionId, transaction) -> {
                    if (!existing.contains(transactionId)) {
                        newTransactions.add(transaction);
                    }
                });
            }
            List<Transaction> saved = transactionRepository.saveAll(newTransactions);
            log.info("Stored {} new transactions for user: {} ({} already present)",
                saved.size(), userId, fetched.size() - saved.size());

            int embedded = embedAndStoreChunks(saved);
            log.info("Transaction ingestion completed for user: {}. Total ingested: {}, embedded: {}",
                userId, saved.size(), embedded);
            return saved.size();
        } catch (Exception e) {
            log.error("Error during transaction ingestion for user: {}", userId, e);
            throw new RuntimeException("Failed to ingest transactions: " + e.getMessage(), e);
        }
    }

    private Transaction convertToEntity(Long userId, String accountId, TrueLayerTransactionDto dto) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setTransactionId(dto.getTransactionId());
        transaction.setAccountId(accountId);

        if (dto.getTimestamp() != null) {
            try {
                transaction.setTimestamp(Instant.parse(dto.getTimestamp()));
            } catch (Exception e) {
                log.warn("Failed to parse timestamp: {}", dto.getTimestamp());
            }
        }

        transaction.setDescription(dto.getDescription());
        transaction.setAmount(dto.getAmount());
        transaction.setCurrency(dto.getCurrency());
        transaction.setTransactionType(dto.getTransactionType());
        transaction.setTransactionCategory(dto.getTransactionCategory());
        transaction.setMerchantName(dto.getMerchantName());

        if (dto.getMeta() != null) {
            transaction.setProviderTransactionCategory(dto.getMeta().getProviderTransactionCategory());
        }

        return transaction;
    }

    public List<Transaction> getUserTransactions(Long userId) {
        return transactionRepository.findByUserId(userId);
    }

    public List<Transaction> getUserTransactions(Long userId, Instant from, Instant to) {
        return transactionRepository.findByUserIdAndTimestampBetween(userId, from, to);
    }

    public long getTransactionCount(Long userId) {
        return transactionRepository.countByUserId(userId);
    }

    // Chunk, embed and store chunks for stored transactions in groups; returns how many were fully processed
    private int embedAndStoreChunks(List<Transaction> transactions) {
        int processed = 0;
        for (int start = 0; start < transactions.size(); start += EMBEDDING_GROUP_SIZE) {
            List<Transaction> group = transactions.subList(start, Math.min(start + EMBEDDING_GROUP_SIZE, transactions.size()));
            try {
                processed += embedAndStoreGroup(group);
            } catch (Exception e) {
                // Left with chunked = false, so processUnprocessedTransactions / reprocess can retry them later
                log.warn("Failed to embed {} transactions (ids {}..{}); they remain unchunked",
                    group.size(), group.get(0).getId(), group.get(group.size() - 1).getId(), e);
            }
        }
        return processed;
    }

    private int embedAndStoreGroup(List<Transaction> group) {
        // Flatten every transaction's chunks into one list so the whole group is embedded in batched API calls
        List<String> texts = new ArrayList<>();
        List<Integer> chunkCounts = new ArrayList<>(group.size());
        for (Transaction transaction : group) {
            List<String> chunks = chunkingService.chunkTransaction(transaction);
            texts.addAll(chunks);
            chunkCounts.add(chunks.size());
        }

        // Network call, deliberately outside any database transaction
        List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException(
                "Mismatch between chunks (" + texts.size() + ") and embeddings (" + embeddings.size() + ")");
        }

        transactionTemplate.executeWithoutResult(status -> {
            int offset = 0;
            for (int i = 0; i < group.size(); i++) {
                Transaction transaction = group.get(i);
                int count = chunkCounts.get(i);
                vectorStoreService.storeChunks(
                    transaction.getUserId(),
                    transaction.getId(),
                    texts.subList(offset, offset + count),
                    embeddings.subList(offset, offset + count));
                offset += count;
            }
            transactionRepository.markChunked(group.stream().map(Transaction::getId).toList());
        });
        group.forEach(transaction -> transaction.setChunked(true));
        log.debug("Embedded and stored {} chunks for {} transactions", texts.size(), group.size());
        return group.size();
    }

    public int processUnprocessedTransactions(Long userId) {
        List<Transaction> unprocessed = transactionRepository.findUnchunkedByUserId(userId);
        log.info("Processing {} unprocessed transactions for user: {}", unprocessed.size(), userId);

        int processed = embedAndStoreChunks(unprocessed);

        log.info("Processed {} transactions for user: {}", processed, userId);
        return processed;
    }

    public int reprocessAllTransactions(Long userId) {
        log.info("Re-processing all transactions for user: {}", userId);

        // Delete all existing chunks and mark every transaction unprocessed, atomically
        transactionTemplate.executeWithoutResult(status -> {
            vectorStoreService.deleteChunksByUserId(userId);
            transactionRepository.markAllUnchunked(userId);
        });

        // Re-embed outside the transaction above, in batched groups
        return processUnprocessedTransactions(userId);
    }
}
