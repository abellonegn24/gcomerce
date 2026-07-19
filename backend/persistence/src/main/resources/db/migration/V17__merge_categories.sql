-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V17: merge product categories
--
-- Adds seed products for the 4 new categories introduced in the
-- ProductCategory enum: ROASTED_COFFEE, OIL_SEED, LIVESTOCK, MINING.
--
-- Migrates the old PROCESSED_COFFEE category to ROASTED_COFFEE (the enum
-- value was removed from ProductCategory.java).
--
-- Also fixes the broken placeholder image URLs from V4 (which pointed to
-- a non-existent CDN host) by updating them to Unsplash source URLs that
-- render real product photos.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Migrate PROCESSED_COFFEE → ROASTED_COFFEE (enum value removed)
-- -----------------------------------------------------------------------------
UPDATE product SET category = 'ROASTED_COFFEE' WHERE category = 'PROCESSED_COFFEE';

-- -----------------------------------------------------------------------------
-- 1. Fix existing seed product image URLs (V4 used a fake CDN host)
-- -----------------------------------------------------------------------------
UPDATE product_media
SET url = CASE
              WHEN url LIKE '%/yirgacheffe-green-500g.jpg%'  THEN 'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=400&q=80'
              WHEN url LIKE '%/sidamo-medium-roast-250g.jpg%' THEN 'https://images.unsplash.com/photo-1773934926697-dfd9c67d93da?w=400&q=80'
              WHEN url LIKE '%/ivory-teff-flour-1kg.jpg%'     THEN 'https://images.unsplash.com/photo-1719785045940-938a0d735a78?w=400&q=80'
              WHEN url LIKE '%/berbere-spice-blend-100g.jpg%' THEN 'https://images.unsplash.com/photo-1565715279542-9104a91324ff?w=400&q=80'
              ELSE url
          END
WHERE url LIKE 'https://images.gadaaistar.com/placeholders/%';

-- -----------------------------------------------------------------------------
-- 2. Seed products for the 4 new categories
-- -----------------------------------------------------------------------------
INSERT INTO product
    (sku, slug, name, short_description, description, category,
     status, weight_kg, hs_code, origin_country, tax_class_id,
     base_currency, base_price, cost_price, min_order_quantity, packaging_notes)
SELECT * FROM (
    SELECT
        'GI-RC-HARAR-1KG'      AS sku,
        'harrar-roasted-1kg'   AS slug,
        'Harrar Longberry Roasted — 1 kg' AS name,
        'Naturally sun-dried Harrar, medium-dark roast with blueberry and wine notes.' AS short_description,
        'Single-origin Harrar longberry, dry-processed and roasted to a medium-dark (Full City) profile. Distinctive blueberry, wine, and chocolate notes. Whole bean, degassed 48 hours before packaging.' AS description,
        'ROASTED_COFFEE' AS category, 'ACTIVE' AS status, 1.020 AS weight_kg,
        '090121' AS hs_code, 'ET' AS origin_country,
        (SELECT id FROM tax_class WHERE code = 'STANDARD') AS tax_class_id,
        'USD' AS base_currency, 28.0000 AS base_price, 14.0000 AS cost_price,
        1.000 AS min_order_quantity,
        'Foil-lined 1 kg stand-up pouch with degassing valve.' AS packaging_notes
    UNION ALL SELECT
        'GI-OS-SESAME-50KG', 'ethiopian-sesame-50kg',
        'Ethiopian Humera Sesame — 50 kg',
        'Premium white Humera sesame seeds, export-grade, 99.5% purity.',
        'Hand-cleaned Humera sesame seeds from the Tigray region. Known for their light color and high oil content (52-54%). Packed in 50 kg jute bags for export. Lab test certificates available on request.',
        'OIL_SEED', 'ACTIVE', 50.000,
        '120740', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 95.0000, 55.0000, 1.000,
        '50 kg jute bag with inner PP liner.'
    UNION ALL SELECT
        'GI-LC-GOAT-1', 'ethiopian-highland-goat',
        'Ethiopian Highland Goat — Live',
        'Afar highland goats, raised free-range, 25-35 kg live weight.',
        'Ethiopian highland goats from the Afar and Somali regions. Hardy breed, raised free-range on natural pasture. Average live weight 25-35 kg. Health certificates and quarantine documentation provided for export.',
        'LIVESTOCK', 'ACTIVE', 30.000,
        '010410', 'ET',
        (SELECT id FROM tax_class WHERE code = 'STANDARD'),
        'USD', 120.0000, 70.0000, 1.000,
        'Live animal, transported in approved livestock carriers.'
    UNION ALL SELECT
        'GI-MN-OPAL-1', 'welo-ethiopian-opal',
        'Welo Ethiopian Opal — 1 ct',
        'Natural Welo (Wello) precious opal, 1 carat, fire play-of-color.',
        'Natural untreated Ethiopian Welo opal with vivid play-of-color (red, green, blue flashes). Mined in the Wello province. Each stone is certified by the Ethiopian Ministry of Mines. Sold per carat.',
        'MINING', 'ACTIVE', 0.001,
        '710310', 'ET', NULL,
        'USD', 350.0000, 180.0000, 1.000,
        'Individual gemstone in protective gem jar with certificate.'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku IN ('GI-RC-HARAR-1KG', 'GI-OS-SESAME-50KG', 'GI-LC-GOAT-1', 'GI-MN-OPAL-1'));

