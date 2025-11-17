package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.model.TransactionChunk;
import ai.efinsight.e_finsight.repository.TransactionChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class VectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final TransactionChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    public VectorStoreService(TransactionChunkRepository chunkRepository, EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public void storeChunk(Long userId, Long transactionId, String chunkText, float[] embedding, Integer chunkIndex) {
        TransactionChunk chunk = new TransactionChunk();
        chunk.setUserId(userId);
        chunk.setTransactionId(transactionId);
        chunk.setChunkText(chunkText);
        chunk.setEmbedding(embeddingService.embeddingToString(embedding));
        chunk.setChunkIndex(chunkIndex);
        
        chunkRepository.save(chunk);
        log.debug("Stored chunk for transaction: {} (user: {})", transactionId, userId);
    }

    @Transactional
    public void storeChunks(Long userId, Long transactionId, List<String> chunkTexts, List<float[]> embeddings) {
        for (int i = 0; i < chunkTexts.size(); i++) {
            storeChunk(userId, transactionId, chunkTexts.get(i), embeddings.get(i), i);
        }
        log.info("Stored {} chunks for transaction: {} (user: {})", chunkTexts.size(), transactionId, userId);
    }

    public List<TransactionChunk> searchSimilar(Long userId, float[] queryEmbedding, int topK) {
        List<TransactionChunk> allChunks = chunkRepository.findEmbeddedChunksByUserId(userId);
        
        if (allChunks.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChunkSimilarity> similarities = new ArrayList<>();
        for (TransactionChunk chunk : allChunks) {
            float[] chunkEmbedding = embeddingService.stringToEmbedding(chunk.getEmbedding());
            if (chunkEmbedding != null) {
                double similarity = cosineSimilarity(queryEmbedding, chunkEmbedding);
                similarities.add(new ChunkSimilarity(chunk, similarity));
            }
        }

        similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        List<TransactionChunk> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, similarities.size()); i++) {
            results.add(similarities.get(i).chunk);
        }

        log.info("Found {} similar chunks for user: {}", results.size(), userId);
        return results;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static class ChunkSimilarity {
        TransactionChunk chunk;
        double similarity;

        ChunkSimilarity(TransactionChunk chunk, double similarity) {
            this.chunk = chunk;
            this.similarity = similarity;
        }
    }
}

