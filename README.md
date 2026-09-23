# 💰 Tax Platform Efficiency Challenge

A Spring Boot + PostgreSQL tax-calculation platform that is **correct but too
expensive to run**. Your job is to make it **cheaper and faster** without
changing tax-calculation correctness.

```
Customer → Tax Calculation API → find jurisdiction → load tax rules → calculate → store calculation
```

| | Starting | Target |
|---|---|---|
| App instances | 4 | **2** |
| CPU / instance | 4 | **2** |
| RAM / instance | 8 GB | **4 GB** |
| Throughput | 380 rps | **> 800 rps** |
| P95 | 1.9 s | **< 500 ms** |
| Queries / request | 14 | fewer |
| Correctness | ✅ | ✅ (non-negotiable) |

> **The rule:** you may only *reduce* CPU, RAM, DB connections, and instance
> count — never increase them. `$1 of compute should do as much useful work as
> possible.`

---

## Prerequisites

- Docker + Docker Compose v2
- [k6](https://k6.io/docs/get-started/installation/) (`brew install k6`)
- `jq` and `curl` (for the smoke test / correctness gate)
- ~6 GB free RAM and a few minutes for the first seed

## Quick start

```bash
# 1. Build + start the STARTING config (4 × 4 CPU / 8 GB). First run seeds ~5M rows.
make up            # or: docker compose up -d --build

# 2. Wait for seeding to finish, then watch the app come up
make logs          # ctrl-c once you see "Started TaxPlatformApplication"

# 3. Smoke test one calculation
make smoke

# 4. Run the benchmark
make bench         # k6 run benchmark/benchmark.js

# 5. Prove tax results are still correct
make verify
```

The API is served through nginx at **http://localhost:8080**.

### Example request

```bash
curl -s -X POST http://localhost:8080/api/tax/calculate \
  -H 'Content-Type: application/json' \
  -d '{"customerId":82731,"amount":1200.00,"state":"CA","city":"CITY_5","productType":"SOFTWARE"}' | jq .
```

```json
{ "subtotal": 1200.00, "taxRate": 0.0875, "tax": 105.00, "total": 1305.00 }
```

> Cities are seeded deterministically as `CITY_1 … CITY_1000`; city `CITY_n`
> belongs to state index `(n-1) % 50`. The benchmark uses the same mapping so
> every generated request has a real tax rule.

## Reduced-infrastructure round

Re-launch with half the infrastructure and re-run against the target:

```bash
make cost-cut      # 2 instances × 2 CPU × 4 GB
make bench-target  # k6 at the 800 rps target
```

The goal is to hit **800+ rps, P95 < 500ms, correct results** at this size.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/tax/calculate` | Single calculation (the hot path) |
| `POST` | `/api/tax/batch?limit=100` | Batch recalculation over recent transactions |
| `GET`  | `/api/tax/rule?state=&city=&productType=` | Inspect the current rule |
| `POST` | `/api/tax/rules` | Publish a tax-rate change for a key |
| `GET`  | `/actuator/health` · `/actuator/metrics` · `/actuator/prometheus` | Ops |

## Measuring

```bash
make explain       # EXPLAIN ANALYZE + live connections + lock waits
make psql          # interactive psql
make monitoring    # optional Prometheus (:9090) + Grafana (:3000, anon admin)
SHOW_SQL=true docker compose up -d   # log every SQL statement (count queries/request)
```

## Scaling the dataset down (laptops)

The full dataset is ~5M transactions. To seed a smaller one, set the sizes
before the **first** `up` (they only apply when the volume is empty):

```bash
SEED_TRANSACTIONS=500000 SEED_CUSTOMERS=50000 docker compose up -d --build
# reset the DB volume to re-seed: make clean && ... up
```

## Troubleshooting

- **`port is already allocated` (5432 / 8080)** — a local Postgres or another
  service holds the port. Change the host-side ports in `.env`:
  `DB_PUBLISH_PORT=55432`, `LB_PUBLISH_PORT=18080` (then point k6/curl at the new
  LB port). Internal service-to-service ports are unaffected.
- **App queries fail right after `up`** — the first run is still seeding ~5M
  rows. Watch `make logs` for `[seed] done.` and `Started TaxPlatformApplication`.
- **Changed `SEED_*` but the dataset didn't change** — seed sizes only apply to
  an empty volume. Run `make clean` then `make up` to reseed.

## Repo layout

```
src/main/java/com/taxplatform/   Spring Boot app
db/schema.sql db/seed.sql        Schema + procedural seed
docker-compose.yml .env*         nginx LB + app replicas + Postgres, CPU/RAM limits
benchmark/                       k6 load test + correctness gate
scripts/                         Database diagnostics (EXPLAIN ANALYZE)
monitoring/                      nginx, Prometheus, Grafana configs
```


