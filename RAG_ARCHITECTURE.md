# RAG (Retrieval Augmented Generation) Architecture - Detailed Explanation

## 🎯 Overview

RAG (Retrieval Augmented Generation) is a technique that enhances LLM responses by retrieving relevant context from your data before generating an answer. In this application, RAG allows AI agents to answer questions about your financial transactions by finding the most relevant transaction data first.

## 📊 The Complete RAG Flow

```
1. Data Ingestion → 2. Chunking → 3. Embedding → 4. Vector Storage → 5. Query → 6. Retrieval → 7. Context Building
```

### High-Level Flow:

1. **Transaction Ingestion** (`TransactionService.ingestTransactions`)
   - Fetches transactions from TrueLayer API
   - Saves to `transactions` table
   - Marks as `chunked = false`

2. **Chunking** (`ChunkingService.chunkTransaction`)
   - Converts transaction to text summary
   - Splits into chunks if too long (max 500 chars)

3. **Embedding** (`EmbeddingService.generateEmbedding`)
   - Converts text chunks to numerical vectors (embeddings)
   - Uses Gemini or OpenAI embedding API

4. **Vector Storage** (`VectorStoreService.storeChunks`)
   - Saves chunks + embeddings to `transaction_chunks` table
   - Marks transaction as `chunked = true`

5. **Query Time** (`RagService.retrieveContext`)
   - User asks a question (e.g., "Where am I spending the most?")
   - Converts query to embedding
   - Searches for similar transaction chunks using cosine similarity
   - Returns top K most relevant chunks

6. **Context Building** (`RagService.buildContextString`)
   - Formats retrieved chunks into readable context
   - Passes to LLM with the original question

---

## 📁 File-by-File Breakdown

### 1. `Transaction.java` - Data Model

**Purpose**: Represents a single bank transaction in the database.

#### Key Fields:

- **`id`** (Long): Primary key, auto-generated
- **`userId`** (Long): Links transaction to a specific user
- **`transactionId`** (String): Unique TrueLayer transaction ID
- **`accountId`** (String): Which bank account this transaction belongs to
- **`timestamp`** (Instant): When the transaction occurred
- **`description`** (String): Transaction description (e.g., "LOTHIAN BUSES")
- **`amount`** (BigDecimal): Transaction amount (positive or negative)
- **`currency`** (String): Currency code (e.g., "GBP")
- **`transactionCategory`** (String): Category (e.g., "Transport", "Food")
- **`merchantName`** (String): Name of merchant/business
- **`chunked`** (boolean): **Critical flag** - indicates if this transaction has been processed through the RAG pipeline

#### Key Method:

**`toTextSummary()`** (lines 177-191)
- **Purpose**: Converts transaction data into a searchable text format
- **Returns**: String like "Transaction: LOTHIAN BUSES at Edinburgh | Amount: -2.50 GBP | Category: Transport | Date: 2024-11-15T08:30:00Z"
- **Why**: This text is what gets chunked and embedded. The format ensures all important details are included for semantic search.

---

### 2. `TransactionChunk.java` - Chunk Storage Model

**Purpose**: Stores individual text chunks of transactions along with their vector embeddings.

#### Key Fields:

- **`id`** (Long): Primary key
- **`userId`** (Long): Links chunk to user (for security/isolation)
- **`transactionId`** (Long): Foreign key to the original `Transaction` record
- **`chunkText`** (String, TEXT): The actual text content of the chunk
- **`embedding`** (String, TEXT): **Critical** - Stores the vector embedding as a JSON array string (e.g., "[0.123, -0.456, 0.789, ...]")
  - Why String? PostgreSQL doesn't have native vector type in this setup, so we store as text
  - Contains ~768 float values (dimension depends on embedding model)
- **`chunkIndex`** (Integer): If a transaction is split into multiple chunks, this tracks which chunk number (0, 1, 2, ...)
- **`createdAt`** (LocalDateTime): When this chunk was created

#### Why This Structure?

- **Separation**: Transactions are stored separately from chunks (normalized database design)
- **Flexibility**: One transaction can have multiple chunks if it's long
- **Efficiency**: Only chunks with embeddings are stored (not all transactions need chunks)

---

### 3. `ChunkingService.java` - Text Chunking

**Purpose**: Converts transaction data into text chunks suitable for embedding.

#### Constants:

- **`MAX_CHUNK_SIZE = 500`** (line 14): Maximum characters per chunk
  - Why 500? Embedding models have token limits. 500 chars ≈ 125 tokens, leaving room for model overhead.

#### Methods:

**`chunkTransaction(Transaction transaction)`** (lines 16-32)
- **Input**: A single `Transaction` object
- **Process**:
  1. Calls `transaction.toTextSummary()` to get text
  2. If text ≤ 500 chars: returns single chunk
  3. If text > 500 chars: splits into multiple chunks of 500 chars each
