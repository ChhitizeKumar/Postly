# Postly — Step 1: Infrastructure

This folder contains **only the infrastructure** — no application code yet.
We'll add services one by one in the next steps.

## What's included

| Container | Purpose | Port |
|---|---|---|
| `postly-postgres` | Auth, User, AI databases | `5432` |
| `postly-mongodb` | Content (posts, comments) | `27017` |
| `postly-redis` | Cache, sessions, rate limiting | `6379` |
| `postly-kafka` | Event bus between services | `9092` |
| `postly-zookeeper` | Required by Kafka | internal |
| `postly-kafka-ui` | Visual Kafka browser | `8090` |
| `postly-clickhouse` | Analytics database | `8123` |
| `postly-minio` | File storage (S3-compatible) | `9001`, `9002` |

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running
- That's it for this step!

## Start

```bash
docker compose up -d
```

Wait about 30 seconds for everything to start, then verify:

```bash
chmod +x verify.sh
./verify.sh
```

You should see all green checkmarks.

## Useful commands

```bash
# See all running containers
docker compose ps

# Watch logs for a specific service
docker compose logs -f kafka
docker compose logs -f postgres

# Open a Postgres shell
docker exec -it postly-postgres psql -U postly -d postly_auth

# Open a Redis shell
docker exec -it postly-redis redis-cli -a postly_secret

# Open a MongoDB shell
docker exec -it postly-mongodb mongosh -u postly -p postly_secret postly_content

# Stop everything (keeps data)
docker compose down

# Stop and DELETE all data (fresh start)
docker compose down -v
```

## Visual UIs

| Tool | URL | Login |
|---|---|---|
| Kafka UI | http://localhost:8090 | none |
| MinIO Console | http://localhost:9001 | postly / postly_secret |

## Credentials (local dev only)

| Service | User | Password |
|---|---|---|
| PostgreSQL | `postly` | `postly_secret` |
| MongoDB | `postly` | `postly_secret` |
| Redis | — | `postly_secret` |
| ClickHouse | `postly` | `postly_secret` |
| MinIO | `postly` | `postly_secret` |

## Next step

Once `./verify.sh` shows all green → **Step 2: Auth Service**
