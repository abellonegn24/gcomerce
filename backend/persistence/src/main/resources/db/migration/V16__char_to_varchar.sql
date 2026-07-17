-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V16: Convert CHAR columns to VARCHAR
--
-- Hibernate's schema validator expects VARCHAR for String @Column mappings,
-- but several migrations created CHAR(n) columns. PostgreSQL stores CHAR as
-- bpchar which Hibernate maps to Types#CHAR, causing validation failures.
-- This migration aligns the database with Hibernate's expectations.
-- =============================================================================

-- CHAR(36) → VARCHAR(36) — UUID columns
ALTER TABLE admin_refresh_token ALTER COLUMN id TYPE VARCHAR(36);
ALTER TABLE admin_refresh_token ALTER COLUMN replaced_by_id TYPE VARCHAR(36);
ALTER TABLE admin_refresh_token ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE customer_refresh_token ALTER COLUMN id TYPE VARCHAR(36);
ALTER TABLE customer_refresh_token ALTER COLUMN replaced_by_id TYPE VARCHAR(36);
ALTER TABLE customer_refresh_token ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE checkout_order ALTER COLUMN uuid TYPE VARCHAR(36);
ALTER TABLE order_refund ALTER COLUMN uuid TYPE VARCHAR(36);
ALTER TABLE quote ALTER COLUMN uuid TYPE VARCHAR(36);

-- CHAR(3) → VARCHAR(3) — Currency columns
ALTER TABLE cart ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE cart_item ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE checkout_order ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE shipping_method ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE quote ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE product ALTER COLUMN base_currency TYPE VARCHAR(3);
ALTER TABLE price_list ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE customer ALTER COLUMN preferred_currency TYPE VARCHAR(3);

-- CHAR(2) → VARCHAR(2) — Country code columns
ALTER TABLE product ALTER COLUMN origin_country TYPE VARCHAR(2);
ALTER TABLE customer_address ALTER COLUMN country_code TYPE VARCHAR(2);
ALTER TABLE shipping_rate_zone ALTER COLUMN country_code TYPE VARCHAR(2);
ALTER TABLE tax_rule ALTER COLUMN country_code TYPE VARCHAR(2);

-- CHAR(64) → VARCHAR(64) — Token hash columns (additional tables)
ALTER TABLE email_verification_token ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE password_reset_token ALTER COLUMN token_hash TYPE VARCHAR(64);

-- Missing version column — BaseEntity has @Version but product_variant table lacks it
ALTER TABLE product_variant ADD COLUMN version INT NOT NULL DEFAULT 0;
