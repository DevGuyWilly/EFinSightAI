package ai.efinsight.e_finsight.service;

import ai.efinsight.e_finsight.dto.TrueLayerAccountDto;
import ai.efinsight.e_finsight.dto.TrueLayerTransactionDto;
import ai.efinsight.e_finsight.llm.LLMConfig;
import ai.efinsight.e_finsight.model.Transaction;
import ai.efinsight.e_finsight.rag.ChunkingService;
import ai.efinsight.e_finsight.rag.EmbeddingService;
import ai.efinsight.e_finsight.rag.VectorStoreService;
import ai.efinsight.e_finsight.repository.TransactionChunkRepository;
import ai.efinsight.e_finsight.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

// Real ingestion pipeline (H2 + chunking + embedding + chunk storage) with TrueLayer mocked and Gemini faked over
// HTTP. Runs WITHOUT a test-managed transaction so the service's own commit boundaries are what gets tested.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ingestion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARCHAR",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "llm.provider=gemini",
        "llm.api-key=test-key",
        "llm.embedding-model=gemini-embedding-001"
})
@Import({TransactionService.class, ChunkingService.class, EmbeddingService.class, VectorStoreService.class, LLMConfig.class})
class TransactionServiceIngestionTest {
    private static final long USER = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Sizes of the batchEmbedContents calls received, in order
    private static final List<Integer> BATCH_SIZES = new CopyOnWriteArrayList<>();
    // Any text containing this marker makes its whole batch fail with a non-retryable 400
    private static volatile String failMarker = null;
    private static final HttpServer GEMINI = startFakeGemini();

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionChunkRepository chunkRepository;

    @MockitoBean
    private TrueLayerApiService apiService;

    @DynamicPropertySource
    static void geminiUrl(DynamicPropertyRegistry registry) {
        registry.add("llm.gemini-api-url", () -> "http://127.0.0.1:" + GEMINI.getAddress().getPort() + "/v1beta");
    }

    @AfterAll
    static void stopFakeGemini() {
        GEMINI.stop(0);
    }

    @BeforeEach
    void reset() {
        chunkRepository.deleteAll();
        transactionRepository.deleteAll();
        BATCH_SIZES.clear();
        failMarker = null;
        when(apiService.getAccounts("1")).thenReturn(List.of(account("A"), account("B")));
    }

    @Test
    void storesNewTransactionsOnceAndEmbedsThemInBatchesOf100() {
        List<TrueLayerTransactionDto> accountA = transactions("a", 150);
        List<TrueLayerTransactionDto> accountB = new ArrayList<>(transactions("b", 100));
        accountB.add(accountA.get(0)); // returned by both accounts: must be stored once
        givenAccountTransactions("A", accountA);
        givenAccountTransactions("B", accountB);
        storeExisting("a1"); // ingested previously: must not be stored or embedded again

        int ingested = transactionService.ingestTransactions(USER);

        assertThat(ingested).isEqualTo(249);
        assertThat(transactionRepository.countByUserId(USER)).isEqualTo(250);
        assertThat(BATCH_SIZES).containsExactly(100, 100, 49);
        assertThat(chunkRepository.count()).isEqualTo(249);
        assertThat(transactionRepository.findUnchunkedByUserId(USER)).isEmpty();
    }

    @Test
    void failedGroupStaysUnchunkedWhileOtherGroupsCommitAndCanBeRetried() {
        List<TrueLayerTransactionDto> accountA = transactions("a", 150);
        accountA.get(120).setDescription("FAIL ME");
        givenAccountTransactions("A", accountA);
        givenAccountTransactions("B", List.of());
        failMarker = "FAIL";

        int ingested = transactionService.ingestTransactions(USER);

        assertThat(ingested).isEqualTo(150);
        assertThat(chunkRepository.count()).isEqualTo(100);
        assertThat(transactionRepository.findUnchunkedByUserId(USER)).hasSize(50);

        failMarker = null;
        assertThat(transactionService.processUnprocessedTransactions(USER)).isEqualTo(50);
        assertThat(chunkRepository.count()).isEqualTo(150);
        assertThat(transactionRepository.findUnchunkedByUserId(USER)).isEmpty();
    }

    @Test
    void reingestingTheSameDataStoresAndEmbedsNothingNew() {
        givenAccountTransactions("A", transactions("a", 10));
        givenAccountTransactions("B", List.of());
        transactionService.ingestTransactions(USER);
        BATCH_SIZES.clear();

        assertThat(transactionService.ingestTransactions(USER)).isZero();
        assertThat(BATCH_SIZES).isEmpty();
        assertThat(chunkRepository.count()).isEqualTo(10);
    }

    @Test
    void reprocessReplacesChunksWithoutDuplicating() {
        givenAccountTransactions("A", transactions("a", 10));
        givenAccountTransactions("B", List.of());
        transactionService.ingestTransactions(USER);

        assertThat(transactionService.reprocessAllTransactions(USER)).isEqualTo(10);
        assertThat(chunkRepository.count()).isEqualTo(10);
        assertThat(transactionRepository.findUnchunkedByUserId(USER)).isEmpty();
    }

    private void givenAccountTransactions(String accountId, List<TrueLayerTransactionDto> transactions) {
        when(apiService.getAccountTransactions(eq("1"), eq(accountId), anyString(), isNull())).thenReturn(transactions);
    }

    private void storeExisting(String transactionId) {
        Transaction transaction = new Transaction();
        transaction.setUserId(USER);
        transaction.setTransactionId(transactionId);
        transaction.setAccountId("A");
        transaction.setChunked(true);
        transactionRepository.save(transaction);
    }

    private static TrueLayerAccountDto account(String id) {
        TrueLayerAccountDto account = new TrueLayerAccountDto();
        account.setAccountId(id);
        return account;
    }

    private static List<TrueLayerTransactionDto> transactions(String prefix, int count) {
        return new ArrayList<>(IntStream.range(0, count).mapToObj(i -> {
            TrueLayerTransactionDto dto = new TrueLayerTransactionDto();
            dto.setTransactionId(prefix + i);
            dto.setTimestamp("2026-07-01T09:00:00Z");
            dto.setDescription("MERCHANT " + prefix + i);
            dto.setAmount(new BigDecimal("-" + (i + 1) + ".00"));
            dto.setCurrency("GBP");
            dto.setTransactionCategory("PURCHASE");
            return dto;
        }).toList());
    }

    @SuppressWarnings("unchecked")
    private static void handle(HttpExchange exchange) throws IOException {
        Map<String, Object> body = OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class);
        List<Map<String, Object>> requests = (List<Map<String, Object>>) body.get("requests");
        String marker = failMarker;
        boolean fail = marker != null && requests.stream().anyMatch(r -> r.toString().contains(marker));
        if (fail) {
            respond(exchange, 400, "{\"error\":{\"code\":400}}");
            return;
        }
        BATCH_SIZES.add(requests.size());
        List<Map<String, Object>> embeddings = requests.stream()
                .map(r -> Map.<String, Object>of("values", List.of(0.1, 0.2, 0.3)))
                .toList();
        respond(exchange, 200, OBJECT_MAPPER.writeValueAsString(Map.of("embeddings", embeddings)));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static HttpServer startFakeGemini() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1beta/models/gemini-embedding-001:batchEmbedContents",
                    TransactionServiceIngestionTest::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
