package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.model.TransactionChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public RagService(EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public List<RagContext> retrieveContext(Long userId, String query, int topK) {
        log.info("Retrieving context for query: '{}' (user: {}, topK: {})", query, userId, topK);

        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding == null) {
                log.warn("Failed to generate embedding for query");
                return new ArrayList<>();
            }

            List<TransactionChunk> similarChunks = vectorStoreService.searchSimilar(userId, queryEmbedding, topK);

            List<RagContext> contexts = similarChunks.stream()
                    .map(chunk -> new RagContext(
                            chunk.getChunkText(),
                            chunk.getTransactionId(),
                            chunk.getId(),
                            "transaction"
                    ))
                    .collect(Collectors.toList());

            log.info("Retrieved {} relevant chunks for query", contexts.size());
            return contexts;
        } catch (Exception e) {
            log.error("Error retrieving context for query: {}", query, e);
            return new ArrayList<>();
        }
    }

    public String buildContextString(List<RagContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "No relevant transaction data found.";
        }

        StringBuilder sb = new StringBuilder("Relevant transaction context:\n\n");
        for (int i = 0; i < contexts.size(); i++) {
            RagContext ctx = contexts.get(i);
            sb.append(String.format("[%d] %s (Source: %s, ID: %d)\n", 
                i + 1, ctx.text, ctx.source, ctx.sourceId));
        }
        return sb.toString();
    }

    public static class RagContext {
        public final String text;
        public final Long sourceId;
        public final Long chunkId;
        public final String source;

        public RagContext(String text, Long sourceId, Long chunkId, String source) {
            this.text = text;
            this.sourceId = sourceId;
            this.chunkId = chunkId;
            this.source = source;
        }
    }
}

