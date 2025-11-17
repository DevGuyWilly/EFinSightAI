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

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                        │
│  /api/plan, /api/transactions, /api/auth/connect-bank   │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              Multi-Agent System                          │
│  SpendingAnalyst → BudgetPlanner → InvestmentAdvisor     │
│              (Coordinated by AgentCoordinatorService)   │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                  RAG Pipeline                            │
│  Query → Embedding → Vector Search → Context Retrieval  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              LLM Integration (Gemini/OpenAI)            │
│  Chat Completion with Retry Logic & Error Handling      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              Data Layer                                  │
│  PostgreSQL (Cloud SQL) - Transactions, Chunks, Users  │
└─────────────────────────────────────────────────────────┘
```

## 🛠️ Tech Stack

- **Backend**: Spring Boot 3.x
- **Database**: PostgreSQL (Google Cloud SQL)
- **Authentication**: JWT-based auth + TrueLayer OAuth
- **AI/ML**:
  - **LLM**: Google Gemini 2.5 Flash (with OpenAI support)
  - **Embeddings**: Gemini Embedding 001
  - **Vector Store**: PostgreSQL-based (cosine similarity)
- **API Integration**: TrueLayer Banking API

## 📁 Project Structure

```
src/main/java/ai/efinsight/e_finsight/
├── agent/              # AI Agents & Coordinator
│   ├── AgentCoordinatorService    # Orchestrates multiple agents
│   ├── SpendingAnalyst            # Analyzes spending patterns
│   ├── BudgetPlanner              # Creates budget recommendations
│   ├── InvestmentAdvisor          # Provides investment advice
│   └── LLMAgent                   # LLM client wrapper
│
├── rag/                # RAG Pipeline
│   ├── RagService                 # Main RAG retrieval service
│   ├── EmbeddingService            # Generates embeddings (Gemini/OpenAI)
│   ├── VectorStoreService          # Vector similarity search
│   └── ChunkingService             # Text chunking for transactions
│
├── llm/                # LLM Integration
│   ├── LLMClient                   # Chat completion client
│   └── LLMConfig                    # LLM configuration
│
├── service/            # Business Logic
│   ├── TransactionService          # Transaction ingestion & processing
│   ├── TrueLayerApiService         # TrueLayer API client
│   └── TrueLayerAuthService        # TrueLayer OAuth handling
│
├── controller/         # REST Controllers
│   ├── PlanController              # POST /api/plan
│   ├── TransactionsController      # Transaction endpoints
│   ├── AuthController              # User authentication
│   └── TrueLayerAuthController     # Bank connection flow
│
├── model/              # JPA Entities
│   ├── Transaction                 # Transaction data
│   ├── TransactionChunk            # Chunked transaction text + embeddings
│   ├── User                        # User accounts
│   └── UserToken                   # TrueLayer OAuth tokens
│
├── repository/         # JPA Repositories
├── dto/                # Data Transfer Objects
├── config/             # Configuration classes
├── security/           # JWT authentication
└── exception/          # Global exception handling
```

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL database (or Google Cloud SQL)
- TrueLayer Developer Account
- Gemini API Key (or OpenAI API Key)

### Configuration

1. **Database Setup**
   - Update `application.properties` with your PostgreSQL connection details
   - Or set environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

2. **TrueLayer Setup**
   - Get credentials from [TrueLayer Console](https://console.truelayer.com/)
   - Update `truelayer.client-id` and `truelayer.client-secret` in `application.properties`
   - Set redirect URI: `http://localhost:8080/callback`

3. **LLM Configuration**
   - Set `llm.provider=gemini` or `llm.provider=openai`
   - Set `llm.api-key` with your API key
   - Configure model names: `llm.chat-model` and `llm.embedding-model`

### Running the Application

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or run the JAR
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

- **POST** `/api/plan` - Generate comprehensive financial plan
  ```json
  {
    "question": "Where am I spending the most money?"
  }
  ```
  
  **Response:**
  ```json
  {
    "success": true,
    "plan": "# Financial Plan\n\n## Spending Analysis\n...",
    "citations": [
      "Transaction ID: 1 - Transaction: LOTHIAN BUSES...",
      ...
    ],
    "question": "Where am I spending the most money?"
  }
  ```

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

### Testing

The application uses Spring Boot's embedded testing. Key components can be tested via:
- Unit tests for services
- Integration tests for controllers
- Manual testing via API endpoints

## 📝 Configuration Files

- `application.properties` - Main configuration (database, TrueLayer, LLM)
- `pom.xml` - Maven dependencies

## 🔐 Security

- JWT-based authentication for API endpoints
- OAuth 2.0 for bank account access
- Secure token storage in database
- Spring Security for endpoint protection

## 📊 Database Schema

- **users** - User accounts
- **user_tokens** - TrueLayer OAuth tokens
- **transactions** - Transaction data
- **transaction_chunks** - Chunked transaction text with embeddings

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
