#!/usr/bin/env bash
# ============================================================
# NexusHub - Master Startup Script
# Usage: ./start.sh [--infra-only] [--full] [--stop]
# ============================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

INFRA_ONLY=false
STOP=false
FULL=false

for arg in "$@"; do
  case $arg in
    --infra-only) INFRA_ONLY=true ;;
    --stop)       STOP=true ;;
    --full)       FULL=true ;;
  esac
done

print_banner() {
  echo -e "${CYAN}"
  echo "  _   _                    _   _       _     "
  echo " | \ | | _____  ___   _ __| | | |_   _| |__  "
  echo " |  \| |/ _ \ \/ / | | / __| |_| | | | '_ \ "
  echo " | |\  |  __/>  <| |_| \__ \  _| |_| | |_) |"
  echo " |_| \_|\___/_/\_\\\\__,_|___/_| \__|_|_.__/ "
  echo -e "${NC}"
  echo -e "${BOLD}  AI-Powered Content Platform — Microservices${NC}"
  echo ""
}

check_requirements() {
  echo -e "${BLUE}▶ Checking requirements...${NC}"
  local missing=()

  command -v docker      &>/dev/null || missing+=("docker")
  command -v docker-compose &>/dev/null || docker compose version &>/dev/null || missing+=("docker-compose")
  command -v java        &>/dev/null || missing+=("java 21+")
  command -v node        &>/dev/null || missing+=("node 20+")
  command -v mvn         &>/dev/null || missing+=("maven")

  if [ ${#missing[@]} -gt 0 ]; then
    echo -e "${RED}✗ Missing requirements: ${missing[*]}${NC}"
    exit 1
  fi
  echo -e "${GREEN}✓ All requirements met${NC}"
}

setup_env() {
  echo -e "${BLUE}▶ Setting up environment...${NC}"

  if [ ! -f .env ]; then
    cp .env.example .env
    echo -e "${YELLOW}⚠ Created .env from .env.example — please fill in your API keys!${NC}"
    echo -e "${YELLOW}  Required: ANTHROPIC_API_KEY, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET${NC}"
  fi

  # Source env
  set -o allexport
  source .env
  set +o allexport

  echo -e "${GREEN}✓ Environment loaded${NC}"
}

start_infrastructure() {
  echo -e "${BLUE}▶ Starting infrastructure (DBs, Kafka, Redis)...${NC}"

  docker compose up -d \
    postgres mongodb redis clickhouse minio \
    zookeeper kafka kafka-init kafka-ui \
    prometheus grafana zipkin

  echo -e "${BLUE}  Waiting for services to be healthy...${NC}"

  # Wait for Postgres
  echo -n "  Postgres: "
  for i in $(seq 1 30); do
    docker exec nexushub-postgres pg_isready -U nexushub &>/dev/null && echo -e "${GREEN}ready${NC}" && break
    echo -n "."
    sleep 2
  done

  # Wait for Kafka
  echo -n "  Kafka:    "
  for i in $(seq 1 30); do
    docker exec nexushub-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &>/dev/null && echo -e "${GREEN}ready${NC}" && break
    echo -n "."
    sleep 3
  done

  # Wait for Redis
  echo -n "  Redis:    "
  for i in $(seq 1 20); do
    docker exec nexushub-redis redis-cli --pass nexushub_secret ping &>/dev/null && echo -e "${GREEN}ready${NC}" && break
    echo -n "."
    sleep 2
  done

  echo -e "${GREEN}✓ Infrastructure ready${NC}"
}

build_services() {
  echo -e "${BLUE}▶ Building Spring Boot services...${NC}"
  mvn clean package -DskipTests -q
  echo -e "${GREEN}✓ Services built${NC}"
}

start_application_services() {
  echo -e "${BLUE}▶ Starting application services...${NC}"
  docker compose up -d \
    eureka-server \
    api-gateway \
    auth-service \
    user-service \
    content-service \
    ai-service \
    notification-service \
    analytics-service
  echo -e "${GREEN}✓ Application services started${NC}"
}

start_frontend() {
  echo -e "${BLUE}▶ Starting React frontend...${NC}"
  if [ "$FULL" = true ]; then
    docker compose up -d frontend
  else
    cd frontend
    npm install --silent
    npm run dev &
    cd ..
    echo -e "${GREEN}✓ Frontend dev server starting at http://localhost:3000${NC}"
  fi
}

print_dashboard() {
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}  🚀 NexusHub is running!${NC}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""
  echo -e "  ${BOLD}Application${NC}"
  echo -e "  Frontend          →  ${GREEN}http://localhost:3000${NC}"
  echo -e "  API Gateway       →  ${GREEN}http://localhost:8080${NC}"
  echo ""
  echo -e "  ${BOLD}Services${NC}"
  echo -e "  Auth Service      →  http://localhost:8081"
  echo -e "  User Service      →  http://localhost:8082"
  echo -e "  Content Service   →  http://localhost:8083"
  echo -e "  AI Service        →  http://localhost:8084"
  echo -e "  Notification Svc  →  http://localhost:8085"
  echo -e "  Analytics Service →  http://localhost:8086"
  echo ""
  echo -e "  ${BOLD}Observability${NC}"
  echo -e "  Eureka Dashboard  →  ${CYAN}http://localhost:8761${NC}"
  echo -e "  Grafana           →  ${CYAN}http://localhost:3001${NC}  (admin / nexushub_admin)"
  echo -e "  Zipkin Tracing    →  ${CYAN}http://localhost:9411${NC}"
  echo -e "  Prometheus        →  ${CYAN}http://localhost:9090${NC}"
  echo ""
  echo -e "  ${BOLD}Infrastructure${NC}"
  echo -e "  Kafka UI          →  ${CYAN}http://localhost:8090${NC}"
  echo -e "  MinIO Console     →  ${CYAN}http://localhost:9001${NC}  (nexushub / nexushub_secret_minio)"
  echo ""
  echo -e "  ${BOLD}Commands${NC}"
  echo -e "  Stop all:         ${YELLOW}./start.sh --stop${NC}"
  echo -e "  View logs:        ${YELLOW}docker compose logs -f [service]${NC}"
  echo -e "  Kafka topics:     ${YELLOW}http://localhost:8090${NC}"
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

stop_all() {
  echo -e "${YELLOW}▶ Stopping all NexusHub services...${NC}"
  docker compose down
  # Kill frontend dev server if running
  pkill -f "vite" 2>/dev/null || true
  echo -e "${GREEN}✓ All services stopped${NC}"
}

# ============================================================
# Main
# ============================================================
print_banner

if [ "$STOP" = true ]; then
  stop_all
  exit 0
fi

check_requirements
setup_env
start_infrastructure

if [ "$INFRA_ONLY" = false ]; then
  if [ "$FULL" = true ]; then
    build_services
    start_application_services
  fi
  start_frontend
fi

print_dashboard
