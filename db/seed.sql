-- ============================================================================
-- Procedural seed. Generates the full dataset with generate_series so the repo
-- stays tiny. Row counts are driven by psql variables (with defaults):
--
--   :cities        number of distinct cities                (default 1000)
--   :versions      historical rule versions per city+product (default 6)
--   :customers     number of customers                      (default 500000)
--   :transactions  number of historical transactions        (default 5000000)
--
-- Rules generated = :cities * 8 products * :versions  (default ~48,000).
-- The benchmark derives valid (state, city, productType) combos from the SAME
-- deterministic rules used here, so every generated request has a matching rule.
-- ============================================================================

\set ON_ERROR_STOP on
\if :{?cities}      \else \set cities 1000       \endif
\if :{?versions}    \else \set versions 6        \endif
\if :{?customers}   \else \set customers 500000  \endif
\if :{?transactions}\else \set transactions 5000000 \endif

\echo Seeding: cities=:cities versions=:versions customers=:customers transactions=:transactions

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
INSERT INTO states (ordinal, code, name) VALUES
 (0,'AL','Alabama'),(1,'AK','Alaska'),(2,'AZ','Arizona'),(3,'AR','Arkansas'),
 (4,'CA','California'),(5,'CO','Colorado'),(6,'CT','Connecticut'),(7,'DE','Delaware'),
 (8,'FL','Florida'),(9,'GA','Georgia'),(10,'HI','Hawaii'),(11,'ID','Idaho'),
 (12,'IL','Illinois'),(13,'IN','Indiana'),(14,'IA','Iowa'),(15,'KS','Kansas'),
 (16,'KY','Kentucky'),(17,'LA','Louisiana'),(18,'ME','Maine'),(19,'MD','Maryland'),
 (20,'MA','Massachusetts'),(21,'MI','Michigan'),(22,'MN','Minnesota'),(23,'MS','Mississippi'),
 (24,'MO','Missouri'),(25,'MT','Montana'),(26,'NE','Nebraska'),(27,'NV','Nevada'),
 (28,'NH','New Hampshire'),(29,'NJ','New Jersey'),(30,'NM','New Mexico'),(31,'NY','New York'),
 (32,'NC','North Carolina'),(33,'ND','North Dakota'),(34,'OH','Ohio'),(35,'OK','Oklahoma'),
 (36,'OR','Oregon'),(37,'PA','Pennsylvania'),(38,'RI','Rhode Island'),(39,'SC','South Carolina'),
 (40,'SD','South Dakota'),(41,'TN','Tennessee'),(42,'TX','Texas'),(43,'UT','Utah'),
 (44,'VT','Vermont'),(45,'VA','Virginia'),(46,'WA','Washington'),(47,'WV','West Virginia'),
 (48,'WI','Wisconsin'),(49,'WY','Wyoming');

INSERT INTO product_tax_categories (id, code, name) VALUES
 (1,'SOFTWARE','Software'),
 (2,'HARDWARE','Hardware'),
 (3,'FOOD','Prepared Food'),
 (4,'CLOTHING','Clothing'),
 (5,'SERVICES','Services'),
 (6,'DIGITAL_GOODS','Digital Goods'),
 (7,'MEDICAL','Medical'),
 (8,'GROCERY','Grocery');

-- ---------------------------------------------------------------------------
-- Cities: city g belongs to state (g-1) % 50, named CITY_g
-- ---------------------------------------------------------------------------
INSERT INTO cities (id, state_code, name)
SELECT g, s.code, 'CITY_' || g
FROM generate_series(1, :cities) AS g
JOIN states s ON s.ordinal = (g - 1) % 50;

INSERT INTO jurisdictions (id, state_code, city, name)
SELECT g, s.code, 'CITY_' || g, 'Jurisdiction of CITY_' || g
FROM generate_series(1, :cities) AS g
JOIN states s ON s.ordinal = (g - 1) % 50;

-- ---------------------------------------------------------------------------
-- Tax rules: for each city x 8 product types x :versions historical versions.
-- Version 1 is the CURRENT rule (effective yesterday); higher versions are
-- progressively older. "Current rate" = base(product) + (city % 10) * 0.0005.
-- ---------------------------------------------------------------------------
INSERT INTO tax_rules (id, state, city, product_type, rate, jurisdiction_id, effective_from)
SELECT
    ((c.g - 1) * :versions * 8) + ((p.idx - 1) * :versions) + v      AS id,
    s.code,
    'CITY_' || c.g,
    p.code,
    round((p.base + (c.g % 10) * 0.0005 + (v - 1) * 0.0010)::numeric, 4),
    c.g,
    now() - interval '1 day' - (v - 1) * interval '180 days'
FROM generate_series(1, :cities) AS c(g)
JOIN states s ON s.ordinal = (c.g - 1) % 50
CROSS JOIN (VALUES
    (1,'SOFTWARE',0.0875),
    (2,'HARDWARE',0.0725),
    (3,'FOOD',0.0400),
    (4,'CLOTHING',0.0650),
    (5,'SERVICES',0.0800),
    (6,'DIGITAL_GOODS',0.0600),
    (7,'MEDICAL',0.0250),
    (8,'GROCERY',0.0300)
) AS p(idx, code, base)
CROSS JOIN generate_series(1, :versions) AS v;

-- ---------------------------------------------------------------------------
-- Customers, profiles, summaries (1:1:1)
-- ---------------------------------------------------------------------------
INSERT INTO customers (id, name, email, state_code, city, created_at)
SELECT g,
       'Customer ' || g,
       'c' || g || '@example.com',
       s.code,
       'CITY_' || (1 + (g % :cities)),
       now() - (g % 365) * interval '1 day'
FROM generate_series(1, :customers) AS g
JOIN states s ON s.ordinal = (g - 1) % 50;

-- ~3% of customers are fully tax exempt (deterministic: id % 33 = 0)
INSERT INTO tax_profiles (id, customer_id, exempt, exemption_rate, tax_id_number)
SELECT g, g, (g % 33 = 0), 0, 'TIN' || g
FROM generate_series(1, :customers) AS g;

INSERT INTO customer_tax_summary (id, customer_id, total_amount, total_tax, calculation_count, version)
SELECT g, g, 0, 0, 0, 0
FROM generate_series(1, :customers) AS g;

-- ---------------------------------------------------------------------------
-- Transactions: state ALWAYS matches the city's state so batch recalcs match.
-- ---------------------------------------------------------------------------
INSERT INTO transactions (id, customer_id, amount, state, city, product_type, created_at)
SELECT g,
       1 + (random() * (:customers - 1))::bigint,
       round((10 + random() * 5000)::numeric, 2),
       s.code,
       'CITY_' || (1 + (g % :cities)),
       (ARRAY['SOFTWARE','HARDWARE','FOOD','CLOTHING','SERVICES','DIGITAL_GOODS','MEDICAL','GROCERY'])[1 + (g % 8)],
       now() - (random() * 365) * interval '1 day'
FROM generate_series(1, :transactions) AS g
JOIN states s ON s.ordinal = ((g % :cities)) % 50;

-- ---------------------------------------------------------------------------
-- Statistics so EXPLAIN ANALYZE reflects reality from the first request.
-- ---------------------------------------------------------------------------
ANALYZE;

\echo Seed complete.
SELECT
    (SELECT count(*) FROM tax_rules)    AS tax_rules,
    (SELECT count(*) FROM customers)    AS customers,
    (SELECT count(*) FROM transactions) AS transactions;
