package ai.efinsight.e_finsight.controller;

import ai.efinsight.e_finsight.rag.EmbeddingService;
import ai.efinsight.e_finsight.rag.RagService;
import ai.efinsight.e_finsight.rag.RagService.RagContext;
import ai.efinsight.e_finsight.rag.VectorStoreService;
import ai.efinsight.e_finsight.rag.VectorStoreService.ChunkSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/test")
public class RagTestController {
    private static final Logger log = LoggerFactory.getLogger(RagTestController.class);

    private final RagService ragService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    public RagTestController(
            RagService ragService,
            VectorStoreService vectorStoreService,
            EmbeddingService embeddingService) {
        this.ragService = ragService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/retrieve")
    public ResponseEntity<?> testRetrieval(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            String query = (String) request.get("query");
            Integer topK = request.get("topK") != null ? (Integer) request.get("topK") : 5;

            if (query == null || query.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Query is required");
                return ResponseEntity.badRequest().body(error);
            }

            log.info("Testing RAG retrieval for query: '{}' (user: {}, topK: {})", query, userId, topK);

            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to generate embedding for query");
                return ResponseEntity.status(500).body(error);
            }

            List<ChunkSimilarity> results = vectorStoreService.searchSimilarWithScores(userId, queryEmbedding, topK);
            List<RagContext> contexts = results.stream()
                    .map(cs -> new RagContext(
                            cs.chunk.getChunkText(),
                            cs.chunk.getTransactionId(),
                            cs.chunk.getId(),
                            "transaction"
                    ))
                    .collect(java.util.stream.Collectors.toList());

            String contextString = ragService.buildContextString(contexts);

            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("topK", topK);
            response.put("retrievedCount", contexts.size());
            response.put("queryEmbeddingDimension", queryEmbedding.length);
            response.put("contexts", buildContextDetailsWithScores(results));
            response.put("formattedContext", contextString);
            response.put("contextLength", contextString.length());
            
            if (!results.isEmpty()) {
                Map<String, Object> similarityStats = new HashMap<>();
                similarityStats.put("highest", results.get(0).similarity);
                similarityStats.put("lowest", results.get(results.size() - 1).similarity);
                similarityStats.put("average", results.stream()
                    .mapToDouble(cs -> cs.similarity)
                    .average()
                    .orElse(0.0));
                response.put("similarityStats", similarityStats);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing RAG retrieval", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to test RAG retrieval: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getRagStats(Authentication authentication) {
        try {
            Long userId = (Long) authentication.getPrincipal();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("userId", userId);
            stats.put("message", "RAG statistics endpoint - check your transaction chunks count");
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting RAG stats", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get RAG stats: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    private List<Map<String, Object>> buildContextDetailsWithScores(List<ChunkSimilarity> results) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            ChunkSimilarity cs = results.get(i);
            Map<String, Object> detail = new HashMap<>();
            detail.put("rank", i + 1);
            detail.put("similarity", Math.round(cs.similarity * 10000.0) / 10000.0);
            detail.put("similarityPercent", Math.round(cs.similarity * 10000.0) / 100.0);
            detail.put("text", cs.chunk.getChunkText());
            detail.put("sourceId", cs.chunk.getTransactionId());
            detail.put("chunkId", cs.chunk.getId());
            detail.put("source", "transaction");
            detail.put("textLength", cs.chunk.getChunkText().length());
            details.add(detail);
        }
        return details;
    }
}


