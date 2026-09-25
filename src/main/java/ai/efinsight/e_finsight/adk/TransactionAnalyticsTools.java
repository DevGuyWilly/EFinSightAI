package ai.efinsight.e_finsight.adk;

import ai.efinsight.e_finsight.repository.TransactionRepository;
import ai.efinsight.e_finsight.repository.TransactionRepository.AmountAtTime;
import ai.efinsight.e_finsight.repository.TransactionRepository.CashflowSummary;
import ai.efinsight.e_finsight.repository.TransactionRepository.SpendingGroup;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ADK function tools that compute exact figures over ALL of a user's transactions, complementing RAG retrieval
 * (which only surfaces the top-K most similar transactions and so cannot answer totals, rankings or trends).
 *
 * The user id is read from session state set by {@link AdkCoordinatorAgentNative}, never from model-supplied
 * arguments, so a model cannot query another user's data.
 */
@Component
public class TransactionAnalyticsTools {
    private static final Logger log = LoggerFactory.getLogger(TransactionAnalyticsTools.class);

    public static final String USER_ID_STATE_KEY = "user_id";

    // Time zone for interpreting the model's calendar dates and bucketing months, independent of JVM/database zones
    static final ZoneId REPORTING_ZONE = ZoneOffset.UTC;

    private static final int DEFAULT_MERCHANT_LIMIT = 10;
    private static final int MAX_MERCHANT_LIMIT = 50;

    private static final String START_DATE_DESCRIPTION =
            "Inclusive start date as YYYY-MM-DD. Omit to include all transactions from the beginning.";
    private static final String END_DATE_DESCRIPTION =
            "Inclusive end date as YYYY-MM-DD. Omit to include all transactions up to today.";

    private final TransactionRepository transactionRepository;

    public TransactionAnalyticsTools(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // Fresh FunctionTool instances for one agent
    public List<BaseTool> asTools() {
        return List.of(
                FunctionTool.create(this, "getSpendingSummary"),
                FunctionTool.create(this, "getSpendingByCategory"),
                FunctionTool.create(this, "getTopMerchants"),
                FunctionTool.create(this, "getMonthlyCashflow"));
    }

    // ---- ADK-facing tools: resolve the user from session state, then delegate ----

    @Schema(name = "get_spending_summary",
            description = "Exact totals over all of the user's transactions in a date range: total spent (money out), "
                    + "total received (money in), net cashflow, transaction count and the first/last transaction "
                    + "dates, per currency.")
    public Map<String, Object> getSpendingSummary(
            @Schema(name = "start_date", description = START_DATE_DESCRIPTION, optional = true) String startDate,
            @Schema(name = "end_date", description = END_DATE_DESCRIPTION, optional = true) String endDate,
            @Schema(name = "toolContext") ToolContext toolContext) {
        return withUser(toolContext, userId -> spendingSummary(userId, startDate, endDate));
    }

    @Schema(name = "get_spending_by_category",
            description = "Exact spending (money out only) over all of the user's transactions in a date range, grouped "
                    + "by transaction category and sorted from highest to lowest. Categories are bank transaction "
                    + "types such as PURCHASE, DIRECT_DEBIT, BILL_PAYMENT, TRANSFER or ATM.")
    public Map<String, Object> getSpendingByCategory(
            @Schema(name = "start_date", description = START_DATE_DESCRIPTION, optional = true) String startDate,
            @Schema(name = "end_date", description = END_DATE_DESCRIPTION, optional = true) String endDate,
            @Schema(name = "toolContext") ToolContext toolContext) {
        return withUser(toolContext, userId -> spendingByCategory(userId, startDate, endDate));
    }

    @Schema(name = "get_top_merchants",
            description = "Exact spending (money out only) over all of the user's transactions in a date range, grouped "
                    + "by merchant (or transaction description when no merchant is known) and sorted from highest "
                    + "to lowest.")
    public Map<String, Object> getTopMerchants(
            @Schema(name = "start_date", description = START_DATE_DESCRIPTION, optional = true) String startDate,
            @Schema(name = "end_date", description = END_DATE_DESCRIPTION, optional = true) String endDate,
            @Schema(name = "limit", description = "Number of merchants to return, 1-50. Defaults to 10.", optional = true)
            Integer limit,
            @Schema(name = "toolContext") ToolContext toolContext) {
        return withUser(toolContext, userId -> topMerchants(userId, startDate, endDate, limit));
    }

    @Schema(name = "get_monthly_cashflow",
            description = "Exact money spent and received per calendar month (UTC) over all of the user's transactions "
                    + "in a date range, oldest month first. Use it for trends and month-over-month comparisons.")
    public Map<String, Object> getMonthlyCashflow(
            @Schema(name = "start_date", description = START_DATE_DESCRIPTION, optional = true) String startDate,
            @Schema(name = "end_date", description = END_DATE_DESCRIPTION, optional = true) String endDate,
            @Schema(name = "toolContext") ToolContext toolContext) {
        return withUser(toolContext, userId -> monthlyCashflow(userId, startDate, endDate));
    }

    // ---- Implementations, keyed by an already-trusted user id ----

    Map<String, Object> spendingSummary(Long userId, String startDate, String endDate) {
        DateRange range = DateRange.parse(startDate, endDate);
        List<Map<String, Object>> byCurrency = transactionRepository
                .summarizeCashflow(userId, range.from(), range.to()).stream()
                .map(TransactionAnalyticsTools::toMap)
                .toList();
        return result(range, "by_currency", byCurrency);
    }

    Map<String, Object> spendingByCategory(Long userId, String startDate, String endDate) {
        DateRange range = DateRange.parse(startDate, endDate);
        List<Map<String, Object>> categories = transactionRepository
                .sumSpendingByCategory(userId, range.from(), range.to()).stream()
                .map(group -> toMap("category", group))
                .toList();
        return result(range, "categories", categories);
    }

    Map<String, Object> topMerchants(Long userId, String startDate, String endDate, Integer limit) {
        DateRange range = DateRange.parse(startDate, endDate);
        int size = limit == null ? DEFAULT_MERCHANT_LIMIT : Math.max(1, Math.min(limit, MAX_MERCHANT_LIMIT));
        List<Map<String, Object>> merchants = transactionRepository
                .sumSpendingByMerchant(userId, range.from(), range.to(), PageRequest.of(0, size)).stream()
                .map(group -> toMap("merchant", group))
                .toList();
        return result(range, "merchants", merchants);
    }

    Map<String, Object> monthlyCashflow(Long userId, String startDate, String endDate) {
        DateRange range = DateRange.parse(startDate, endDate);
        // Keyed by (month, currency); TreeMap keeps months oldest first
        Map<YearMonth, Map<String, MonthTotals>> byMonth = new TreeMap<>();
        for (AmountAtTime row : transactionRepository.findAmountsInRange(userId, range.from(), range.to())) {
            YearMonth month = YearMonth.from(row.getTimestamp().atZone(REPORTING_ZONE));
            byMonth.computeIfAbsent(month, m -> new TreeMap<>())
                    .computeIfAbsent(String.valueOf(row.getCurrency()), c -> new MonthTotals())
                    .add(row.getAmount());
        }

        List<Map<String, Object>> months = new ArrayList<>();
        byMonth.forEach((month, byCurrency) -> byCurrency.forEach((currency, totals) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("month", month.toString());
            map.put("currency", currency);
            map.put("total_spent", money(totals.spent));
            map.put("total_received", money(totals.received));
            map.put("transaction_count", totals.count);
            months.add(map);
        }));
        return result(range, "months", months);
    }

