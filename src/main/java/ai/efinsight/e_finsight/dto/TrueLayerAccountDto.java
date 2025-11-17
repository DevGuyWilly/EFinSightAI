package ai.efinsight.e_finsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrueLayerAccountDto {
    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("account_type")
    private String accountType;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("update_timestamp")
    private String updateTimestamp;

    @JsonProperty("account_number")
    private AccountNumber accountNumber;

    @JsonProperty("provider")
    private Provider provider;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getUpdateTimestamp() {
        return updateTimestamp;
    }

    public void setUpdateTimestamp(String updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(AccountNumber accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public static class AccountNumber {
        @JsonProperty("iban")
        private String iban;

        @JsonProperty("number")
        private String number;

        @JsonProperty("sort_code")
        private String sortCode;

        @JsonProperty("swift_bic")
        private String swiftBic;

        @JsonProperty("bsb")
        private String bsb;

        public String getIban() {
            return iban;
        }

        public void setIban(String iban) {
            this.iban = iban;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public String getSortCode() {
            return sortCode;
        }

        public void setSortCode(String sortCode) {
            this.sortCode = sortCode;
        }

        public String getSwiftBic() {
            return swiftBic;
        }

        public void setSwiftBic(String swiftBic) {
            this.swiftBic = swiftBic;
        }

        public String getBsb() {
            return bsb;
        }

        public void setBsb(String bsb) {
            this.bsb = bsb;
        }
    }

    public static class Provider {
        @JsonProperty("provider_id")
        private String providerId;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("logo_uri")
        private String logoUri;

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getLogoUri() {
            return logoUri;
        }

        public void setLogoUri(String logoUri) {
            this.logoUri = logoUri;
        }
    }
}

