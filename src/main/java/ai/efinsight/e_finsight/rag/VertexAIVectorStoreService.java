package ai.efinsight.e_finsight.rag;

import ai.efinsight.e_finsight.config.VertexAIConfig;
import com.google.cloud.aiplatform.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "vertex.ai.project-id", matchIfMissing = false)
public class VertexAIVectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(VertexAIVectorStoreService.class);

    private final VertexAIConfig config;
    private final IndexServiceClient indexServiceClient;
    private final MatchServiceClient matchServiceClient;

    public VertexAIVectorStoreService(VertexAIConfig config) throws IOException {
        this.config = config;
        
        // Validate configuration
        if (config.getProjectId() == null || config.getProjectId().isEmpty() || 
            config.getProjectId().equals("your-gcp-project-id") ||
            config.getIndexId() == null || config.getIndexId().isEmpty() ||
            config.getIndexEndpointId() == null || config.getIndexEndpointId().isEmpty()) {
            throw new IllegalStateException(
                "Vertex AI configuration is incomplete. Please set vertex.ai.project-id, " +
                "vertex.ai.index-id, and vertex.ai.index-endpoint-id in application.properties"
            );
        }
        
        try {
            String endpoint = String.format("%s-aiplatform.googleapis.com:443", config.getLocation());
            this.indexServiceClient = IndexServiceClient.create(
                IndexServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build()
            );
            this.matchServiceClient = MatchServiceClient.create(
                MatchServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build()
            );
            log.info("Initialized Vertex AI Vector Search service for project: {}, location: {}", 
                config.getProjectId(), config.getLocation());
        } catch (IOException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("credentials were not found") || 
                errorMsg.contains("Your default credentials were not found")) {
                throw new IllegalStateException(
                    "Google Cloud credentials not found. To fix this:\n" +
                    "1. Set environment variable: export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json\n" +
                    "2. OR run: gcloud auth application-default login\n" +
                    "3. OR comment out vertex.ai.* properties in application.properties to use PostgreSQL fallback\n" +
                    "See VERTEX_AI_QUICK_START.md for details.", e);
            }
            throw e;
        }
    }

    public String upsertDatapoint(Long userId, Long transactionId, Long chunkId, String chunkText, float[] embedding) {
        try {
            String datapointId = String.format("user_%d_tx_%d_chunk_%d", userId, transactionId, chunkId);
            
            IndexDatapoint.Builder datapointBuilder = IndexDatapoint.newBuilder()
                .setDatapointId(datapointId);

            // Add embedding vector (Vertex AI expects Float, not Double)
            List<Float> embeddingList = new ArrayList<>();
            for (float f : embedding) {
                embeddingList.add(f);
            }
            datapointBuilder.addAllFeatureVector(embeddingList);

            // Add metadata (restricts, allows filtering)
            Map<String, String> restricts = new HashMap<>();
            restricts.put("user_id", String.valueOf(userId));
            restricts.put("transaction_id", String.valueOf(transactionId));
            restricts.put("chunk_id", String.valueOf(chunkId));
            
            for (Map.Entry<String, String> entry : restricts.entrySet()) {
                IndexDatapoint.Restriction restriction = IndexDatapoint.Restriction.newBuilder()
                    .setNamespace(entry.getKey())
                    .addAllowList(entry.getValue())
                    .build();
                datapointBuilder.addRestricts(restriction);
            }

            // Note: We're using dense embeddings only, sparse embeddings require indices

            IndexDatapoint datapoint = datapointBuilder.build();

            UpsertDatapointsRequest request = UpsertDatapointsRequest.newBuilder()
                .setIndex(config.getIndexName())
                .addDatapoints(datapoint)
                .build();

            UpsertDatapointsResponse response = indexServiceClient.upsertDatapoints(request);
            if (response != null) {
                log.debug("Upserted datapoint {} to Vertex AI Vector Search", datapointId);
            }
            
            return datapointId;
        } catch (Exception e) {
            log.error("Error upserting datapoint to Vertex AI Vector Search", e);
            throw new RuntimeException("Failed to upsert datapoint to Vertex AI", e);
        }
    }

    public List<VectorSearchResult> findNeighbors(Long userId, float[] queryEmbedding, int topK) {
        try {
            // Convert float[] to List<Float> (Vertex AI expects Float)
            List<Float> queryVector = new ArrayList<>();
            for (float f : queryEmbedding) {
                queryVector.add(f);
            }

            // Build query restriction to filter by user_id
            // Try using IndexDatapoint.Restriction (may work for queries too)
            IndexDatapoint.Restriction userRestriction = IndexDatapoint.Restriction.newBuilder()
                .setNamespace("user_id")
                .addAllowList(String.valueOf(userId))
                .build();

            // Build the query with feature vector and restrictions
            // Based on Python SDK example: Query has a datapoint field with IndexDatapoint containing feature_vector
            // Structure: FindNeighborsRequest.Query -> setDatapoint(IndexDatapoint) -> addAllFeatureVector(List<Float>)
            
            // Create IndexDatapoint with feature vector
            IndexDatapoint.Builder datapointBuilder = IndexDatapoint.newBuilder();
            datapointBuilder.addAllFeatureVector(queryVector);
            
            // Add restrictions to the datapoint
            datapointBuilder.addRestricts(userRestriction);
            
            IndexDatapoint queryDatapoint = datapointBuilder.build();
            
            // Build the query with the datapoint
            FindNeighborsRequest.Query.Builder queryBuilder = FindNeighborsRequest.Query.newBuilder()
                .setNeighborCount(topK)
                .setDatapoint(queryDatapoint);
            
            FindNeighborsRequest.Query query = queryBuilder.build();

            FindNeighborsRequest request = FindNeighborsRequest.newBuilder()
                .setIndexEndpoint(config.getIndexEndpoint())
                .setDeployedIndexId(config.getIndexDeploymentId())
                .addQueries(query)
                .build();

            FindNeighborsResponse response = matchServiceClient.findNeighbors(request);
            
            List<VectorSearchResult> results = new ArrayList<>();
            if (response.getNearestNeighborsCount() > 0) {
                FindNeighborsResponse.NearestNeighbors nearestNeighbors = response.getNearestNeighbors(0);
                for (FindNeighborsResponse.Neighbor neighbor : nearestNeighbors.getNeighborsList()) {
                    // Get datapoint ID from the datapoint object
                    String datapointId = neighbor.getDatapoint().getDatapointId();
                    
                    double distance = neighbor.getDistance();
                    
                    // Convert distance to similarity
                    // For dot product: higher distance = higher similarity
                    // Dot product returns values where higher = more similar
                    // Normalize to 0-1 range for consistency
                    double similarity = Math.max(0.0, Math.min(1.0, (distance + 1.0) / 2.0));
                    
                    // Extract metadata from datapoint restricts if available
                    Map<String, String> metadata = new HashMap<>();
                    if (neighbor.hasDatapoint()) {
                        IndexDatapoint datapoint = neighbor.getDatapoint();
                        for (IndexDatapoint.Restriction restriction : datapoint.getRestrictsList()) {
                            if (restriction.getAllowListCount() > 0) {
                                metadata.put(restriction.getNamespace(), restriction.getAllowList(0));
                            }
                        }
                    }
                    
                    results.add(new VectorSearchResult(datapointId, similarity, metadata));
                }
            }

            log.info("Found {} neighbors for user: {} (top similarity: {})", 
                results.size(), userId,
                results.isEmpty() ? 0.0 : results.get(0).similarity);
            
            return results;
        } catch (Exception e) {
            log.error("Error finding neighbors in Vertex AI Vector Search", e);
            throw new RuntimeException("Failed to find neighbors in Vertex AI", e);
        }
    }

    public void deleteDatapoint(String datapointId) {
        try {
            RemoveDatapointsRequest request = RemoveDatapointsRequest.newBuilder()
                .setIndex(config.getIndexName())
                .addDatapointIds(datapointId)
                .build();

            indexServiceClient.removeDatapoints(request);
            log.debug("Deleted datapoint {} from Vertex AI Vector Search", datapointId);
        } catch (Exception e) {
            log.error("Error deleting datapoint from Vertex AI Vector Search", e);
            throw new RuntimeException("Failed to delete datapoint from Vertex AI", e);
        }
    }

    public void deleteDatapointsByUser(Long userId) {
        try {
            // Note: Vertex AI doesn't support bulk delete by metadata filter
            // We'll need to track datapoint IDs in PostgreSQL and delete them individually
            // This method should be called with a list of datapoint IDs
            log.warn("Bulk delete by user not directly supported. Use deleteDatapoint() with specific IDs.");
        } catch (Exception e) {
            log.error("Error deleting datapoints by user from Vertex AI Vector Search", e);
            throw new RuntimeException("Failed to delete datapoints by user from Vertex AI", e);
        }
    }

    public static class VectorSearchResult {
        public final String datapointId;
        public final double similarity;
        public final Map<String, String> metadata;

        public VectorSearchResult(String datapointId, double similarity, Map<String, String> metadata) {
            this.datapointId = datapointId;
            this.similarity = similarity;
            this.metadata = metadata;
        }
    }
}

