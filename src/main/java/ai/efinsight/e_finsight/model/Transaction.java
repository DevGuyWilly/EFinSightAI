package ai.efinsight.e_finsight.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId; // TrueLayer transaction ID

    @Column(name = "account_id", nullable = false)
    private String accountId; // TrueLayer account ID

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_category")
    private String transactionCategory;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "provider_transaction_category")
    private String providerTransactionCategory;

    @Column(name = "ingested_at")
    private LocalDateTime ingestedAt;

    @Column(name = "chunked")
    private boolean chunked = false; // Whether this transaction has been chunked and embedded

    @PrePersist
    protected void onCreate() {
        if (ingestedAt == null) {
            ingestedAt = LocalDateTime.now();
        }
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

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getTransactionCategory() {
        return transactionCategory;
    }

    public void setTransactionCategory(String transactionCategory) {
        this.transactionCategory = transactionCategory;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getProviderTransactionCategory() {
        return providerTransactionCategory;
    }

    public void setProviderTransactionCategory(String providerTransactionCategory) {
        this.providerTransactionCategory = providerTransactionCategory;
    }

    public LocalDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(LocalDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    /**
     * Generate a text summary for chunking/embedding
     */
    public String toTextSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction: ").append(description != null ? description : "Unknown");
        if (merchantName != null) {
            sb.append(" at ").append(merchantName);
        }
        sb.append(" | Amount: ").append(amount).append(" ").append(currency);
        if (transactionCategory != null) {
            sb.append(" | Category: ").append(transactionCategory);
        }
        if (timestamp != null) {
            sb.append(" | Date: ").append(timestamp);
        }
        return sb.toString();
    }
}

