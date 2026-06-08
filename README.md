# AI Oncall Agent

A Spring Boot + React RAG application for on-call developers. It ingests code, logs, runbooks, and design docs, chunks them by headings/chapters, embeds them with Spring AI, stores vectors in pgvector, stores metadata in Postgres, retrieves and reranks relevant chunks, then asks OpenAI for a grounded answer.

## Stack

- Backend: Java 21, Spring Boot, Spring AI, JPA, Flyway, Apache Tika
- Frontend: React + Vite
- Storage: PostgreSQL + pgvector
- AI: OpenAI chat and embedding models through Spring AI

## Agent Architecture

The backend is split into two agent-style services:

- **Conversation Agent**: receives user questions, retrieves related chunks from pgvector through `RetrievalService`, reranks them, sends grounded context to the LLM, and returns an answer with citations.
- **Operations Agent**: processes operational signals from Prometheus alerts and MCP log queries, converts them into text snapshots, chunks/embeds them, and stores them in the same vector database for later diagnosis.

This keeps user-facing reasoning separate from background alert/log processing while sharing the same knowledge base and vector store.

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
- MCP logs: `POST {MCP_LOGS_ENDPOINT}` with a request body defined by `MCP_LOGS_REQUEST_TEMPLATE`

## Custom MCP Log Format

This project does not use a public MCP website by default. `http://mcp-logs:8081/mcp/logs/query` is only a Docker-network placeholder for a log MCP service that you run or receive from your class/team. To get an endpoint, deploy your own MCP/log-query HTTP service, then set `MCP_LOGS_ENDPOINT` to that service URL.

The backend can adapt to your request and response shape through environment variables:

```bash
MCP_LOGS_ENDPOINT=http://your-mcp-service:8081/mcp/logs/query
MCP_LOGS_AUTH_HEADER=Authorization
MCP_LOGS_AUTH_TOKEN=Bearer your_token
MCP_LOGS_REQUEST_TEMPLATE={"query":"${query}","since":"${since}","until":"${until}","limit":${limit}}
MCP_LOGS_RESPONSE_LOGS_PATH=logs
MCP_LOGS_RESPONSE_TIMESTAMP_FIELDS=timestamp,time,ts
MCP_LOGS_RESPONSE_SERVICE_FIELDS=service,app,source
MCP_LOGS_RESPONSE_LEVEL_FIELDS=level,severity
MCP_LOGS_RESPONSE_MESSAGE_FIELDS=message,msg,line
MCP_LOGS_RESPONSE_ATTRIBUTES_PATH=attributes
```

For example, if your MCP response is `{ "data": { "items": [...] } }`, set:

```bash
MCP_LOGS_RESPONSE_LOGS_PATH=data.items
```

If each log uses `body` instead of `message`, set:

```bash
MCP_LOGS_RESPONSE_MESSAGE_FIELDS=body,message,msg,line
```

Template placeholders available in `MCP_LOGS_REQUEST_TEMPLATE`: `${query}`, `${since}`, `${until}`, `${limit}`.

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
MCP_LOGS_AUTH_HEADER=
MCP_LOGS_AUTH_TOKEN=
MCP_LOGS_QUERY="error OR exception OR timeout OR failed"
MCP_LOGS_LIMIT=200
MCP_LOGS_REQUEST_TEMPLATE={"query":"${query}","since":"${since}","until":"${until}","limit":${limit}}
MCP_LOGS_RESPONSE_LOGS_PATH=logs
MCP_LOGS_RESPONSE_TIMESTAMP_FIELDS=timestamp,time,ts
MCP_LOGS_RESPONSE_SERVICE_FIELDS=service,app,source
MCP_LOGS_RESPONSE_LEVEL_FIELDS=level,severity
MCP_LOGS_RESPONSE_MESSAGE_FIELDS=message,msg,line
MCP_LOGS_RESPONSE_ATTRIBUTES_PATH=attributes
```

Each sync run is recorded in Postgres. Non-empty snapshots are chunked, embedded, and stored in pgvector as `alert` or `logs` source documents, so user questions can cite current operational context.

## Notes

- Never put GitHub passwords, OpenAI keys, or production database passwords in this repo. Use environment variables, GitHub PATs, or SSH keys.
- The reranker is a lightweight hybrid reranker that combines vector similarity with keyword/title coverage. Replace `RetrievalService.keywordBoost` with a cross-encoder or LLM reranker when you want higher precision.
