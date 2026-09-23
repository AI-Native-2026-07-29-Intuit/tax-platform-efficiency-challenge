-- ============================================================================
-- Tax Platform schema.
-- ============================================================================

CREATE TABLE states (
    ordinal INT PRIMARY KEY,
    code    VARCHAR(2)  UNIQUE NOT NULL,
    name    VARCHAR(64) NOT NULL
);

CREATE TABLE product_tax_categories (
    id   INT PRIMARY KEY,
    code VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(64) NOT NULL
);

CREATE TABLE cities (
    id         BIGINT PRIMARY KEY,
    state_code VARCHAR(2)  NOT NULL REFERENCES states(code),
    name       VARCHAR(64) NOT NULL
);
CREATE INDEX idx_cities_state_name ON cities(state_code, name);

CREATE TABLE jurisdictions (
    id         BIGINT PRIMARY KEY,
    state_code VARCHAR(2)   NOT NULL,
    city       VARCHAR(64)  NOT NULL,
    name       VARCHAR(128) NOT NULL
);
CREATE INDEX idx_jurisdictions_state_city ON jurisdictions(state_code, city);

CREATE TABLE tax_rules (
    id             BIGINT PRIMARY KEY,
    state          VARCHAR(2)   NOT NULL,
    city           VARCHAR(64)  NOT NULL,
    product_type   VARCHAR(32)  NOT NULL,
    rate           NUMERIC(6,4) NOT NULL,
    jurisdiction_id BIGINT,
    effective_from TIMESTAMPTZ  NOT NULL
);
-- FIX (Problem 1): composite covering index matching the exact access pattern
-- WHERE state=? AND city=? AND product_type=? ORDER BY effective_from DESC.
-- Collapses the Bitmap Heap Scan + Sort into a single-row index scan.
CREATE INDEX idx_tax_rule_lookup
    ON tax_rules (state, city, product_type, effective_from DESC);

CREATE TABLE customers (
    id         BIGINT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    email      VARCHAR(128) NOT NULL,
    state_code VARCHAR(2),
    city       VARCHAR(64),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tax_profiles (
    id             BIGINT PRIMARY KEY,
    customer_id    BIGINT NOT NULL UNIQUE REFERENCES customers(id),
    exempt         BOOLEAN NOT NULL DEFAULT false,
    exemption_rate NUMERIC(4,3) NOT NULL DEFAULT 0,
    tax_id_number  VARCHAR(32)
);

CREATE TABLE transactions (
    id           BIGINT PRIMARY KEY,
    customer_id  BIGINT NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    state        VARCHAR(2)   NOT NULL,
    city         VARCHAR(64)  NOT NULL,
    product_type VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_transactions_customer ON transactions(customer_id);

CREATE TABLE customer_tax_summary (
    id                BIGINT PRIMARY KEY,
    customer_id       BIGINT NOT NULL UNIQUE REFERENCES customers(id),
    total_amount      NUMERIC(16,2) NOT NULL DEFAULT 0,
    total_tax         NUMERIC(16,2) NOT NULL DEFAULT 0,
    calculation_count BIGINT NOT NULL DEFAULT 0,
    version           BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tax_calculations (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    subtotal    NUMERIC(12,2) NOT NULL,
    tax_rate    NUMERIC(6,4)  NOT NULL,
    tax         NUMERIC(12,2) NOT NULL,
    total       NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_tax_calc_customer ON tax_calculations(customer_id);
