-- Add primary_image_url snapshot column to order_item so order details
-- can be rendered without lazily loading Product.media (which fails
-- outside a Hibernate session).
ALTER TABLE order_item ADD COLUMN primary_image_url VARCHAR(1024);