-- -----------------------------------------------------------------------------
-- 3. Primary images for the new products
-- -----------------------------------------------------------------------------
INSERT INTO product_media (product_id, url, alt_text, media_type, is_primary, sort_order)
SELECT p.id,
       CASE p.slug
           WHEN 'harrar-roasted-1kg'       THEN 'https://images.unsplash.com/photo-1773934926697-dfd9c67d93da?w=400&q=80'
           WHEN 'ethiopian-sesame-50kg'    THEN 'https://images.unsplash.com/photo-1549530708-be1b34900690?w=400&q=80'
           WHEN 'ethiopian-highland-goat'  THEN 'https://images.unsplash.com/photo-1706775576908-80cc9622dc94?w=400&q=80'
           WHEN 'welo-ethiopian-opal'      THEN 'https://images.unsplash.com/photo-1679395283817-39f56e249ea6?w=400&q=80'
       END,
       p.name,
       'IMAGE', TRUE, 0
FROM product p
WHERE p.sku IN ('GI-RC-HARAR-1KG', 'GI-OS-SESAME-50KG', 'GI-LC-GOAT-1', 'GI-MN-OPAL-1')
  AND NOT EXISTS (SELECT 1 FROM product_media pm WHERE pm.product_id = p.id AND pm.is_primary = TRUE);

-- -----------------------------------------------------------------------------
-- 4. Wholesale price entries for the new products
-- -----------------------------------------------------------------------------
INSERT INTO price_list_entry (price_list_id, product_id, price, min_quantity)
SELECT
    pl.id AS price_list_id,
    p.id  AS product_id,
    ROUND(p.base_price * (
        CASE g.code
            WHEN 'WHOLESALE_T1' THEN 0.80
            WHEN 'WHOLESALE_T2' THEN 0.70
            WHEN 'VIP'          THEN 0.65
        END
    ), 4) AS price,
    (CASE g.code
        WHEN 'WHOLESALE_T1' THEN 10.000
        WHEN 'WHOLESALE_T2' THEN 25.000
        WHEN 'VIP'          THEN 50.000
    END) AS min_quantity
FROM price_list pl
JOIN customer_group g ON g.id = pl.customer_group_id
CROSS JOIN product p
WHERE p.sku IN ('GI-RC-HARAR-1KG', 'GI-OS-SESAME-50KG', 'GI-LC-GOAT-1', 'GI-MN-OPAL-1')
  AND NOT EXISTS (
      SELECT 1 FROM price_list_entry ple
      WHERE ple.price_list_id = pl.id AND ple.product_id = p.id
  );

-- -----------------------------------------------------------------------------
-- 5. Inventory for the new products
-- -----------------------------------------------------------------------------
INSERT INTO inventory (product_id, quantity, reserved_quantity, restock_threshold)
SELECT p.id,
       (CASE p.category
           WHEN 'ROASTED_COFFEE' THEN 200.000
           WHEN 'OIL_SEED'       THEN 100.000
           WHEN 'LIVESTOCK'      THEN  50.000
           WHEN 'MINING'         THEN  30.000
       END),
       0.000,
       20.000
FROM product p
WHERE p.sku IN ('GI-RC-HARAR-1KG', 'GI-OS-SESAME-50KG', 'GI-LC-GOAT-1', 'GI-MN-OPAL-1')
  AND NOT EXISTS (SELECT 1 FROM inventory inv WHERE inv.product_id = p.id);
