package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs on in-memory H2 by default; set TEST_DB_URL/USERNAME/PASSWORD/DRIVER/DIALECT to run against real Postgres
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:analytics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARCHAR}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.datasource.driver-class-name=${TEST_DB_DRIVER:org.h2.Driver}",
        "spring.jpa.database-platform=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.properties.hibernate.dialect=${TEST_DB_DIALECT:org.hibernate.dialect.H2Dialect}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(TransactionAnalyticsTools.class)
class TransactionAnalyticsToolsTest {
    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionAnalyticsTools tools;

    private int sequence;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        save(USER, "2026-07-03T09:00:00Z", "-40.00", "GBP", "PURCHASE", "TESCO", "TESCO STORES 123");
        save(USER, "2026-07-10T09:00:00Z", "-60.00", "GBP", "PURCHASE", "TESCO", "TESCO STORES 123");
        save(USER, "2026-07-15T09:00:00Z", "-900.00", "GBP", "DIRECT_DEBIT", null, "RENT LANDLORD");
        save(USER, "2026-07-25T09:00:00Z", "2500.00", "GBP", "CREDIT", null, "SALARY");
        save(USER, "2026-08-02T09:00:00Z", "-25.50", "GBP", "PURCHASE", "AMAZON", "AMAZON MKTPLACE");
        save(USER, "2026-08-31T23:30:00Z", "-10.00", "GBP", null, "PRET", "PRET A MANGER");
        // Another user's data must never leak into the first user's figures
        save(OTHER_USER, "2026-07-05T09:00:00Z", "-99999.00", "GBP", "PURCHASE", "TESCO", "TESCO STORES 999");
    }

    @Test
    void spendingSummaryTotalsAllTransactionsForTheUserOnly() {
        Map<String, Object> result = tools.spendingSummary(USER, null, null);

        List<Map<String, Object>> byCurrency = rows(result, "by_currency");
        assertThat(byCurrency).hasSize(1);
        Map<String, Object> gbp = byCurrency.get(0);
        assertThat(gbp.get("currency")).isEqualTo("GBP");
        assertThat(gbp.get("total_spent")).isEqualTo(1035.50);
        assertThat(gbp.get("total_received")).isEqualTo(2500.00);
        assertThat(gbp.get("net_cashflow")).isEqualTo(1464.50);
        assertThat(gbp.get("transaction_count")).isEqualTo(6L);
        assertThat(result.get("start_date")).isEqualTo("all");
    }

    @Test
    void spendingByCategoryIsSortedDescendingAndBucketsMissingCategories() {
        List<Map<String, Object>> categories = rows(tools.spendingByCategory(USER, null, null), "categories");

        assertThat(categories).extracting(row -> row.get("category"))
                .containsExactly("DIRECT_DEBIT", "PURCHASE", "UNCATEGORISED");
        assertThat(categories).extracting(row -> row.get("total_spent"))
                .containsExactly(900.00, 125.50, 10.00);
        assertThat(categories.get(1).get("transaction_count")).isEqualTo(3L);
    }

    @Test
    void topMerchantsFallsBackToDescriptionAndRespectsLimit() {
        List<Map<String, Object>> merchants = rows(tools.topMerchants(USER, null, null, 2), "merchants");

        assertThat(merchants).extracting(row -> row.get("merchant"))
                .containsExactly("RENT LANDLORD", "TESCO");
        assertThat(merchants.get(1).get("total_spent")).isEqualTo(100.00);
    }

    @Test
    void topMerchantsClampsOutOfRangeLimits() {
        assertThat(rows(tools.topMerchants(USER, null, null, 0), "merchants")).hasSize(1);
        assertThat(rows(tools.topMerchants(USER, null, null, 1000), "merchants")).hasSize(4);
    }

    @Test
    void monthlyCashflowGroupsByCalendarMonthOldestFirst() {
        List<Map<String, Object>> months = rows(tools.monthlyCashflow(USER, null, null), "months");

        assertThat(months).extracting(row -> row.get("month")).containsExactly("2026-07", "2026-08");
        assertThat(months.get(0).get("total_spent")).isEqualTo(1000.00);
        assertThat(months.get(0).get("total_received")).isEqualTo(2500.00);
        assertThat(months.get(1).get("total_spent")).isEqualTo(35.50);
    }

    @Test
    void dateRangeIsInclusiveOfBothEndDates() {
        Map<String, Object> result = tools.spendingSummary(USER, "2026-07-10", "2026-08-31");

        Map<String, Object> gbp = rows(result, "by_currency").get(0);
        // 60 + 900 + 25.50 + 10 (the 23:30 transaction on the end date is included)
        assertThat(gbp.get("total_spent")).isEqualTo(995.50);
        assertThat(gbp.get("transaction_count")).isEqualTo(5L);
        assertThat(result.get("end_date")).isEqualTo("2026-08-31");
    }

    @Test
    void emptyRangeReturnsNoteInsteadOfRows() {
        Map<String, Object> result = tools.spendingByCategory(USER, "2020-01-01", "2020-12-31");

        assertThat(rows(result, "categories")).isEmpty();
        assertThat(result).containsKey("note");
    }

    @Test
    void invalidDatesAreRejectedWithAnActionableMessage() {
        assertThatThrownBy(() -> tools.spendingSummary(USER, "last month", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> tools.spendingSummary(USER, "2026-08-01", "2026-07-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after");
    }

    @Test
    void toolWithoutUserInSessionStateReturnsErrorInsteadOfQuerying() {
        Map<String, Object> result = tools.getSpendingSummary(null, null, null);

        assertThat(result).containsKey("error");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return (List<Map<String, Object>>) result.get(key);
    }

    private void save(long userId, String timestamp, String amount, String currency, String category,
                      String merchant, String description) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setTransactionId("tx-" + (++sequence));
        transaction.setAccountId("acc-" + userId);
        transaction.setTimestamp(Instant.parse(timestamp));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency(currency);
        transaction.setTransactionCategory(category);
        transaction.setMerchantName(merchant);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }
}
