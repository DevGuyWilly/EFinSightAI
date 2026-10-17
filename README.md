# EFinSight API

Spring Boot backend for EFinSight: connects a UK bank through TrueLayer, stores 90 days of transactions, and answers questions about them with retrieval-augmented, multi-agent Gemini. The React frontend lives in [efinsight-web](https://github.com/DevGuyWilly/efinsight-web).

## Overview

The API connects to a bank account via TrueLayer, ingests the transaction history, and uses AI to provide:
- **Spending Analysis** - Identify where you're spending the most money
- **Budget Recommendations** - Get personalized budget plans based on your spending patterns
- **Investment Advice** - Receive investment recommendations tailored to your financial situation

## Key Features

- **Secure Bank Connection** - OAuth 2.0 integration with TrueLayer for secure bank account access
- **Transaction Ingestion** - Automatically fetches and stores last 90 days of transactions
- **RAG Pipeline** - Uses embeddings and vector search to retrieve relevant transaction context
- **Multi-Agent System** - Three specialized AI agents work together:
  - **Spending Analyst** - Analyzes spending patterns and trends
  - **Budget Planner** - Creates personalized budget recommendations
  - **Investment Advisor** - Provides investment advice based on financial data
- **Natural Language Queries** - Ask questions in plain English and get comprehensive financial plans

## Tech Stack

- **Backend**: Spring Boot 3.5, Java 21, Spring Security (stateless JWT), Spring Data JPA
- **Database**: PostgreSQL with pgvector, hosted on Supabase (any Postgres with pgvector works)
- **Authentication**: JWT login (24 h) + TrueLayer OAuth; TrueLayer tokens are encrypted at rest (AES-256-GCM)
- **Hosting**: Docker image on Render (`Dockerfile`, `render.yaml`)
- **AI/ML**:
  - **LLM**: Google Gemini via Google ADK agents (model set by `llm.chat-model`)
  - **Embeddings**: Gemini Embedding 001
  - **Vector Store**: PostgreSQL with pgvector (exact cosine search in the database), or Google Vertex AI Vector Search when `vertex.ai.project-id` is set
- **API Integration**: TrueLayer Banking API

## Getting Started

### Prerequisites

- Java 21+
- Maven wrapper is included (`./mvnw`) — no separate Maven install needed
- A PostgreSQL database with the [pgvector](https://github.com/pgvector/pgvector) extension available (Supabase and Cloud SQL include it; locally: `brew install pgvector`, on a Postgres version that supports it) — the app runs `CREATE EXTENSION IF NOT EXISTS vector` at startup and won't boot without it. See [`scripts/setup-cloud-sql.sh`](scripts/setup-cloud-sql.sh) to provision one on Google Cloud SQL
- TrueLayer Developer Account
- Gemini API access — two separate credentials are needed, for two separate code paths:
  - `GOOGLE_API_KEY` (or `GEMINI_API_KEY`) as an **environment variable** — used by the ADK agents' model resolution, independent of `application.properties`
  - `llm.api-key` in `application.properties` — used by the embedding HTTP client path (Gemini or OpenAI, per `llm.provider`)
- (Optional) A Google Cloud project with a Vertex AI Vector Search index deployed, for production-grade vector search — the app uses pgvector search in PostgreSQL if this isn't configured
- (Optional, for testing the bank-connection OAuth flow on a real phone) [ngrok](https://ngrok.com/) or similar

### Configuration

1. **App config** — copy `src/main/resources/application.properties.example` to `application.properties` (gitignored, safe to put real values in) and fill in your database, TrueLayer, and LLM settings.
2. **Environment variables** — create a `.env` file (gitignored) at the project root with `GOOGLE_API_KEY=<your-key>` and `TOKEN_ENCRYPTION_KEY=<secret>` (generate one with `openssl rand -base64 32`). `TOKEN_ENCRYPTION_KEY` encrypts stored TrueLayer tokens; the app won't start without it, every instance that shares the database (e.g. your laptop and Render, both on Supabase) needs the **same** value, and changing it makes stored tokens unreadable (users would have to reconnect their bank). Load the file into your shell before running the app (or add the variables to your IDE run configuration):
   ```bash
   set -a; source .env; set +a
   ```
3. **Database** — point `spring.datasource.*` at a Postgres instance (the example file reads `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSL_MODE`; for Supabase use the session pooler). Tables are created and updated by Hibernate (`ddl-auto=update`) and there are no migration scripts, so a new `NOT NULL` column needs a column default (or a manual `ALTER TABLE`), otherwise Hibernate skips it and queries fail. [`scripts/setup-cloud-sql.sh`](scripts/setup-cloud-sql.sh) is an optional helper for provisioning Google Cloud SQL instead.
4. **TrueLayer** — get credentials from the [TrueLayer Console](https://console.truelayer.com/), set `truelayer.client-id` / `truelayer.client-secret`, and register your exact `truelayer.redirect-uri` value in the Console's Redirect URIs allowlist. The redirect URI must be `<frontend origin>/callback`, because the browser reaches the API through the frontend's proxy (see [Deployment](#deployment)). The bank-connection flow itself lives at `/auth/connect-bank` and `/callback` (not under `/api/auth`).
5. **Vertex AI Vector Search** (optional) — set the `vertex.ai.*` properties (see comments in `application.properties.example`); requires Application Default Credentials (`gcloud auth application-default login`) available to the app at startup, or the whole app will fail to boot rather than just falling back.

### Running the Application

```bash
# Load environment variables (GOOGLE_API_KEY, etc.)
set -a; source .env; set +a

# Run the application
./mvnw spring-boot:run

# Or build and run the JAR
./mvnw clean package
java -jar target/e-finsight-*.jar
```

The application will start on `http://localhost:8080`

## API Endpoints

### Authentication

- **POST** `/api/auth/signup` - Create an account and return a JWT
- **POST** `/api/auth/login` - Log in and return a JWT (`token`, `userId`, `email`, `firstName`, `lastName`, `bankConnected`, `hideBalances`)
- **GET** `/api/me` - The signed-in user as the server has them now (`userId`, `email`, `firstName`, `lastName`, `bankConnected`, `hideBalances`, `createdAt`, `lastLoginAt`). The login response goes stale once the bank is connected or a preference changes on another device, so clients should refresh from here.
- **POST** `/api/users/change-password` - Change the password (`currentPassword`, `newPassword`)
- **POST** `/api/users/preferences` - Update preferences (`hideBalances`); omitted fields are left unchanged

A missing, expired or invalid token on any protected route is `401` with a `WWW-Authenticate: Bearer` header and a `{message, timestamp}` JSON body (previously Spring's default `403`).

### Bank Connection

- **POST** `/api/bank/connect-url` - Authenticated. Returns `{"url": "/auth/connect-bank?ticket=<ticket>"}`: a ticket valid for 60 seconds and redeemable once
- **GET** `/auth/connect-bank?ticket=<ticket>` - Start the TrueLayer OAuth flow. Opened by browser navigation, which can't carry an `Authorization` header, so it takes the ticket from the call above instead of the login JWT; redirects to TrueLayer with a signed, 15-minute `state`. A missing, invalid, expired or already-used ticket redirects to `/auth/error?message=invalid_token`. (The old `?token=<jwt>` form is no longer accepted.)
- **GET** `/callback` - OAuth callback: checks the `state`, exchanges the code, stores the tokens encrypted, then redirects to `/auth/success` or `/auth/error?message=…` (`invalid_state`, `token_exchange_failed`, …). Those two paths serve small fallback pages here; behind the frontend's proxy they land on the React routes of the same name

### Transactions

- **POST** `/api/transactions/ingest` - Fetch and store transactions (last 90 days). Slow (fetch, chunk, embed); transactions already stored are skipped
- **POST** `/api/transactions/reprocess` - Re-chunk and re-embed every stored transaction (rebuilds the search index)
- **GET** `/api/transactions` - All of the user's transactions, unpaged
- **GET** `/api/transactions/count` - Number of stored transactions

### Financial Planning

- **POST** `/api/plan` - Ask the advisor. Omit `conversationId` to start a new conversation; send it to ask a follow-up (the agents see the last 10 turns). Only successful answers are saved.
  ```json
  {
    "question": "What about last month?",
    "conversationId": 42
  }
  ```

  **Response** (errors: `400` blank/too long, `404` unknown or someone else's conversation, `503` the AI failed, all with `success: false` and `error`):
  ```json
  {
    "success": true,
    "question": "What about last month?",
    "summary": "…",
    "sections": { "spendingAnalysis": "…", "budgetRecommendations": null, "investmentAdvice": null },
    "citations": [{ "transactionId": 1, "merchant": "TESCO", "amount": "-12.40", "currency": "GBP", "category": "PURCHASE", "date": "…", "description": "…" }],
    "agentResponses": { "spending_analysis": "…" },
    "conversationId": 42,
    "conversationTitle": "Where am I spending the most money?"
  }
  ```

### Conversations

- **GET** `/api/conversations` - `{ "conversations": [{ id, title, createdAt, updatedAt, messageCount }] }`, most recently used first
- **GET** `/api/conversations/{id}` - `{ id, title, createdAt, updatedAt, messages: [{ id, role: "user" | "assistant", content, createdAt, response? }] }`; assistant messages carry the full answer in `response`
- **DELETE** `/api/conversations/{id}` - delete one conversation (`204`)
- **DELETE** `/api/conversations` - delete all of the user's conversations (`204`)

Conversations belong to one user: another user's id behaves exactly like a missing one (`404`).

## How It Works

1. **Connect a bank**
   - The client calls `POST /api/bank/connect-url` for a short-lived ticket, then navigates the browser to `/auth/connect-bank?ticket=…`
   - TrueLayer redirects back to `/callback` with an authorization code
   - The API exchanges the code for access and refresh tokens and stores them encrypted

2. **Ingest transactions**
   - `POST /api/transactions/ingest` fetches 90 days of transactions and stores them
   - Each transaction is chunked and embedded in batches (Gemini `batchEmbedContents`, 100 per call); a failed batch stays unchunked and is retried on the next ingest or reprocess

3. **Ask a question**
   - `POST /api/plan` rewrites a follow-up into a standalone question, retrieves the 15 closest transaction chunks (pgvector), and runs the ADK coordinator
   - The coordinator is an `LlmAgent` with three specialists as tools (spending analyst, budget planner, investment advisor), and decides which to call
   - The retrieved context, the last 10 conversation turns, today's date and the user id are placed in the ADK session state, which every agent's instruction reads. The specialists also have four SQL-backed tools (spending summary, spending by category, top merchants, monthly cashflow) so totals and aggregates are computed, not guessed from retrieved chunks
   - A run is capped at 120 s. The answer, per-specialist sections and the cited source transactions are returned, and successful answers are saved to the conversation

## Architecture

```
controller/   REST endpoints (auth, me, users, bank, transactions, plan, conversations, TrueLayer OAuth)
security/     JWT filter, 401 entry point, AES-GCM cipher for stored TrueLayer tokens
service/      TrueLayer API/OAuth, transaction ingestion, conversations, connect tickets
rag/          chunking, embeddings, pgvector store (Vertex store when configured), retrieval
adk/          coordinator + specialist agents, analytics tools, question rewriter
agent/        AgentCoordinatorService: ties retrieval, history and the coordinator together
model/        JPA entities: users, user_tokens, transactions, transaction_chunks, conversations, conversation_messages
```

Security notes:
- Stateless JWT (24 h, `jwt.expiration`). The bank-connect ticket (60 s, single use per instance) and the OAuth `state` (15 min) are signed tokens with a `purpose` claim, so neither can be used as a login token and a login token can't start or finish a bank connection
- `TOKEN_ENCRYPTION_KEY` is required at startup. Stored tokens from before encryption was added are encrypted on startup
- Every data query is scoped to the authenticated user; another user's conversation id is a 404
- No CORS configuration: the browser is expected to call the API through the frontend's origin (Vite proxy locally, Render rewrites in production)

## Example Queries

- "Where am I spending the most money?"
- "How can I save more money?"
- "What's my spending pattern for groceries?"
- "Create a budget plan for me"
- "Should I invest in stocks?"

## Development

RAG debug endpoints (`/api/rag/test/retrieve`, `/api/rag/test/stats`) only exist when `app.debug-endpoints.enabled=true`; they're off by default.

### Building

```bash
./mvnw clean package
```

### Tests

```bash
./mvnw test -Dtest='AccessControlTest,JwtUtilTest,ConnectTicketServiceTest,PlanControllerTest,ConversationControllerTest'
```

Most database tests run on in-memory H2; set `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`, `TEST_DB_DRIVER` and `TEST_DB_DIALECT` to run them on a real Postgres. The pgvector tests need `TEST_PGVECTOR_URL`. `EFinsightApplicationTests` boots the whole app against whatever datasource `application.properties` points to, so only run the full suite with a throwaway database.

## Deployment

Render builds the `Dockerfile` (the image is built from `application.properties.example`; all secrets come in as environment variables). [`render.yaml`](render.yaml) lists them: `DB_*`, `JWT_SECRET`, `TOKEN_ENCRYPTION_KEY`, `TRUELAYER_CLIENT_ID`, `TRUELAYER_CLIENT_SECRET`, `TRUELAYER_REDIRECT_URI`, `LLM_API_KEY`, `GOOGLE_API_KEY`, `LLM_CHAT_MODEL`. `SERVER_TOMCAT_USE_RELATIVE_REDIRECTS=true` keeps `/auth/success` redirects relative so the browser stays on the frontend's origin.

The frontend's static site proxies `/api/*`, `/callback` and `/auth/connect-bank` to this service, so `TRUELAYER_REDIRECT_URI` is `https://<frontend-site>/callback`. Every instance that shares a database must use the same `TOKEN_ENCRYPTION_KEY`. `render.yaml` defaults to the free plan (512 MB, sleeps after 15 minutes idle); the service has run on Starter.

## Configuration Files

- `application.properties` - Main configuration (gitignored; start from `application.properties.example`)
- `render.yaml`, `Dockerfile` - Deployment
- `pom.xml` - Maven dependencies

## License

Private project - All rights reserved
