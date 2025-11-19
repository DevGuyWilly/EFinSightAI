# Vertex AI Vector Search Setup Guide

## Overview

This application uses **Vertex AI Vector Search** (formerly Matching Engine) as the dedicated vector database for RAG (Retrieval Augmented Generation). This provides:

- ✅ **Scalable vector search** - Handles millions of vectors efficiently
- ✅ **Fast similarity search** - Indexed search with sub-second latency
- ✅ **Managed service** - No infrastructure management needed
- ✅ **Production-ready** - Used by Google Cloud customers at scale

## Prerequisites

1. **Google Cloud Project** with billing enabled
2. **Vertex AI API** enabled (includes Vector Search/Matching Engine)
3. **Service Account** with Vertex AI permissions
4. **Index and Index Endpoint** created in Vertex AI Vector Search

## Step 1: Enable APIs

```bash
# Enable Vertex AI API (includes Vector Search/Matching Engine)
gcloud services enable aiplatform.googleapis.com

# Note: matchingengine.googleapis.com is deprecated/not needed
# Vector Search is included in the Vertex AI API
```

**If you get permission errors:**
- Ensure you have the **Project Editor** or **Owner** role
- Or ask your administrator to enable the API
- The API might already be enabled - check with:
  ```bash
  gcloud services list --enabled | grep aiplatform
  ```

## Step 2: Create Service Account

```bash
# Create service account
gcloud iam service-accounts create vertex-ai-sa \
    --display-name="Vertex AI Service Account"

# Grant permissions - Use AI Platform Developer role (recommended)
# This role has permissions to create indexes, endpoints, and query vectors
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:vertex-ai-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/aiplatform.developer"

# Alternative: Use AI Platform Admin if you need full control including deletion
# gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
#     --member="serviceAccount:vertex-ai-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
#     --role="roles/aiplatform.admin"

# Create and download key
gcloud iam service-accounts keys create vertex-ai-key.json \
    --iam-account=vertex-ai-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com
```

### Which Role to Use?

| Role | Permissions | Recommendation |
|------|-------------|----------------|
| **AI Platform Developer** | Create/read/update indexes, endpoints, query vectors | ✅ **Use this** - Perfect for Vector Search |
| **AI Platform Admin** | Full control including delete operations | Use only if you need to delete resources |
| **AI Platform Viewer** | Read-only access | ❌ **Don't use** - Cannot create or update |

**For Vector Search, use `roles/aiplatform.developer`** - it provides:
- ✅ Create and manage indexes
- ✅ Create and manage index endpoints  
- ✅ Deploy indexes to endpoints
- ✅ Upsert datapoints (store vectors)
- ✅ Query vectors (find neighbors)

See `VERTEX_AI_ROLES.md` for detailed role comparison.
```

## Step 3: Set Authentication

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/vertex-ai-key.json"
```

Or in `application.properties`:
```properties
# Set this environment variable or use gcloud auth application-default login
# GOOGLE_APPLICATION_CREDENTIALS=/path/to/vertex-ai-key.json
```

## Step 4: Create Vector Search Index

### Option A: Using Google Cloud Console

