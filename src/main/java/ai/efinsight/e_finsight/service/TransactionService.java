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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TrueLayerApiService apiService;
    private final TransactionRepository transactionRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public TransactionService(
            TrueLayerApiService apiService, 
            TransactionRepository transactionRepository,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService) {
        this.apiService = apiService;
        this.transactionRepository = transactionRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    // Ingest transactions for a user
    @Transactional
    public int ingestTransactions(Long userId) {
        log.info("Starting transaction ingestion for user: {}", userId);
        String userIdStr = String.valueOf(userId);

        try {
            // Get the accounts for the user
            List<TrueLayerAccountDto> accounts = apiService.getAccounts(userIdStr);
            log.info("Found {} accounts for user: {}", accounts.size(), userId);

            int totalIngested = 0;
            String from = LocalDate.now().minusDays(90).toString();
            
            for (TrueLayerAccountDto account : accounts) {
                try {
                    List<TrueLayerTransactionDto> transactions = apiService.getAccountTransactions(
                            userIdStr, account.getAccountId(), from, null);

                    for (TrueLayerTransactionDto txnDto : transactions) {
                        if (transactionRepository.findByTransactionId(txnDto.getTransactionId()).isPresent()) {
                            continue;
                        }

                        Transaction transaction = convertToEntity(userId, account.getAccountId(), txnDto);
                        transaction = transactionRepository.save(transaction);
                        totalIngested++;
                        
                        try {
                            processTransactionChunks(transaction);
                            transaction.setChunked(true);
                            transactionRepository.save(transaction);
                        } catch (Exception e) {
                            log.warn("Failed to process chunks for transaction: {}", transaction.getTransactionId(), e);
                        }
                    }

                    log.info("Ingested {} transactions for account: {} (user: {})", 
                        transactions.size(), account.getAccountId(), userId);
                } catch (Exception e) {
                    log.warn("Failed to ingest transactions for account: {}", account.getAccountId(), e);
                }
            }

            log.info("Transaction ingestion completed for user: {}. Total ingested: {}", userId, totalIngested);
            return totalIngested;
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

    @Transactional
    public void processTransactionChunks(Transaction transaction) {
        // Chunk the transaction into chunks
        List<String> chunks = chunkingService.chunkTransaction(transaction);
        // Generate embeddings for the chunks
        List<float[]> embeddings = embeddingService.generateEmbeddings(chunks);
        
        if (chunks.size() == embeddings.size()) {
            vectorStoreService.storeChunks(
                transaction.getUserId(),
                transaction.getId(),
                chunks,
                embeddings
            );
            log.debug("Processed {} chunks for transaction: {}", chunks.size(), transaction.getId());
        } else {
            log.warn("Mismatch between chunks ({}) and embeddings ({}) for transaction: {}", 
                chunks.size(), embeddings.size(), transaction.getId());
        }
    }

    @Transactional
    public int processUnprocessedTransactions(Long userId) {
        List<Transaction> unprocessed = transactionRepository.findUnchunkedByUserId(userId);
        log.info("Processing {} unprocessed transactions for user: {}", unprocessed.size(), userId);
        
        int processed = 0;
        for (Transaction transaction : unprocessed) {
            try {
                processTransactionChunks(transaction);
                transaction.setChunked(true);
                transactionRepository.save(transaction);
                processed++;
            } catch (Exception e) {
                log.warn("Failed to process transaction: {}", transaction.getId(), e);
            }
        }
        
        log.info("Processed {} transactions for user: {}", processed, userId);
        return processed;
    }

    @Transactional
    public int reprocessAllTransactions(Long userId) {
        log.info("Re-processing all transactions for user: {}", userId);
        
        // Delete all existing chunks for this user
        vectorStoreService.deleteChunksByUserId(userId);
        
        // Mark all transactions as unprocessed
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
        for (Transaction transaction : allTransactions) {
            transaction.setChunked(false);
            transactionRepository.save(transaction);
        }
        
        // Process all transactions
        return processUnprocessedTransactions(userId);
    }
}

