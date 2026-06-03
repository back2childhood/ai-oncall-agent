# AI Oncall Agent

A Spring Boot + React RAG application for on-call developers. It ingests code, logs, runbooks, and design docs, chunks them by headings/chapters, embeds them with Spring AI, stores vectors in pgvector, stores metadata in Postgres, retrieves and reranks relevant chunks, then asks OpenAI for a grounded answer.

## Stack

- Backend: Java 21, Spring Boot, Spring AI, JPA, Flyway, Apache Tika
- Frontend: React + Vite
- Storage: PostgreSQL + pgvector
- AI: OpenAI chat and embedding models through Spring AI

## Run

```bash
docker compose up -d
export OPENAI_API_KEY=your_key_here
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

## Notes

- Never put GitHub passwords, OpenAI keys, or production database passwords in this repo. Use environment variables, GitHub PATs, or SSH keys.
- The reranker is a lightweight hybrid reranker that combines vector similarity with keyword/title coverage. Replace `RetrievalService.keywordBoost` with a cross-encoder or LLM reranker when you want higher precision.
