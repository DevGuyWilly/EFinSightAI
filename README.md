# PersonaFinSight (e-finsight)

An AI-powered financial insights platform that analyzes your banking transactions using Retrieval Augmented Generation (RAG) and multi-agent AI systems to provide personalized financial advice.

## 🎯 Overview

PersonaFinSight connects to your bank accounts via TrueLayer, ingests your transaction history, and uses advanced AI to provide:
- **Spending Analysis** - Identify where you're spending the most money
- **Budget Recommendations** - Get personalized budget plans based on your spending patterns
- **Investment Advice** - Receive investment recommendations tailored to your financial situation

## ✨ Key Features

- **🔐 Secure Bank Connection** - OAuth 2.0 integration with TrueLayer for secure bank account access
- **📊 Transaction Ingestion** - Automatically fetches and stores last 90 days of transactions
- **🧠 RAG Pipeline** - Uses embeddings and vector search to retrieve relevant transaction context
- **🤖 Multi-Agent System** - Three specialized AI agents work together:
  - **Spending Analyst** - Analyzes spending patterns and trends
  - **Budget Planner** - Creates personalized budget recommendations
  - **Investment Advisor** - Provides investment advice based on financial data
- **💬 Natural Language Queries** - Ask questions in plain English and get comprehensive financial plans

## 🛠️ Tech Stack

- **Backend**: Spring Boot 3.x
- **Database**: PostgreSQL (Google Cloud SQL)
- **Authentication**: JWT-based auth + TrueLayer OAuth
- **AI/ML**:
  - **LLM**: Google Gemini via Google ADK agents (model set by `llm.chat-model`)
  - **Embeddings**: Gemini Embedding 001
  - **Vector Store**: PostgreSQL with pgvector (exact cosine search in the database), or Google Vertex AI Vector Search when configured
- **API Integration**: TrueLayer Banking API

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven wrapper is included (`./mvnw`) — no separate Maven install needed
- A PostgreSQL database with the [pgvector](https://github.com/pgvector/pgvector) extension available (Supabase and Cloud SQL include it; locally: `brew install pgvector`) — the app runs `CREATE EXTENSION IF NOT EXISTS vector` at startup and won't boot without it. See [`scripts/setup-cloud-sql.sh`](scripts/setup-cloud-sql.sh) to provision one on Google Cloud SQL
- TrueLayer Developer Account
- Gemini API access — two separate credentials are needed, for two separate code paths:
  - `GOOGLE_API_KEY` (or `GEMINI_API_KEY`) as an **environment variable** — used by the ADK agents' model resolution, independent of `application.properties`
  - `llm.api-key` in `application.properties` — used by the embedding HTTP client path (Gemini or OpenAI, per `llm.provider`)
- (Optional) A Google Cloud project with a Vertex AI Vector Search index deployed, for production-grade vector search — the app uses pgvector search in PostgreSQL if this isn't configured
- (Optional, for testing the bank-connection OAuth flow on a real phone) [ngrok](https://ngrok.com/) or similar

### Configuration

1. **App config** — copy `src/main/resources/application.properties.example` to `application.properties` (gitignored, safe to put real values in) and fill in your database, TrueLayer, and LLM settings.
2. **Environment variables** — create a `.env` file (gitignored) at the project root with `GOOGLE_API_KEY=<your-key>`. This must be loaded into your shell before running the app:
   ```bash
   set -a; source .env; set +a
   ```
3. **Database** — run `./scripts/setup-cloud-sql.sh` to provision a Cloud SQL Postgres instance and populate `application.properties` automatically, or point `spring.datasource.*` at any Postgres instance yourself.
4. **TrueLayer** — get credentials from the [TrueLayer Console](https://console.truelayer.com/), set `truelayer.client-id` / `truelayer.client-secret`, and register your exact `truelayer.redirect-uri` value in the Console's Redirect URIs allowlist. Note the bank-connection flow itself lives at `/auth/connect-bank` and `/callback` (not under `/api/auth`).
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

## 📡 API Endpoints

### Authentication

- **POST** `/api/auth/signup` - Create new user account
- **POST** `/api/auth/login` - Login and get JWT token

### Bank Connection

- **GET** `/api/auth/connect-bank` - Initiate TrueLayer OAuth flow
- **GET** `/callback` - OAuth callback handler

### Transactions

- **POST** `/api/transactions/ingest` - Fetch and store transactions (last 90 days)
- **GET** `/api/transactions` - Get all user transactions
- **GET** `/api/transactions/count` - Get transaction count

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

## 🔄 How It Works

1. **Connect Bank Account**
   - User initiates OAuth flow via `/api/auth/connect-bank`
   - TrueLayer redirects back with authorization code
   - System exchanges code for access/refresh tokens

2. **Ingest Transactions**
   - Call `/api/transactions/ingest` to fetch last 90 days of transactions
   - Transactions are stored in PostgreSQL
   - Each transaction is chunked and embedded
   - Embeddings stored for vector search

3. **Generate Financial Plan**
   - User asks a question via `/api/plan`
   - RAG pipeline retrieves relevant transaction context
   - Multiple AI agents analyze the data:
     - **Spending Analyst** identifies spending patterns
     - **Budget Planner** creates budget recommendations
     - **Investment Advisor** provides investment advice
   - Coordinator merges all insights into comprehensive plan
   - Response includes plan + citations (source transactions)

## 🧪 Example Queries

- "Where am I spending the most money?"
- "How can I save more money?"
- "What's my spending pattern for groceries?"
- "Create a budget plan for me"
- "Should I invest in stocks?"

## 🔧 Development

### Building

```bash
mvn clean package
```

## 📝 Configuration Files

- `application.properties` - Main configuration (database, TrueLayer, LLM)
- `pom.xml` - Maven dependencies

## 🤝 Contributing

This is a personal project, but suggestions and improvements are welcome!

## 📄 License

Private project - All rights reserved

## 🙏 Acknowledgments

- **TrueLayer** - Banking API integration
- **Google Gemini** - LLM and embeddings
- **Spring Boot** - Application framework

---

**Built with ❤️ for intelligent financial insights**
