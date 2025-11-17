package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionsController {
    private static final Logger log = LoggerFactory.getLogger(TransactionsController.class);

    private final TransactionService transactionService;

    public TransactionsController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestTransactions(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            log.info("Transaction ingestion requested for user: {}", userId);

            int ingested = transactionService.ingestTransactions(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Transactions ingested successfully");
            response.put("count", ingested);
            response.put("userId", userId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during transaction ingestion", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to ingest transactions: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> getTransactions(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            List<Transaction> transactions = transactionService.getUserTransactions(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("count", transactions.size());
            response.put("transactions", transactions);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching transactions", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch transactions: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/count")
    public ResponseEntity<?> getTransactionCount(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            long count = transactionService.getTransactionCount(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("userId", userId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching transaction count", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch transaction count: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