- **Returns**: `List<String>` of text chunks
- **Example**: 
  - Input: Transaction with 1200-char description
  - Output: `["chunk1 (500 chars)", "chunk2 (500 chars)", "chunk3 (200 chars)"]`

**`chunkTransactions(List<Transaction> transactions)`** (lines 34-41)
- **Purpose**: Batch processing - chunks multiple transactions at once
- **Returns**: Flat list of all chunks from all transactions
- **Logging**: Logs how many chunks were created from how many transactions

---

### 4. `EmbeddingService.java` - Vector Generation

**Purpose**: Converts text into numerical vectors (embeddings) that capture semantic meaning.

#### How Embeddings Work:

- **Embedding**: A list of numbers (e.g., `[0.123, -0.456, 0.789, ...]`) that represents the "meaning" of text
- **Semantic Similarity**: Similar texts have similar embeddings (close in vector space)
- **Example**: "coffee shop" and "café" will have similar embeddings even though words differ

#### Key Fields:

- **`config`** (LLMConfig): Contains API keys, provider (Gemini/OpenAI), model names
- **`restTemplate`**: HTTP client for API calls
- **`objectMapper`**: JSON parser

#### Methods:

**`generateEmbedding(String text)`** (lines 29-34)
- **Purpose**: Generate embedding for a single text string
- **Process**: Wraps single text in list, calls `generateEmbeddings()`, returns first result
- **Returns**: `float[]` array (the embedding vector)

**`generateEmbeddings(List<String> texts)`** (lines 36-53)
- **Purpose**: Batch embedding generation (more efficient)
- **Process**:
  1. Checks provider (OpenAI or Gemini)
  2. Routes to appropriate method
  3. Handles errors gracefully
- **Returns**: `List<float[]>` - one embedding per input text

**`generateOpenAIEmbeddings(List<String> texts)`** (lines 55-95)
- **API Endpoint**: `https://api.openai.com/v1/embeddings`
- **Model**: `text-embedding-3-small` (default) or from config
- **Request Body**:
  ```json
  {
    "model": "text-embedding-3-small",
    "input": ["text1", "text2", ...]
  }
  ```
- **Response Parsing**: Extracts `data[].embedding` array from JSON
- **Returns**: List of float arrays (typically 1536 dimensions for OpenAI)

**`generateGeminiEmbeddings(List<String> texts)`** (lines 97-156)
- **API Endpoint**: `{baseUrl}/models/{model}:embedContent`
- **Model**: `gemini-embedding-001` (from config)
- **Request Body** (per text):
  ```json
  {
    "model": "models/gemini-embedding-001",
    "content": {
      "parts": [{"text": "your text here"}]
    }
  }
  ```
- **API Key**: Passed as query parameter `?key=YOUR_KEY`
- **Response Parsing**: Extracts `embedding.values` array
- **Error Handling**: If embedding fails, returns zero vector `new float[768]` (fallback)
- **Returns**: List of float arrays (typically 768 dimensions for Gemini)

**`embeddingToString(float[] embedding)`** (lines 158-169)
- **Purpose**: Converts embedding array to string for database storage
- **Format**: `"[0.123,-0.456,0.789,...]"`
- **Why**: PostgreSQL stores as TEXT, so we serialize the array

**`stringToEmbedding(String embeddingStr)`** (lines 171-187)
- **Purpose**: Converts stored string back to float array
- **Process**: Parses comma-separated values, converts to float array
- **Error Handling**: Returns `null` if parsing fails

---

### 5. `VectorStoreService.java` - Vector Storage & Search

**Purpose**: Stores embeddings and performs similarity search to find relevant chunks.

#### Key Dependencies:

- **`chunkRepository`**: Database access for `TransactionChunk` entities
- **`embeddingService`**: For string ↔ array conversions

#### Methods:

**`storeChunk(...)`** (lines 26-36)
- **Purpose**: Save a single chunk with its embedding to database
- **Parameters**:
  - `userId`: User who owns this chunk
  - `transactionId`: Links to original transaction
  - `chunkText`: The text content
  - `embedding`: The vector (float array)
  - `chunkIndex`: Position if multiple chunks per transaction
- **Process**:
  1. Creates `TransactionChunk` entity
  2. Converts embedding array to string using `embeddingService.embeddingToString()`
  3. Saves to database
- **Transaction**: `@Transactional` ensures atomicity

**`storeChunks(...)`** (lines 38-44)
- **Purpose**: Batch storage - saves multiple chunks at once
- **Process**: Loops through chunks and embeddings, calls `storeChunk()` for each
- **Logging**: Logs how many chunks were stored

