-- YagoutPay hosted checkout integration.
-- payment_attempt counts how many times a gateway redirect payload has been
-- built for this order. YagoutPay requires order_no to be unique per
-- transaction request, so attempts after the first append "-<attempt>" to
-- the invoice number (e.g. GI-2026-00001-2).

ALTER TABLE checkout_order
  ADD COLUMN payment_attempt INT NOT NULL DEFAULT 0;
