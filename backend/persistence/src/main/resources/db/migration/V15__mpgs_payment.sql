-- MPGS (Mastercard Payment Gateway Services) hosted checkout integration.
-- Adds columns to store the MPGS session + success indicator on the order
-- so the webhook handler can verify payment results without relying on
-- the browser. payment_method records which gateway the customer chose
-- at checkout so the payment-payload endpoint knows which flow to invoke.

ALTER TABLE checkout_order
  ADD COLUMN mpgs_session_id       VARCHAR(128) NULL,
  ADD COLUMN mpgs_success_indicator VARCHAR(128) NULL,
  ADD COLUMN payment_method         VARCHAR(20)  NULL DEFAULT NULL;
