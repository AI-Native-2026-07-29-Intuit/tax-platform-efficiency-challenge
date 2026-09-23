#!/usr/bin/env bash
# ============================================================================
# Correctness gate. Independent of the benchmark's internal check: it derives
# the expected tax rate straight from the database (the newest effective rule)
# and asserts the live API returns a matching tax for a NON-exempt customer.
#
#   ./benchmark/verify-correctness.sh
#   BASE_URL=http://localhost:8080 ./benchmark/verify-correctness.sh
#
# Requires: curl, jq, and a running stack (uses `docker compose exec db psql`).
# ============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_USER="${DB_USER:-tax}"
DB_NAME="${DB_NAME:-taxdb}"
AMOUNT="1200.00"

# Non-exempt customers only (id % 33 != 0). 1 qualifies.
CASES=(
  "CA CITY_5 SOFTWARE 1"
  "NY CITY_32 HARDWARE 2"
  "TX CITY_43 GROCERY 4"
  "FL CITY_9 SERVICES 7"
  "WA CITY_47 DIGITAL_GOODS 11"
)

psql_q() {
  docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -tA -c "$1"
}

fail=0
printf '%-4s %-10s %-14s %-10s %-10s %-10s %s\n' STATE CITY PRODUCT DB_RATE EXP_TAX API_TAX RESULT
for c in "${CASES[@]}"; do
  read -r state city product customer <<<"$c"

  db_rate=$(psql_q "SELECT rate FROM tax_rules
                    WHERE state='$state' AND city='$city' AND product_type='$product'
                      AND effective_from <= now()
                    ORDER BY effective_from DESC LIMIT 1;")

  if [[ -z "$db_rate" ]]; then
    printf '%-4s %-10s %-14s %-10s %-10s %-10s %s\n' "$state" "$city" "$product" "-" "-" "-" "NO_RULE"
    fail=1
    continue
  fi

  expected_tax=$(awk -v a="$AMOUNT" -v r="$db_rate" 'BEGIN{printf "%.2f", a*r}')

  body=$(printf '{"customerId":%s,"amount":%s,"state":"%s","city":"%s","productType":"%s"}' \
         "$customer" "$AMOUNT" "$state" "$city" "$product")
  resp=$(curl -s -X POST "$BASE_URL/api/tax/calculate" -H 'Content-Type: application/json' -d "$body")
  api_tax=$(echo "$resp" | jq -r '.tax')

  if [[ "$api_tax" == "$expected_tax" ]]; then
    result="PASS"
  else
    result="FAIL"
    fail=1
  fi
  printf '%-4s %-10s %-14s %-10s %-10s %-10s %s\n' \
    "$state" "$city" "$product" "$db_rate" "$expected_tax" "$api_tax" "$result"
done

echo
if [[ "$fail" -eq 0 ]]; then
  echo "CORRECTNESS: PASS - API tax matches the database rule for all cases."
  exit 0
else
  echo "CORRECTNESS: FAIL - at least one case mismatched. Tax results must stay correct!"
  exit 1
fi
