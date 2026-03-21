#!/usr/bin/env bash
# ================================================================
# Postly — Infrastructure Health Check
# Run after: docker compose up -d
# Usage: ./verify.sh
# ================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; FAILED=1; }
info() { echo -e "  ${YELLOW}→${NC} $1"; }

FAILED=0

echo ""
echo "Postly Infrastructure Check"
echo "=============================="
echo ""

# PostgreSQL
echo "PostgreSQL (port 5432)"
if docker exec postly-postgres pg_isready -U postly -d postly_auth &>/dev/null; then
  ok "Running and accepting connections"
  # Check our databases exist
  DBS=$(docker exec postly-postgres psql -U postly -tAc "SELECT datname FROM pg_database WHERE datname LIKE 'postly%'")
  for db in postly_auth postly_users postly_ai; do
    echo "$DBS" | grep -q "$db" && ok "Database '$db' exists" || fail "Database '$db' missing"
  done
else
  fail "Not reachable"
fi
echo ""

# MongoDB
echo "MongoDB (port 27017)"
if docker exec postly-mongodb mongosh --quiet --eval 'db.runCommand("ping").ok' postly_content &>/dev/null; then
  ok "Running and accepting connections"
  TAGS=$(docker exec postly-mongodb mongosh --quiet --eval 'db.tags.countDocuments()' postly_content 2>/dev/null)
  ok "Tags collection seeded with $TAGS entries"
else
  fail "Not reachable"
fi
echo ""

# Redis
echo "Redis (port 6379)"
if docker exec postly-redis redis-cli -a postly_secret ping 2>/dev/null | grep -q PONG; then
  ok "Running and accepting connections"
  # Test set/get
  docker exec postly-redis redis-cli -a postly_secret set postly:test "ok" &>/dev/null
  VAL=$(docker exec postly-redis redis-cli -a postly_secret get postly:test 2>/dev/null)
  [ "$VAL" = "ok" ] && ok "Read/write working" || fail "Read/write failed"
else
  fail "Not reachable"
fi
echo ""

# Kafka
echo "Kafka (port 9092)"
if docker exec postly-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &>/dev/null; then
  ok "Broker is up"
  TOPICS=$(docker exec postly-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null)
  for topic in user-events post-events notification-events ai-job-requests ai-job-results audit-log; do
    echo "$TOPICS" | grep -q "$topic" && ok "Topic '$topic' exists" || fail "Topic '$topic' missing (kafka-setup may still be running)"
  done
else
  fail "Broker not reachable"
fi
echo ""

# ClickHouse
echo "ClickHouse (port 8123)"
if curl -sf "http://localhost:8123/ping" &>/dev/null; then
  ok "HTTP interface responding"
  TABLE=$(curl -sf "http://localhost:8123/?user=postly&password=postly_secret&query=SHOW+TABLES+FROM+postly_analytics" 2>/dev/null)
  echo "$TABLE" | grep -q "events" && ok "events table exists" || fail "events table missing"
else
  fail "Not reachable"
fi
echo ""

# MinIO
echo "MinIO (port 9001 console, 9002 API)"
if curl -sf "http://localhost:9002/minio/health/live" &>/dev/null; then
  ok "S3 API healthy"
else
  fail "S3 API not reachable"
fi
echo ""

# Kafka UI
echo "Kafka UI (port 8090)"
if curl -sf "http://localhost:8090" &>/dev/null; then
  ok "Kafka UI is up → http://localhost:8090"
else
  info "Kafka UI still starting (normal — give it 30s)"
fi
echo ""

# Summary
echo "=============================="
if [ "$FAILED" -eq 0 ]; then
  echo -e "${GREEN}All checks passed! Infrastructure is ready.${NC}"
  echo ""
  echo "Next step → build the Auth Service"
else
  echo -e "${RED}Some checks failed. Run: docker compose logs [service-name]${NC}"
fi
echo ""
