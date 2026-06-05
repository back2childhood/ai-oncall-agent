# AI Oncall Agent

A Spring Boot + React RAG application for on-call developers. It ingests code, logs, runbooks, and design docs, chunks them by headings/chapters, embeds them with Spring AI, stores vectors in pgvector, stores metadata in Postgres, retrieves and reranks relevant chunks, then asks OpenAI for a grounded answer.

## Stack

- Backend: Java 21, Spring Boot, Spring AI, JPA, Flyway, Apache Tika
- Frontend: React + Vite
- Storage: PostgreSQL + pgvector
- AI: OpenAI chat and embedding models through Spring AI

## Run With Docker

```bash
cp .env.example .env
# edit .env and set OPENAI_API_KEY, OPENAI_BASE_URL, and model names
docker compose up --build
```

Open `http://localhost:3000`.

The compose stack starts:

- `postgres`: PostgreSQL with pgvector
- `backend`: Spring Boot API on `http://localhost:8080`
- `frontend`: Nginx-served React app on `http://localhost:3000`

## Run Locally

```bash
docker compose up -d postgres
export OPENAI_API_KEY=your_key_here
export OPENAI_BASE_URL=https://api.openai.com
export PROMETHEUS_ENABLED=true
export PROMETHEUS_BASE_URL=http://localhost:9090
export MCP_LOGS_ENABLED=true
export MCP_LOGS_ENDPOINT=http://localhost:8081/mcp/logs/query
cd backend
mvn spring-boot:run
```

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

## API

- `POST /api/documents` multipart upload with `file` and optional `sourceType`
- `GET /api/documents` list uploaded docs
- `POST /api/chat` with `{ "question": "...", "topK": 8 }`
- `GET /api/operations/sync` recent live alert/log sync runs
- `POST /api/operations/sync` manually pull Prometheus alerts and MCP logs now

## Live Alerts And Logs

The app can continuously read operational signals and index them into the same RAG store as uploaded documents.

- Prometheus alerts: `GET {PROMETHEUS_BASE_URL}/api/v1/alerts`
- MCP logs: `POST {MCP_LOGS_ENDPOINT}` with `{ "query", "since", "until", "limit" }`

Useful environment variables:

```bash
OPENAI_API_KEY=your_key_here
OPENAI_BASE_URL=https://api.openai.com
OPENAI_CHAT_MODEL=gpt-4.1-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
OPERATIONS_SYNC_ENABLED=true
OPERATIONS_SYNC_FIXED_DELAY_MS=60000
OPERATIONS_LOGS_LOOKBACK_MINUTES=15
PROMETHEUS_ENABLED=true
PROMETHEUS_BASE_URL=http://localhost:9090
MCP_LOGS_ENABLED=true
MCP_LOGS_ENDPOINT=http://localhost:8081/mcp/logs/query
MCP_LOGS_QUERY="error OR exception OR timeout OR failed"
MCP_LOGS_LIMIT=200
```

Each sync run is recorded in Postgres. Non-empty snapshots are chunked, embedded, and stored in pgvector as `alert` or `logs` source documents, so user questions can cite current operational context.

## Notes

- Never put GitHub passwords, OpenAI keys, or production database passwords in this repo. Use environment variables, GitHub PATs, or SSH keys.
- The reranker is a lightweight hybrid reranker that combines vector similarity with keyword/title coverage. Replace `RetrievalService.keywordBoost` with a cross-encoder or LLM reranker when you want higher precision.
