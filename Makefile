.DEFAULT_GOAL := help
SHELL := /bin/bash

BASE_URL ?= http://localhost:8080

.PHONY: help up cost-cut monitoring down clean logs bench bench-target verify explain psql smoke

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Start the stack at the STARTING config (4x4CPU/8GB). First run seeds the DB.
	docker compose up -d --build

cost-cut: ## Switch to the reduced config: 2 instances x 2 CPU x 4 GB.
	docker compose --env-file .env.cost-cut up -d

monitoring: ## Start the stack WITH Prometheus + Grafana (:3000) + postgres-exporter.
	docker compose --profile monitoring up -d --build

down: ## Stop containers (keeps the seeded volume).
	docker compose down

clean: ## Stop containers AND delete the seeded database volume.
	docker compose down -v

logs: ## Tail app logs.
	docker compose logs -f app

smoke: ## One example calculation.
	curl -s -X POST $(BASE_URL)/api/tax/calculate \
		-H 'Content-Type: application/json' \
		-d '{"customerId":82731,"amount":1200.00,"state":"CA","city":"CITY_5","productType":"SOFTWARE"}' | jq .

bench: ## Run the k6 benchmark at the default rate.
	k6 run benchmark/benchmark.js

bench-target: ## Run k6 at the target 800 rps.
	k6 run -e RATE=800 -e BASE_URL=$(BASE_URL) benchmark/benchmark.js

verify: ## Correctness gate (API vs DB ground truth).
	BASE_URL=$(BASE_URL) ./benchmark/verify-correctness.sh

explain: ## Show query plans / connections / lock waits.
	docker compose exec -T db psql -U tax -d taxdb -f - < scripts/explain-analyze.sql

psql: ## Open a psql shell.
	docker compose exec db psql -U tax -d taxdb
