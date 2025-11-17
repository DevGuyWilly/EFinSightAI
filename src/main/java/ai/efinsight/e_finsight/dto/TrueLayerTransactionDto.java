package ai.efinsight.e_finsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class TrueLayerTransactionDto {
    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("normalised_provider_transaction_id")
    private String normalisedProviderTransactionId;

    @JsonProperty("provider_transaction_id")
    private String providerTransactionId;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("description")
    private String description;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("transaction_type")
    private String transactionType;

    @JsonProperty("transaction_category")
    private String transactionCategory;

    @JsonProperty("transaction_classification")
    private String[] transactionClassification;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("running_balance")
    private RunningBalance runningBalance;

    @JsonProperty("meta")
    private TransactionMeta meta;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getNormalisedProviderTransactionId() {
        return normalisedProviderTransactionId;
    }

    public void setNormalisedProviderTransactionId(String normalisedProviderTransactionId) {
        this.normalisedProviderTransactionId = normalisedProviderTransactionId;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
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

    public String[] getTransactionClassification() {
        return transactionClassification;
    }

    public void setTransactionClassification(String[] transactionClassification) {
        this.transactionClassification = transactionClassification;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public TransactionMeta getMeta() {
        return meta;
    }

    public void setMeta(TransactionMeta meta) {
        this.meta = meta;
    }

    public RunningBalance getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(RunningBalance runningBalance) {
        this.runningBalance = runningBalance;
    }

    public static class RunningBalance {
        @JsonProperty("amount")
        private java.math.BigDecimal amount;

        @JsonProperty("currency")
        private String currency;

        public java.math.BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(java.math.BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    public static class TransactionMeta {
        @JsonProperty("bank_transaction_id")
        private String bankTransactionId;

        @JsonProperty("provider_category")
        private String providerCategory;

        @JsonProperty("provider_transaction_category")
        private String providerTransactionCategory;

        public String getBankTransactionId() {
            return bankTransactionId;
        }

        public void setBankTransactionId(String bankTransactionId) {
            this.bankTransactionId = bankTransactionId;
        }

        public String getProviderCategory() {
            return providerCategory;
        }

        public void setProviderCategory(String providerCategory) {
            this.providerCategory = providerCategory;
        }

        public String getProviderTransactionCategory() {
            return providerTransactionCategory;
        }

        public void setProviderTransactionCategory(String providerTransactionCategory) {
            this.providerTransactionCategory = providerTransactionCategory;
        }
    }
}

