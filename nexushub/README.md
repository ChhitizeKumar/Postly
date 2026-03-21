# NexusHub 🚀

> AI-Powered Content & Community Platform — Production-grade microservices architecture

[![CI/CD](https://github.com/yourusername/nexushub/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/yourusername/nexushub/actions)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue)](https://react.dev)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## What is NexusHub?

NexusHub is a full-stack, production-ready platform where developers write and share technical posts, powered by Claude AI for writing assistance and featuring real-time notifications, scalable event-driven architecture, and complete observability.

**It's not a tutorial app. It's a system you'd be proud to maintain at a real company.**

---

## Architecture

```
                        ┌─────────────────────────────────┐
                        │   React 18 + TypeScript + Vite   │
                        │   Zustand · React Query · TipTap │
                        └────────────────┬────────────────┘
                                         │ HTTPS / WebSocket
                        ┌────────────────▼────────────────┐
                        │        API Gateway (8080)         │
                        │   Spring Cloud · JWT · RateLimit  │
                        │   Circuit Breaker · Routing        │
                        └───┬──────┬──────┬───────┬────────┘
                            │      │      │       │
              ┌─────────────▼┐ ┌───▼──┐ ┌▼─────┐ ┌▼──────────┐
              │ Auth Service │ │ User │ │  AI  │ │  Content  │
              │ JWT · OAuth2 │ │ Svc  │ │ Svc  │ │   Svc     │
              │ PostgreSQL   │ │ PG   │ │ PG + │ │  MongoDB  │
              └──────────────┘ └──────┘ │pgvec │ └─────┬─────┘
                                        └──────┘       │
                        ┌───────────────────────────────▼──┐
                        │         Apache Kafka Bus          │
                        │  user-events · post-events        │
                        │  ai-jobs · notifications · audit  │
                        └───────┬──────────────┬────────────┘
                                │              │
              ┌─────────────────▼──┐  ┌────────▼──────────┐
              │ Notification Svc   │  │  Analytics Svc    │
              │ WebSocket · Email  │  │  ClickHouse       │
              │ Redis Pub/Sub      │  │  Grafana Metrics  │
              └────────────────────┘  └───────────────────┘

         Shared: Redis (cache · sessions · rate-limit) · MinIO (S3 files)
         Observability: Prometheus · Grafana · Zipkin · Eureka
         Deploy: Docker Compose → Kubernetes (Helm) → Cloud (AWS/GCP)
```

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Frontend** | React 18 + TypeScript + Vite | Fast HMR, type safety, code splitting |
| **State** | Zustand + React Query | Simple global state + server cache |
| **Editor** | TipTap (ProseMirror) | Extensible rich text editor |
| **Gateway** | Spring Cloud Gateway | Reactive routing, JWT validation, rate limiting |
| **Backend** | Spring WebFlux (reactive) | Non-blocking I/O for 10k+ concurrent users |
| **Auth** | Spring Security + JWT + OAuth2 | Industry-standard auth flows |
| **Messaging** | Apache Kafka | Decoupled async event processing |
| **Cache** | Redis | Sessions, rate limiting, response caching |
| **DB: Users/Auth** | PostgreSQL + pgvector | ACID + vector similarity for RAG |
| **DB: Content** | MongoDB | Flexible document schema for posts |
| **DB: Analytics** | ClickHouse | Sub-second aggregation over millions of events |
| **Files** | MinIO (S3-compatible) | Local S3 for images and documents |
| **AI** | Anthropic Claude API | Writing assistant, RAG, summarization |
| **Service Discovery** | Netflix Eureka | Auto-registration and load balancing |
| **Tracing** | Zipkin + Micrometer | Distributed request tracing |
| **Metrics** | Prometheus + Grafana | Service health dashboards |
| **CI/CD** | GitHub Actions | Automated test → build → deploy |
| **Container** | Docker + Kubernetes | Local dev and cloud production |

---

## Services

| Service | Port | Tech | Responsibility |
|---|---|---|---|
| `api-gateway` | 8080 | Spring Cloud Gateway | Single entry point, JWT auth, routing |
| `auth-service` | 8081 | Spring Boot + PostgreSQL | Login, register, OAuth2, tokens |
| `user-service` | 8082 | Spring WebFlux + PostgreSQL | Profiles, follows, avatars |
| `content-service` | 8083 | Spring WebFlux + MongoDB | Posts, comments, tags, likes |
| `ai-service` | 8084 | Spring WebFlux + pgvector | Claude AI, RAG, streaming |
| `notification-service` | 8085 | Spring WebFlux + Redis | WebSocket, email notifications |
| `analytics-service` | 8086 | Spring Boot + ClickHouse | Event aggregation, dashboards |

---

## Quick Start

### Prerequisites

- Docker + Docker Compose
- Java 21+
- Node.js 20+
- Maven 3.9+

### 1. Clone and configure

```bash
git clone https://github.com/yourusername/nexushub.git
cd nexushub

# Create your .env file
cp .env.example .env

# Edit .env and add your keys:
#   ANTHROPIC_API_KEY=sk-ant-...
#   GOOGLE_CLIENT_ID=...
#   GOOGLE_CLIENT_SECRET=...
```

### 2. Start infrastructure only (fastest way to begin)

```bash
./start.sh --infra-only
```

This boots: PostgreSQL, MongoDB, Redis, ClickHouse, MinIO, Kafka + Zookeeper, Kafka UI, Prometheus, Grafana, Zipkin.

### 3. Run services in dev mode

```bash
# Each in its own terminal (or use an IDE)
cd services/auth-service    && ./mvnw spring-boot:run
cd services/api-gateway     && ./mvnw spring-boot:run
cd services/content-service && ./mvnw spring-boot:run
cd services/ai-service      && ./mvnw spring-boot:run

# Frontend
cd frontend && npm install && npm run dev
```

### 4. Full Docker stack

```bash
./start.sh --full
```

### 5. Stop everything

```bash
./start.sh --stop
```

---

## Dashboards & URLs

| Tool | URL | Credentials |
|---|---|---|
| **Frontend** | http://localhost:3000 | — |
| **API Gateway** | http://localhost:8080 | — |
| **Eureka** | http://localhost:8761 | — |
| **Grafana** | http://localhost:3001 | admin / nexushub_admin |
| **Zipkin** | http://localhost:9411 | — |
| **Prometheus** | http://localhost:9090 | — |
| **Kafka UI** | http://localhost:8090 | — |
| **MinIO Console** | http://localhost:9001 | nexushub / nexushub_secret_minio |

---

## Key Features

### 🤖 AI Writing Assistant (Streaming)
Real-time text improvement via Server-Sent Events. Words stream back from Claude word-by-word as you type.

```
POST /api/v1/ai/write/stream
Content-Type: text/event-stream

→ data: "Here"
→ data: " is"
→ data: " the"
→ data: " improved..."
```

### 📨 Real-Time Notifications
WebSocket connection per user. When someone likes your post, the notification appears instantly in the other browser tab.

### 📄 Document Q&A (RAG)
Upload a PDF → text is chunked → embedded into pgvector → ask questions → Claude answers from the document's content only.

### 📊 Analytics Pipeline
Every user action (post view, like, follow) flows through Kafka → ClickHouse → Grafana. Sub-second aggregation over millions of events.

---

## Project Structure

```
nexushub/
├── frontend/                    # React 18 + Vite
│   ├── src/
│   │   ├── components/          # Reusable UI components
│   │   ├── pages/               # Route-level pages
│   │   ├── hooks/               # useAiWriter, useNotifications, ...
│   │   ├── store/               # Zustand stores
│   │   └── services/            # API client, streaming
│   └── Dockerfile
│
├── services/
│   ├── api-gateway/             # Spring Cloud Gateway
│   ├── auth-service/            # JWT + OAuth2
│   ├── user-service/            # Profiles + follows
│   ├── content-service/         # Posts + comments
│   ├── ai-service/              # Claude + RAG
│   ├── notification-service/    # WebSocket + email
│   └── analytics-service/       # ClickHouse consumer
│
├── infrastructure/
│   ├── docker/                  # DB init scripts, Prometheus config
│   ├── k8s/helm/                # Kubernetes Helm charts
│   └── terraform/               # Cloud IaC (AWS/GCP)
│
├── .github/workflows/           # CI/CD pipeline
├── docker-compose.yml           # Full local stack
├── start.sh                     # One-command startup
└── pom.xml                      # Maven parent POM
```

---

## Environment Variables

See `.env.example` for all variables. Required ones:

| Variable | Description |
|---|---|
| `JWT_SECRET` | Min 256-bit secret. Generate: `openssl rand -base64 64` |
| `ANTHROPIC_API_KEY` | Get from console.anthropic.com |
| `GOOGLE_CLIENT_ID` | From Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | From Google Cloud Console |

---

## Phases

- [x] **Phase 1** — Project scaffold, infrastructure, Docker Compose
- [ ] **Phase 2** — Auth Service + API Gateway (complete auth flows)
- [ ] **Phase 3** — Content Service (posts, comments, tags, search)
- [ ] **Phase 4** — AI Service (streaming, RAG, auto-tagging)
- [ ] **Phase 5** — Notification Service (WebSocket, email)
- [ ] **Phase 6** — Analytics + Grafana dashboards
- [ ] **Phase 7** — Kubernetes + CI/CD + cloud deployment

---

## License

MIT © NexusHub Contributors