**`searchSimilar(Long userId, float[] queryEmbedding, int topK)`** (lines 46-71)
- **Purpose**: **Core RAG retrieval** - finds most similar chunks to a query
- **Parameters**:
  - `userId`: Only search this user's chunks (security/isolation)
  - `queryEmbedding`: The embedding of the user's question
  - `topK`: How many results to return (e.g., 5, 10)
- **Process**:
  1. Fetches all chunks for user from database
  2. For each chunk:
     - Converts stored embedding string back to float array
     - Calculates cosine similarity between query and chunk embedding
     - Stores similarity score
  3. Sorts by similarity (highest first)
  4. Returns top K chunks
- **Returns**: `List<TransactionChunk>` ordered by relevance

**`cosineSimilarity(float[] a, float[] b)`** (lines 73-93)
- **Purpose**: Calculate how similar two embeddings are
- **Formula**: `cosine_similarity = dot_product(a, b) / (norm(a) * norm(b))`
- **Range**: -1.0 to 1.0
  - `1.0` = identical
  - `0.0` = orthogonal (unrelated)
  - `-1.0` = opposite
- **Why Cosine Similarity?**: 
  - Measures angle between vectors (semantic similarity)
  - Normalized (not affected by vector magnitude)
  - Works well for text embeddings

**Inner Class: `ChunkSimilarity`** (lines 95-103)
- **Purpose**: Helper class to pair chunks with their similarity scores
- **Fields**: `chunk` (the TransactionChunk), `similarity` (the score)
- **Used for**: Sorting chunks by relevance

---

### 6. `RagService.java` - RAG Orchestration

**Purpose**: High-level service that orchestrates the entire RAG retrieval process.

#### Key Dependencies:

- **`embeddingService`**: To embed user queries
- **`vectorStoreService`**: To search for similar chunks

#### Methods:

**`retrieveContext(Long userId, String query, int topK)`** (lines 24-51)
- **Purpose**: **Main RAG method** - retrieves relevant context for a user's question
- **Parameters**:
  - `userId`: Which user's data to search
  - `query`: User's question (e.g., "Where am I spending the most money?")
  - `topK`: How many relevant chunks to return (default: 5-10)
- **Process**:
  1. **Embed Query**: Converts user's question to embedding using `embeddingService.generateEmbedding(query)`
  2. **Search**: Calls `vectorStoreService.searchSimilar()` to find similar transaction chunks
  3. **Transform**: Converts `TransactionChunk` objects to `RagContext` objects
  4. **Return**: List of relevant contexts
- **Error Handling**: Returns empty list if embedding fails or search errors
- **Returns**: `List<RagContext>` - ready-to-use context for LLM

**`buildContextString(List<RagContext> contexts)`** (lines 53-65)
- **Purpose**: Formats retrieved contexts into a readable string for the LLM
- **Input**: List of `RagContext` objects
- **Output**: Formatted string like:
  ```
  Relevant transaction context:

  [1] Transaction: LOTHIAN BUSES at Edinburgh | Amount: -2.50 GBP | Category: Transport (Source: transaction, ID: 123)
  [2] Transaction: TESCO STORES at London | Amount: -45.30 GBP | Category: Groceries (Source: transaction, ID: 124)
  ...
  ```
- **Why**: LLMs work better with well-formatted context. This string gets prepended to the user's question.

**Inner Class: `RagContext`** (lines 67-79)
- **Purpose**: Data structure to hold retrieved context information
- **Fields**:
  - `text`: The actual chunk text
  - `sourceId`: ID of the original transaction
  - `chunkId`: ID of the chunk record
  - `source`: Source type (always "transaction" in this app)
- **Why**: Provides metadata about where context came from (useful for citations)

---

### 7. `TransactionService.java` - Integration Point

**Purpose**: Orchestrates the entire transaction ingestion and RAG processing pipeline.

#### Key Methods:

**`ingestTransactions(Long userId)`** (lines 43-90)
- **Purpose**: Main entry point for transaction ingestion
- **Process**:
  1. Fetches accounts from TrueLayer API
  2. For each account:
     - Fetches last 90 days of transactions
     - For each transaction:
       - Checks if already exists (deduplication)
       - Saves to database
       - **Calls `processTransactionChunks()`** to run RAG pipeline
       - Marks transaction as `chunked = true`
  3. Returns total count of ingested transactions
- **Error Handling**: Continues processing even if individual transactions fail

**`processTransactionChunks(Transaction transaction)`** (lines 133-149)
- **Purpose**: **RAG Processing Pipeline** - converts transaction to searchable chunks
- **Process**:
  1. **Chunk**: `chunkingService.chunkTransaction(transaction)` → List of text chunks
  2. **Embed**: `embeddingService.generateEmbeddings(chunks)` → List of embeddings
  3. **Store**: `vectorStoreService.storeChunks(...)` → Saves to database
