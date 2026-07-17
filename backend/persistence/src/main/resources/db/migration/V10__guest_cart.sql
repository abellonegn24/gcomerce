-- V10: Guest cart support — session-keyed carts for anonymous shoppers.
-- customer_id becomes nullable (guest carts have no customer), and a new
-- session_key column lets us look up a guest's cart by their browser session.

-- Drop the existing unique constraint on customer_id so we can alter the column.
ALTER TABLE cart DROP CONSTRAINT uk_cart_customer;

-- Make customer_id nullable so a cart row can belong to a guest session.
ALTER TABLE cart ALTER COLUMN customer_id DROP NOT NULL;

-- Re-add the unique constraint on customer_id (NULL values are distinct
-- under PostgreSQL, so multiple guest carts with NULL customer_id coexist).
ALTER TABLE cart ADD CONSTRAINT uk_cart_customer UNIQUE (customer_id);

-- Session key for guest carts (UUID generated client-side, stored in localStorage).
ALTER TABLE cart ADD COLUMN session_key VARCHAR(36) NULL;
ALTER TABLE cart ADD CONSTRAINT uk_cart_session_key UNIQUE (session_key);
