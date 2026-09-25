package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.model.TransactionChunk;
import ai.efinsight.e_finsight.repository.TransactionChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final TransactionChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VertexAIVectorStoreService vertexAIVectorStore;

    public VectorStoreService(
            TransactionChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            @Autowired(required = false) Optional<VertexAIVectorStoreService> vertexAIVectorStore) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vertexAIVectorStore = vertexAIVectorStore.orElse(null);
        log.info("VectorStoreService initialized with Vertex AI Vector Search: {}", this.vertexAIVectorStore != null);
    }

    @Transactional
    public void storeChunk(Long userId, Long transactionId, String chunkText, float[] embedding, Integer chunkIndex) {
        
        // Create a new transaction chunk
        TransactionChunk chunk = new TransactionChunk();

        chunk.setUserId(userId);
        chunk.setTransactionId(transactionId);
        chunk.setChunkText(chunkText);

        // Convert float[] → String for PostgreSQL storage
        chunk.setEmbedding(embeddingService.embeddingToString(embedding));
        // Result: "[0.123,-0.456,0.789,...]" (JSON array as string)

        chunk.setChunkIndex(chunkIndex);
        
        // Save to PostgreSQL first to get the ID
        chunk = chunkRepository.save(chunk);
        

        // Not working yet - Store in Vertex AI Vector Search - Not working yet
        if (vertexAIVectorStore != null) {
            try {
                String datapointId = vertexAIVectorStore.upsertDatapoint(
                    userId, transactionId, chunk.getId(), chunkText, embedding);
                chunk.setVertexDatapointId(datapointId);
                chunkRepository.save(chunk); // Update with datapoint ID
                log.debug("Stored chunk {} in Vertex AI Vector Search (datapoint: {})", chunk.getId(), datapointId);
            } catch (Exception e) {
                log.error("Failed to store chunk in Vertex AI Vector Search, continuing with PostgreSQL only", e);
            }
        }
        
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
        List<ChunkSimilarity> results = searchSimilarWithScores(userId, queryEmbedding, topK);
        return results.stream().map(cs -> cs.chunk).collect(java.util.stream.Collectors.toList());
    }

    public List<ChunkSimilarity> searchSimilarWithScores(Long userId, float[] queryEmbedding, int topK) {
        // Use Vertex AI Vector Search if available -
        if (vertexAIVectorStore != null) {
            try {
                List<VertexAIVectorStoreService.VectorSearchResult> vertexResults = 
                    vertexAIVectorStore.findNeighbors(userId, queryEmbedding, topK);
                
                // Map Vertex AI results to TransactionChunks
                List<ChunkSimilarity> results = new ArrayList<>();
                for (VertexAIVectorStoreService.VectorSearchResult vertexResult : vertexResults) {
                    // Extract chunk ID from datapoint ID or metadata
                    String datapointId = vertexResult.datapointId;
                    Map<String, String> metadata = vertexResult.metadata;
                    
                    // Try to find chunk by datapoint ID first
                    TransactionChunk chunk = chunkRepository.findByVertexDatapointId(datapointId)
                        .orElseGet(() -> {
                            // Fallback: try to extract from metadata
                            if (metadata.containsKey("chunk_id")) {
                                Long chunkId = Long.parseLong(metadata.get("chunk_id"));
                                return chunkRepository.findById(chunkId).orElse(null);
                            }
                            return null;
                        });
                    
                    if (chunk != null) {
                        results.add(new ChunkSimilarity(chunk, vertexResult.similarity));
                    }
                }
                
                log.info("Found {} similar chunks via Vertex AI Vector Search for user: {} (top similarity: {})", 
                    results.size(), userId, 
                    results.isEmpty() ? 0.0 : results.get(0).similarity);
                return results;
            } catch (Exception e) {
                log.error("Error searching Vertex AI Vector Search, falling back to PostgreSQL", e);
                // Fall through to PostgreSQL search
            }
        }
        
        // Fallback: exact cosine search inside PostgreSQL via pgvector
        List<TransactionChunkRepository.ChunkMatch> matches = chunkRepository.findNearestByCosine(
                userId, embeddingService.embeddingToString(queryEmbedding), topK);

        Map<Long, TransactionChunk> chunksById = chunkRepository
                .findAllById(matches.stream().map(TransactionChunkRepository.ChunkMatch::getChunkId).toList())
                .stream()
                .collect(Collectors.toMap(TransactionChunk::getId, Function.identity()));

        List<ChunkSimilarity> results = new ArrayList<>();
        for (TransactionChunkRepository.ChunkMatch match : matches) {
            TransactionChunk chunk = chunksById.get(match.getChunkId());
            // Zero vectors have no direction, so pgvector reports NaN similarity; they carry no signal
            if (chunk != null && match.getSimilarity() != null && !match.getSimilarity().isNaN()) {
                results.add(new ChunkSimilarity(chunk, match.getSimilarity()));
            }
        }

        log.info("Found {} similar chunks via pgvector for user: {} (top similarity: {})",
            results.size(), userId,
            results.isEmpty() ? 0.0 : results.get(0).similarity);
        return results;
    }

    @Transactional
    public void deleteChunksByUserId(Long userId) {
        // Delete from Vertex AI Vector Search
        if (vertexAIVectorStore != null) {
            try {
                List<TransactionChunk> chunks = chunkRepository.findByUserId(userId);
                for (TransactionChunk chunk : chunks) {
                    if (chunk.getVertexDatapointId() != null) {
                        vertexAIVectorStore.deleteDatapoint(chunk.getVertexDatapointId());
                    }
                }
                log.debug("Deleted {} chunks from Vertex AI Vector Search for user: {}", chunks.size(), userId);
            } catch (Exception e) {
                log.error("Error deleting chunks from Vertex AI Vector Search", e);
            }
        }
        
        // Delete from PostgreSQL
        chunkRepository.deleteByUserId(userId);
        log.info("Deleted all chunks for user: {}", userId);
    }

    @Transactional
    public void deleteChunksByTransactionId(Long transactionId) {
        // Delete from Vertex AI Vector Search
        if (vertexAIVectorStore != null) {
            try {
                List<TransactionChunk> chunks = chunkRepository.findByTransactionId(transactionId);
                for (TransactionChunk chunk : chunks) {
                    if (chunk.getVertexDatapointId() != null) {
                        vertexAIVectorStore.deleteDatapoint(chunk.getVertexDatapointId());
                    }
                }
                log.debug("Deleted {} chunks from Vertex AI Vector Search for transaction: {}", chunks.size(), transactionId);
            } catch (Exception e) {
                log.error("Error deleting chunks from Vertex AI Vector Search", e);
            }
        }
        
        // Delete from PostgreSQL
        chunkRepository.deleteByTransactionId(transactionId);
        log.debug("Deleted chunks for transaction: {}", transactionId);
    }

    public static class ChunkSimilarity {
        public final TransactionChunk chunk;
        public final double similarity;

        public ChunkSimilarity(TransactionChunk chunk, double similarity) {
            this.chunk = chunk;
            this.similarity = similarity;
        }
    }
}