- **Validation**: Ensures chunks and embeddings counts match
- **Called**: Automatically during ingestion, or manually via `processUnprocessedTransactions()`

**`processUnprocessedTransactions(Long userId)`** (lines 152-170)
- **Purpose**: Processes transactions that were saved but not yet chunked
- **Use Case**: If chunking failed during ingestion, this can retry
- **Process**: Finds all transactions with `chunked = false`, processes them

---

## 🔄 Complete Example Flow

### Scenario: User asks "Where am I spending the most money?"

#### Step 1: Query Processing (`RagService.retrieveContext`)
```java
String query = "Where am I spending the most money?";
Long userId = 123L;
int topK = 5;
```

#### Step 2: Embed Query (`EmbeddingService.generateEmbedding`)
```java
float[] queryEmbedding = embeddingService.generateEmbedding(query);
// Result: [0.123, -0.456, 0.789, ..., 0.234] (768 dimensions)
```

#### Step 3: Search Similar Chunks (`VectorStoreService.searchSimilar`)
```java
// Fetches all chunks for user 123
// Calculates cosine similarity for each:
// - Chunk 1: "TESCO STORES | Amount: -45.30 GBP" → similarity: 0.89
// - Chunk 2: "LOTHIAN BUSES | Amount: -2.50 GBP" → similarity: 0.45
// - Chunk 3: "AMAZON | Amount: -120.00 GBP" → similarity: 0.92
// - ...

// Sorts by similarity, returns top 5
```

#### Step 4: Build Context (`RagService.buildContextString`)
```
Relevant transaction context:

[1] Transaction: AMAZON at Online | Amount: -120.00 GBP | Category: Shopping (Source: transaction, ID: 456)
[2] Transaction: TESCO STORES at London | Amount: -45.30 GBP | Category: Groceries (Source: transaction, ID: 123)
[3] Transaction: RESTAURANT at Edinburgh | Amount: -35.50 GBP | Category: Dining (Source: transaction, ID: 789)
...
```

#### Step 5: Pass to LLM
```java
String prompt = buildContextString(contexts) + "\n\nUser Question: " + query;
String response = llmClient.chatCompletion(prompt);
// LLM generates answer using the retrieved context
```

---

## 🎯 Key Design Decisions

### Why Store Embeddings as Strings?
- **PostgreSQL Limitation**: Standard PostgreSQL doesn't have native vector types
- **Alternative**: Could use `pgvector` extension, but requires database setup
- **Current Approach**: Simple, works with any PostgreSQL, easy to debug

### Why Chunk Transactions?
- **Token Limits**: LLMs have context window limits (e.g., 32K tokens)
- **Precision**: Smaller chunks = more precise retrieval
- **Efficiency**: Don't need to embed entire transaction history for each query

### Why Cosine Similarity?
- **Semantic Matching**: Captures meaning, not just keyword matching
- **Normalized**: Works regardless of text length
- **Proven**: Industry standard for embedding similarity

### Why Top-K Retrieval?
- **Efficiency**: Don't need to process all chunks
- **Relevance**: Top K are most relevant, rest is noise
- **Cost**: Fewer chunks = less tokens sent to LLM = lower API costs

---

## 🔍 Database Schema

### `transactions` Table
```sql
- id (PK)
- user_id
- transaction_id (unique)
- account_id
- timestamp
- description
- amount
- currency
- transaction_category
- merchant_name
- chunked (boolean) ← Critical flag
```

### `transaction_chunks` Table
```sql
- id (PK)
- user_id
- transaction_id (FK → transactions.id)
- chunk_text (TEXT)
- embedding (TEXT) ← Vector stored as string
- chunk_index
- created_at
```

---

## 🚀 Performance Considerations

1. **Batch Embedding**: `generateEmbeddings()` processes multiple texts at once (more efficient)
2. **Lazy Processing**: Transactions are chunked on-demand, not all at once
3. **Indexing**: Consider adding database indexes on `user_id` and `transaction_id` for faster queries
4. **Caching**: Embeddings are computed once and stored (no recomputation needed)

---

## 🛠️ Future Improvements

1. **pgvector Extension**: Use native PostgreSQL vector type for faster similarity search
2. **Hybrid Search**: Combine semantic (embedding) + keyword search
3. **Chunk Optimization**: Better chunking strategies (sentence-aware, semantic boundaries)
4. **Embedding Caching**: Cache query embeddings for repeated questions
5. **Batch Retrieval**: Optimize similarity search with vectorized operations

---

This RAG implementation provides a solid foundation for semantic search over financial transactions, enabling AI agents to provide contextually relevant answers based on actual user data.

