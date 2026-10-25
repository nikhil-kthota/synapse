#!/usr/bin/env bash
# Starts the whole Synapse stack on macOS/Linux: database, AI model check, backend and frontend.
# Usage (from the repo root):  ./start.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Creating .env with fresh local secrets..."
  {
    echo "# Local development secrets for Synapse. Never commit this file."
    echo "POSTGRES_DB=synapse"
    echo "POSTGRES_USER=synapse"
    echo "POSTGRES_PASSWORD=$(openssl rand -hex 18)"
    echo "SYNAPSE_DB_PORT=5433"
    echo "SYNAPSE_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
    echo "OLLAMA_BASE_URL=http://localhost:11434"
    echo "OLLAMA_MODEL=qwen2.5-coder:7b"
  } > .env
fi

echo "[1/4] Starting PostgreSQL (Docker)..."
docker compose up -d >/dev/null

echo "[2/4] Checking the local AI model..."
MODEL=$(grep '^OLLAMA_MODEL=' .env | cut -d= -f2-)
if command -v ollama >/dev/null; then
  ollama list | grep -q "$MODEL" || { echo "      Pulling $MODEL (one-time, ~4.7 GB)..."; ollama pull "$MODEL"; }
else
  echo "      Ollama not installed: AI documentation will be skipped. See https://ollama.com" >&2
fi

[ -d node_modules ] || npm install

echo "[3/4] Starting the backend on http://localhost:8080 ..."
(cd backend && ./mvnw -q spring-boot:run) &
BACKEND_PID=$!
trap 'kill $BACKEND_PID 2>/dev/null' EXIT

echo "[4/4] Starting the frontend on http://localhost:5173/synapse/ ..."
npm run dev