1. Go to [Vertex AI Vector Search](https://console.cloud.google.com/vertex-ai/matching-engine)
2. Click "Create Index"
3. Configure with these values:
   - **Display name**: `transaction-embeddings-index`
   - **Description**: `Vector search index for financial transaction embeddings using Gemini`
   - **Region**: `us-central1 (Iowa)` ✅
   - **GCS folder URI**: `gs://your-bucket-name/transaction-embeddings/` (create bucket first!)
   - **Algorithm type**: `Tree-AH algorithm` ✅
   - **Dimensions**: `768` ✅ (for Gemini embedding-001)
   - **Approximate neighbors count**: `10` (typical for RAG)
   - **Update method**: `Batch` (use Streaming only for real-time updates)
   - **Shard size**: `Medium` (good for 10K-1M vectors)
   - **Distance measure type**: `Dot product distance` ✅ (NOT Cosine!)
   - **Feature norm type**: `None` ✅ (Gemini embeddings are pre-normalized)
   - **Fraction leaf nodes to search**: `0.05` (default, good balance)
   - **Leaf node embedding count**: `1000` (default, good balance)
4. Click "Create"

**See `VERTEX_AI_INDEX_CONFIGURATION.md` for detailed explanations of each field.**

**Important:** Google strongly recommends **Dot Product** over Cosine Distance. Dot Product with normalized vectors is mathematically equivalent to cosine distance but offers better performance. Gemini embeddings are already normalized, so Dot Product is the correct choice.

### Option B: Using gcloud CLI

```bash
gcloud ai indexes create \
    --display-name="transaction-embeddings-index" \
    --metadata-file=index-metadata.json \
    --region=us-central1
```

Create `index-metadata.json`:
```json
{
  "contentsDeltaUri": "gs://your-bucket/index/",
  "config": {
    "dimensions": 768,
    "approximateNeighborsCount": 10,
    "distanceMeasureType": "DOT_PRODUCT",
    "algorithmConfig": {
      "treeAhConfig": {
        "leafNodeEmbeddingCount": 500,
        "leafNodesToSearchPercent": 10
      }
    }
  }
}
```

**Note:** `DOT_PRODUCT` is the recommended distance measure (not `COSINE_DISTANCE`). Dot Product with normalized vectors (like Gemini embeddings) is mathematically equivalent to cosine distance but offers better performance.

## Step 5: Create Index Endpoint

1. Go to [Vertex AI Vector Search Endpoints](https://console.cloud.google.com/vertex-ai/matching-engine/endpoints)
2. Click "Create Endpoint"
3. Configure:
   - **Endpoint Name**: `transaction-embeddings-endpoint`
   - **Region**: `us-central1`
4. Click "Create"

## Step 6: Deploy Index to Endpoint

1. In the endpoint details, click "Deploy Index"
2. Select your index
3. Configure:
   - **Deployment ID**: `transaction-embeddings-deployment`
   - **Min Replica Count**: `1`
   - **Max Replica Count**: `1`
4. Click "Deploy"

## Step 7: Get Resource IDs

After creating the index and endpoint, get the resource IDs:

1. **Index ID**: Found in the index URL or details page
   - Format: `projects/PROJECT_ID/locations/LOCATION/indexes/INDEX_ID`
   - Or just the numeric `INDEX_ID`

2. **Index Endpoint ID**: Found in the endpoint URL
   - Format: `projects/PROJECT_ID/locations/LOCATION/indexEndpoints/ENDPOINT_ID`
   - Or just the numeric `ENDPOINT_ID`

3. **Deployment ID**: The deployment name you used (e.g., `transaction-embeddings-deployment`)

## Step 8: Configure Application

Update `application.properties`:

```properties
# Vertex AI Vector Search Configuration
vertex.ai.project-id=your-gcp-project-id
vertex.ai.location=us-central1
vertex.ai.index-id=1234567890123456789
vertex.ai.index-endpoint-id=9876543210987654321
vertex.ai.index-deployment-id=transaction-embeddings-deployment
```

Or use environment variables:

```bash
export VERTEX_AI_PROJECT_ID="your-gcp-project-id"
export VERTEX_AI_LOCATION="us-central1"
export VERTEX_AI_INDEX_ID="1234567890123456789"
export VERTEX_AI_INDEX_ENDPOINT_ID="9876543210987654321"
export VERTEX_AI_INDEX_DEPLOYMENT_ID="transaction-embeddings-deployment"
```

## Step 9: Verify Setup

The application will:
- ✅ Automatically use Vertex AI Vector Search if configured
- ✅ Fall back to PostgreSQL if Vertex AI is not configured
- ✅ Log which vector store is being used

Check logs for:
```
VectorStoreService initialized with Vertex AI Vector Search: true
```

## Troubleshooting

### Error: "Index not found"
- Verify the index ID is correct
- Check that the index exists in the specified region
- Ensure the service account has `aiplatform.indexes.get` permission

### Error: "Endpoint not found"
- Verify the endpoint ID is correct
- Check that the endpoint is deployed
- Ensure the deployment ID matches

### Error: "Authentication failed"
- Verify `GOOGLE_APPLICATION_CREDENTIALS` is set
- Check service account key file permissions
- Ensure service account has required roles

### Fallback to PostgreSQL
- If Vertex AI is not configured, the app automatically uses PostgreSQL
- This is fine for development/testing
- For production, configure Vertex AI for better performance

## Cost Considerations

Vertex AI Vector Search pricing:
- **Index Storage**: ~$0.10 per GB/month
- **Queries**: ~$0.001 per query
- **Index Updates**: ~$0.10 per million updates

For small datasets (< 100K vectors), costs are minimal.

## Next Steps

1. **Test the integration**: Use the RAG test endpoint
   ```bash
   POST /api/rag/test/retrieve
   ```

2. **Monitor performance**: Check logs for search latency

3. **Scale as needed**: Increase replicas for higher query volume

---

**Note**: The application gracefully falls back to PostgreSQL if Vertex AI is not configured, so you can develop locally without Vertex AI setup.