    private static final class MonthTotals {
        private BigDecimal spent = BigDecimal.ZERO;
        private BigDecimal received = BigDecimal.ZERO;
        private long count;

        void add(BigDecimal amount) {
            if (amount.signum() < 0) {
                spent = spent.add(amount.negate());
            } else {
                received = received.add(amount);
            }
            count++;
        }
    }

    // ---- Helpers ----

    private Map<String, Object> withUser(ToolContext toolContext, java.util.function.Function<Long, Map<String, Object>> body) {
        Object rawUserId = toolContext == null ? null : toolContext.state().get(USER_ID_STATE_KEY);
        if (rawUserId == null) {
            log.error("Analytics tool called without '{}' in session state", USER_ID_STATE_KEY);
            return Map.of("error", "User context is unavailable, so transaction totals cannot be computed.");
        }
        try {
            return body.apply(Long.valueOf(rawUserId.toString()));
        } catch (IllegalArgumentException e) {
            // Returned to the model (rather than thrown) so it can correct its arguments and retry
            return Map.of("error", e.getMessage());
        }
    }

    private static Map<String, Object> result(DateRange range, String key, List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("start_date", range.startLabel());
        result.put("end_date", range.endLabel());
        result.put(key, rows);
        if (rows.isEmpty()) {
            result.put("note", "No transactions found in this date range.");
        }
        return result;
    }

    private static Map<String, Object> toMap(CashflowSummary row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currency", row.getCurrency());
        map.put("total_spent", money(row.getSpent()));
        map.put("total_received", money(row.getReceived()));
        map.put("net_cashflow", money(nullToZero(row.getReceived()).subtract(nullToZero(row.getSpent()))));
        map.put("transaction_count", row.getTransactionCount());
        map.put("first_transaction", row.getFirstTransaction() != null ? row.getFirstTransaction().toString() : null);
        map.put("last_transaction", row.getLastTransaction() != null ? row.getLastTransaction().toString() : null);
        return map;
    }

    private static Map<String, Object> toMap(String labelKey, SpendingGroup row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(labelKey, row.getLabel());
        map.put("currency", row.getCurrency());
        map.put("total_spent", money(row.getTotal()));
        map.put("transaction_count", row.getTransactionCount());
        return map;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double money(BigDecimal value) {
        return nullToZero(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // Inclusive YYYY-MM-DD dates from the model, converted to a half-open [from, to) instant range in REPORTING_ZONE
    record DateRange(Instant from, Instant to, String startLabel, String endLabel) {
        static DateRange parse(String startDate, String endDate) {
            LocalDate start = parseDate(startDate, "start_date");
            LocalDate end = parseDate(endDate, "end_date");
            if (start != null && end != null && start.isAfter(end)) {
                throw new IllegalArgumentException("start_date " + start + " is after end_date " + end + ".");
            }
            return new DateRange(
                    start != null ? start.atStartOfDay(REPORTING_ZONE).toInstant() : Instant.EPOCH,
                    end != null ? end.plusDays(1).atStartOfDay(REPORTING_ZONE).toInstant()
                            : Instant.now().plusSeconds(86_400),
                    start != null ? start.toString() : "all",
                    end != null ? end.toString() : "today");
        }

        private static LocalDate parseDate(String value, String name) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(name + " must be a date in YYYY-MM-DD format, got '" + value + "'.");
            }
        }
    }
}
