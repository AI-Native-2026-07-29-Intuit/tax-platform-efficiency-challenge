-- General database diagnostics. Run inside the db container:
--   docker compose exec -T db psql -U tax -d taxdb -f - < scripts/explain-analyze.sql

\echo '=== Tax rule lookup plan ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM tax_rules
WHERE state = 'CA' AND city = 'CITY_5' AND product_type = 'SOFTWARE'
  AND effective_from <= now()
ORDER BY effective_from DESC
LIMIT 1;

\echo '=== Current indexes on tax_rules ==='
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'tax_rules';

\echo '=== Table sizes ==='
SELECT relname AS table, n_live_tup AS approx_rows
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;

\echo '=== Live connections by state ==='
SELECT state, count(*)
FROM pg_stat_activity
WHERE datname = 'taxdb'
GROUP BY state
ORDER BY count(*) DESC;

\echo '=== Lock waits ==='
SELECT wait_event_type, wait_event, count(*)
FROM pg_stat_activity
WHERE datname = 'taxdb' AND wait_event IS NOT NULL
GROUP BY wait_event_type, wait_event
ORDER BY count(*) DESC;
