package ai.efinsight.e_finsight.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CitationDto {
    private Long transactionId;
    private String merchant;
    private String amount;
    private String currency;
    private String category;
    private String date;
    private String description;

    public CitationDto() {
    }

    public CitationDto(Long transactionId, String merchant, String amount, String currency, 
                      String category, String date, String description) {
        this.transactionId = transactionId;
        this.merchant = merchant;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.date = date;
        this.description = description;
    }

    // Getters and setters
    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

