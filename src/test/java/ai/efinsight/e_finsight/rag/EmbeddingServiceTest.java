package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.llm.LLMConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Stands up a local fake of Gemini's batchEmbedContents endpoint and inspects what EmbeddingService sends to it
class EmbeddingServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<List<Map<String, Object>>> batches = new CopyOnWriteArrayList<>();
    private final List<String> requestUris = new CopyOnWriteArrayList<>();
    private final List<String> apiKeyHeaders = new CopyOnWriteArrayList<>();
    // Number of upcoming calls to answer with this status before succeeding
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private volatile int failureStatus = 429;

    private HttpServer server;
    private EmbeddingService embeddingService;

    @BeforeEach
    void startFakeGemini() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-embedding-001:batchEmbedContents", this::handle);
        server.start();

        LLMConfig config = new LLMConfig();
        config.setProvider("gemini");
        config.setApiKey("test-key");
        config.setEmbeddingModel("gemini-embedding-001");
        config.setGeminiApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta");
        embeddingService = new EmbeddingService(config);
        embeddingService.retryBaseDelayMs = 1;
    }

    @AfterEach
    void stopFakeGemini() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private void handle(HttpExchange exchange) throws IOException {
        requestUris.add(exchange.getRequestURI().toString());
        apiKeyHeaders.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
        Map<String, Object> body = objectMapper.readValue(exchange.getRequestBody(), Map.class);

        if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            respond(exchange, failureStatus, "{\"error\":{\"code\":" + failureStatus + "}}");
            return;
        }

        List<Map<String, Object>> requests = (List<Map<String, Object>>) body.get("requests");
        batches.add(requests);
        // Embed each text as [index-within-batch, text length, 1] so the test can check ordering
        List<Map<String, Object>> embeddings = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> content = (Map<String, Object>) requests.get(i).get("content");
            String text = (String) ((List<Map<String, Object>>) content.get("parts")).get(0).get("text");
            embeddings.add(Map.of("values", List.of(i, text.length(), 1)));
        }
        respond(exchange, 200, objectMapper.writeValueAsString(Map.of("embeddings", embeddings)));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @Test
    void queryEmbeddingsUseRetrievalQueryTaskType() {
        float[] embedding = embeddingService.generateQueryEmbedding("Where do I spend the most?");

        assertThat(embedding).containsExactly(0f, 26f, 1f);
        assertThat(batches).singleElement().satisfies(batch ->
                assertThat(batch).singleElement().satisfies(request ->
                        assertThat(request.get("taskType")).isEqualTo("RETRIEVAL_QUERY")));
    }

    @Test
    void documentEmbeddingsUseRetrievalDocumentTaskType() {
        List<float[]> embeddings = embeddingService.generateEmbeddings(List.of("TESCO -40.00", "SALARY 2500.00"));

        assertThat(embeddings).hasSize(2);
        assertThat(batches).singleElement().satisfies(batch -> assertThat(batch).hasSize(2)
                .allSatisfy(request -> assertThat(request.get("taskType")).isEqualTo("RETRIEVAL_DOCUMENT")));
    }

    @Test
    void splitsLargeInputsIntoBatchesOfAtMost100AndKeepsOrder() {
        List<String> texts = IntStream.range(0, 250).mapToObj(i -> "x".repeat(i + 1)).toList();

        List<float[]> embeddings = embeddingService.generateEmbeddings(texts);

        assertThat(batches).extracting(List::size).containsExactly(100, 100, 50);
        assertThat(embeddings).hasSize(250);
        // Text i has length i + 1, echoed back as the second value, so order is preserved across batches
        for (int i = 0; i < 250; i++) {
            assertThat(embeddings.get(i)[1]).isEqualTo(i + 1f);
        }
    }

    @Test
    void sendsApiKeyInHeaderNotUrl() {
        embeddingService.generateEmbeddings(List.of("TESCO -40.00"));

        assertThat(apiKeyHeaders).containsOnly("test-key");
        assertThat(requestUris).noneMatch(uri -> uri.contains("test-key"));
    }

    @Test
    void retriesRateLimitedCallsThenSucceeds() {
        failuresRemaining.set(2);

        assertThat(embeddingService.generateEmbeddings(List.of("TESCO -40.00"))).hasSize(1);
        assertThat(requestUris).hasSize(3);
    }

    @Test
    void givesUpAfterThreeAttempts() {
        failuresRemaining.set(3);

        assertThatThrownBy(() -> embeddingService.generateEmbeddings(List.of("TESCO -40.00")))
                .hasMessageContaining("429");
        assertThat(requestUris).hasSize(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        failureStatus = 400;
        failuresRemaining.set(1);

        assertThatThrownBy(() -> embeddingService.generateEmbeddings(List.of("TESCO -40.00")))
                .hasMessageContaining("400");
        assertThat(requestUris).hasSize(1);
    }
}
