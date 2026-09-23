#!/bin/bash
# Runs at Postgres init (after schema.sql). Reads seed sizes from environment
# so you can scale the dataset down for a laptop without editing SQL:
#
#   SEED_CITIES, SEED_VERSIONS, SEED_CUSTOMERS, SEED_TRANSACTIONS
#
# Defaults reproduce the challenge spec (~5M transactions).
set -euo pipefail

echo "[seed] starting (this can take a couple of minutes for the full dataset)..."

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v cities="${SEED_CITIES:-1000}" \
     -v versions="${SEED_VERSIONS:-6}" \
     -v customers="${SEED_CUSTOMERS:-500000}" \
     -v transactions="${SEED_TRANSACTIONS:-5000000}" \
     -f /seed/seed.sql

echo "[seed] done."
