-- =============================================================================
-- Gadaa Istar E-Commerce — Flyway V22: B2B per-ton catalog seed
--
-- Rebuilds the catalog around metric-ton export lots (the company's actual
-- trade unit) instead of retail-size consumer packs.
--
-- What it does:
--   1. Archives the V4/V17 retail-size seed products (500 g / 250 g / 1 kg /
--      50 kg / 100 g). They are ARCHIVED, not deleted: order_item.product_id
--      has no cascade and an existing order references GI-SP-BRB-100.
--   2. Seeds 66 products across RAW_COFFEE, ROASTED_COFFEE, WHITE_PRODUCTS,
--      SPICY_PRODUCTS and OIL_SEED. Each unit = 1 metric ton (1,000 kg),
--      so weight_kg = 1000 and base_price = USD per MT FOB Djibouti.
--   3. Attaches a verified, license-safe primary image per product
--      (Wikimedia Commons CC/PD or Unsplash, both hotlink-friendly and
--      free for commercial use; CC attribution is carried in alt_text).
--   4. Adds container-lot wholesale tiers: 19 MT (~1 x 20ft FCL of bagged
--      coffee) and 100 MT program pricing. Commodity-appropriate discounts
--      (3-11%), not the 20-35% retail-style cuts used by earlier seeds.
--   5. Seeds inventory and a sea-freight shipping method — the air-freight
--      rate card makes no sense for tonnage (1000 kg x $6.50/kg).
--
-- PRICE BASIS (researched 2026-11):
--   * ECTA minimum export prices, week of Oct 20, 2026 (TBX/tbx.et):
--       Natural G1 floors $/MT: Yirgacheffe/Guji/Sidamo/Limu/Bale 9,507;
--       Bench Maji/Lekempti 8,695; Jimma 8,289; Kafa 7,882; Sheka 7,984.
--   * Live Ethiopian exporter listings (ethiopianexportmarket.com, Oct 2026):
--       Yirgacheffe G1W $11,800, Kochere G1W $12,400, Gesha G1W $13,800,
--       Guji G1U $10,300, Sidamo G1W $9,400 / G1U $10,600, Limu G1W $8,700,
--       Harar G4U $4,800, Djimma G3U $5,600, Nekemte G2W $6,800, Kaffa G2W
--       $7,200, Bench Maji G3W $5,200, Bale G2W $7,000, Arsi G3U $5,400.
--   * ECTA roasted FOB (Oct 20, 2026): G1 $19,363, G2 $15,363, G3 $14,699,
--       G4 $13,227, G5 $10,153 per MT.
--   * Teff: Ministry of Trade export floor Feb 2026 — white teff $1,550/MT,
--       red teff $1,500/MT (biweekly reviews); listed here at prevailing
--       market levels.
--   * Pulses/cereals: Ethiopian exporter quotes — white pea beans $825-1,300,
--       kabuli chickpeas ~$1,100-1,300, fava ~$700-800, sorghum ~$380-480.
--   * Sesame: ECX whitish Humera avg $1,369/MT (Sep 2026) -> FOB ~$1,650.
--   * Spices: korerima farmgate 1,100-1,500 ETB/kg, Addis retail 2,700-2,900
--       ETB/kg (Oct 2026) -> export ~$12.5k/MT; ginger/turmeric/peppers
--       indexed to regional FOB benchmarks; blends carry value-add premium.
--
-- Idempotent: every INSERT is guarded (NOT EXISTS / ON CONFLICT) so a fresh
-- DB and an already-seeded DB both converge safely.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Fix the V17 opal image — it currently shows an amethyst, not an opal.
--    Replace with a genuine Welo (Ethiopian) precious opal photo.
-- -----------------------------------------------------------------------------
UPDATE product_media pm
SET url                  = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a2/Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg/960px-Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg',
    card_thumbnail_url   = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a2/Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg/500px-Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg',
    detail_thumbnail_url = 'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a2/Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg/960px-Opal_szlachetny_Welo_-_Afar_Province%2C_Etiopia%2C_Afryka.jpg',
    alt_text             = 'Welo Ethiopian opal with play-of-color (photo: Lech Darski, CC BY-SA 4.0, Wikimedia Commons)'
WHERE pm.product_id = (SELECT id FROM product WHERE sku = 'GI-MN-OPAL-1')
  AND pm.is_primary = TRUE;

-- -----------------------------------------------------------------------------
-- 1. Archive retail-size seed products (order history stays intact)
-- -----------------------------------------------------------------------------
UPDATE product SET status = 'ARCHIVED'
WHERE sku IN (
    'GI-RC-YRG-500',       -- Yirgacheffe Green Coffee - 500 g
    'GI-PC-SDMO-250',      -- Sidamo Medium Roast - 250 g
    'GI-WP-TEFF-1KG',      -- Ivory Teff Flour - 1 kg
    'GI-SP-BRB-100',       -- Berbere Spice Blend - 100 g (has order ref)
    'GI-RC-HARAR-1KG',     -- Harrar Longberry Roasted - 1 kg
    'GI-OS-SESAME-50KG'    -- Humera Sesame - 50 kg (superseded by 1 MT lot)
);

-- -----------------------------------------------------------------------------
-- 2. Per-ton products. Unit = 1 metric ton; weight_kg = 1000; USD/MT FOB.
-- -----------------------------------------------------------------------------
INSERT INTO product
    (sku, slug, name, short_description, description, category,
     status, weight_kg, hs_code, origin_country, tax_class_id,
     base_currency, base_price, cost_price, min_order_quantity, packaging_notes)
SELECT * FROM (
    -- =========================== RAW_COFFEE (green) ==========================
    SELECT
        'GI-RC-YRG-WG1-MT' AS sku, 'yirgacheffe-washed-g1-1mt' AS slug,
        'Yirgacheffe Washed G1 Green — 1 MT' AS name,
        'Top-grade washed Yirgacheffe: jasmine, bergamot, lemon-citrus acidity.' AS short_description,
        'ECX/ECTA Grade 1 washed lot from Gedeo Zone washing stations (1,700-2,200 m). Clean floral cup with tea-like body; moisture 10-12%, screen 14-17. FOB Djibouti.' AS description,
        'RAW_COFFEE' AS category, 'ACTIVE' AS status, 1000.000 AS weight_kg,
        '090111' AS hs_code, 'ET' AS origin_country,
        (SELECT id FROM tax_class WHERE code = 'EXPORT') AS tax_class_id,
        'USD' AS base_currency, 11800.0000 AS base_price, 7200.0000 AS cost_price,
        1.000 AS min_order_quantity,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT, palletized. FOB Djibouti.' AS packaging_notes
    UNION ALL SELECT
        'GI-RC-YRG-WG2-MT', 'yirgacheffe-washed-g2-1mt',
        'Yirgacheffe Washed G2 Green — 1 MT',
        'Washed Yirgacheffe Grade 2 — the specialty-roaster workhorse lot.',
        'Grade 2 washed Yirgacheffe: bright citrus and stone-fruit cup, dependable blend or single-origin base. Sun-dried on raised beds, hand-sorted. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 9600.0000, 5900.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-KOC-WG1-MT', 'kochere-washed-g1-1mt',
        'Kochere Washed G1 Green — 1 MT',
        'Kochere micro-region washed G1 — peak Yirgacheffe-zone cup quality.',
        'Kochere (Yirgacheffe district) washed Grade 1: intense jasmine and bergamot aromatics, sparkling acidity, silky body. Premium micro-region lot for competition-grade roasters. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 12400.0000, 7600.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-YRG-NG1-MT', 'yirgacheffe-natural-g1-1mt',
        'Yirgacheffe Natural G1 Green — 1 MT',
        'Dry-processed Yirgacheffe: blueberry, strawberry, wine-like sweetness.',
        'Grade 1 natural (sun-dried whole cherry) from Gedeo Zone smallholders. Fruit-forward cup with heavy sweetness and floral finish; raised-bed dried 15-21 days. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 10500.0000, 6400.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GUJ-NG1-MT', 'guji-natural-g1-1mt',
        'Guji Natural G1 Green — 1 MT',
        'The blueberry-bomb origin: Guji natural at Grade 1, 88+ cup.',
        'Natural-processed Grade 1 from Guji Zone (Oromia), 1,800-2,300 m. Explosive berry, tropical fruit and wine complexity; the current demand leader among Ethiopian naturals. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 10300.0000, 6300.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GUJ-WG1-MT', 'guji-washed-g1-1mt',
        'Guji Washed G1 Green — 1 MT',
        'Washed Guji G1 — clean stone fruit, florals, refined acidity.',
        'Grade 1 washed lot from Guji Zone stations. Apricot, peach and floral notes with a cleaner, brighter cup than the naturals; excellent for filter programs. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 9900.0000, 6100.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GUJ-WG2-MT', 'guji-washed-g2-1mt',
        'Guji Washed G2 Green — 1 MT',
        'Washed Guji Grade 2 — specialty quality at a mid-tier price.',
        'Washed Grade 2 Guji: sweet citrus, caramel, gentle florals. Strong value for single-origin filter or premium espresso components. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 9100.0000, 5600.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GUJ-NG3-MT', 'guji-natural-g3-1mt',
        'Guji Natural G3 Green — 1 MT',
        'Commercial Guji natural — fruity base for blends and dark roasts.',
        'Grade 3 natural Guji: berry sweetness with rustic edge. Ideal espresso-blend fruit component or volume single-origin for value programs. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 6300.0000, 3900.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-SID-WG1-MT', 'sidama-washed-g1-1mt',
        'Sidama Washed G1 Green — 1 MT',
        'Washed Sidama G1 — balanced citrus, honey, chocolate undertone.',
        'Grade 1 washed lot from Sidama Region cooperatives (1,500-2,100 m). The versatile all-rounder: works for filter, espresso and blends alike. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 9400.0000, 5800.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-SID-NG1-MT', 'sidama-natural-g1-1mt',
        'Sidama Natural G1 Green — 1 MT',
        'Natural Sidama G1 — blueberry, jammy sweetness, creamy body.',
        'Sun-dried natural Grade 1 from Sidama smallholders. Intense berries and candied fruit over a creamy body; a favourite for fruit-forward espresso. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 10600.0000, 6500.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-SID-WG2-MT', 'sidama-washed-g2-1mt',
        'Sidama Washed G2 Green — 1 MT',
        'Washed Sidama Grade 2 — clean, sweet, dependable volume lot.',
        'Grade 2 washed Sidama: soft citrus, milk chocolate, balanced cup. The highest-volume washed export region — consistent container programs. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 8200.0000, 5000.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-SID-NG2-MT', 'sidama-natural-g2-1mt',
        'Sidama Natural G2 Green — 1 MT',
        'Natural Sidama Grade 2 — fruit character at a blender price.',
        'Grade 2 natural Sidama: sweet berry and cocoa notes with wider grade tolerance than G1. Strong value component for natural-blend programs. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 8600.0000, 5300.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-HRR-NG1-MT', 'harar-natural-g1-1mt',
        'Harar Natural G1 Green — 1 MT',
        'Harar longberry natural — mocha, blueberry, wild spice heritage cup.',
        'Grade 1 sun-dried Harrar from the eastern highlands (Harari Region). The original mocha profile: blueberry, dark chocolate, red wine and spice. Always dry-processed. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 8900.0000, 5500.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-HRR-NG4-MT', 'harar-natural-g4-1mt',
        'Harar Natural G4 Green — 1 MT',
        'Commercial Harar natural — classic mocha blend base, value grade.',
        'Grade 4 dry-processed Harrar: chocolate and wine notes with commercial grading. Traditional GCC and espresso blend staple; the value entry into the Harar profile. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 4800.0000, 3000.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-LIM-WG1-MT', 'limu-washed-g1-1mt',
        'Limu Washed G1 Green — 1 MT',
        'Washed Limu G1 — sweet spiced cup, winey acidity, round body.',
        'Grade 1 washed Limu (Jimma Zone, western Oromia, 1,400-2,000 m). Spicy-winey character prized by European roasters; balanced and elegant. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 8700.0000, 5300.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-LIM-WG2-MT', 'limu-washed-g2-1mt',
        'Limu Washed G2 Green — 1 MT',
        'Washed Limu Grade 2 — reliable mid-roast workhorse.',
        'Grade 2 washed Limu: chocolate, soft spice, low acidity. Consistent lot for blends and single-origin drip programs. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 7900.0000, 4800.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-JIM-WG2-MT', 'jimma-washed-g2-1mt',
        'Jimma Washed G2 Green — 1 MT',
        'Washed Jimma Grade 2 — Ethiopia''s volume origin, clean base cup.',
        'Grade 2 washed Djimma from the largest producing zone. Nutty-chocolate, low-acid cup; the price-competitive backbone for commercial blends. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 6500.0000, 4000.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-JIM-NG5-MT', 'jimma-natural-g5-1mt',
        'Jimma Natural G5 Green — 1 MT',
        'Commercial Djimma sun-dried — espresso and instant-coffee base.',
        'Grade 5 unwashed Djimma: Ethiopia''s most price-competitive export grade. Full-bodied, earthy chocolate for commercial roasting, blends and soluble coffee. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 4600.0000, 2900.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-LEK-WG1-MT', 'lekempti-washed-g1-1mt',
        'Lekempti (Nekemte) Washed G1 Green — 1 MT',
        'Washed Nekemte G1 — Welega origin, fruity-sweet, medium body.',
        'Grade 1 washed lot from East Welega (Lekempti/Nekemte). Fruit-accented cup with pleasant wildness and medium body; strong performer in European accounts. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 7900.0000, 4800.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-KAF-WG2-MT', 'kaffa-washed-g2-1mt',
        'Kaffa Forest Washed G2 Green — 1 MT',
        'Washed Kaffa — coffee''s birthplace, forest-grown heirloom cup.',
        'Grade 2 washed coffee from the Kaffa Zone, origin of Coffea arabica. Wild-grown heirloom varieties give a winey, perfumed cup with soft citrus. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 7200.0000, 4400.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-BMJ-WG3-MT', 'bench-maji-washed-g3-1mt',
        'Bench Maji Washed G3 Green — 1 MT',
        'Washed Bench Maji Grade 3 — soft, sweet southwestern lot.',
        'Grade 3 washed coffee from Bench Sheko Zone. Mild, sweet, tea-like cup suited to blends and mid-market single origins. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5200.0000, 3200.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-BAL-WG2-MT', 'bale-washed-g2-1mt',
        'Bale Washed G2 Green — 1 MT',
        'Washed Bale Grade 2 — highland Oromia lot, sweet citrus cup.',
        'Grade 2 washed Bale from the Bale highlands of southeast Oromia. Balanced sweet-citrus cup from smallholder garden coffee; rising export origin. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 7000.0000, 4300.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-ARS-NG3-MT', 'west-arsi-natural-g3-1mt',
        'West Arsi Natural G3 Green — 1 MT',
        'West Arsi natural — fruit-forward Oromia lot, growing reputation.',
        'Grade 3 natural from West Arsi Zone (bordering Sidama and Guji). Berry and chocolate notes; increasingly shipped as a value natural for espresso blends. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5400.0000, 3300.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GIM-WG2-MT', 'gimbi-washed-g2-1mt',
        'Gimbi Washed G2 Green — 1 MT',
        'Washed Gimbi Grade 2 — western Welega, bold cup for blends.',
        'Grade 2 washed lot from Gimbi (West Welega Zone). Bold, chocolatey cup with the classic western profile; steady blend component at competitive pricing. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 6800.0000, 4200.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-GES-WG1-MT', 'gesha-village-washed-g1-1mt',
        'Gesha (Gori Gesha) Washed G1 Green — 1 MT',
        'Ethiopia''s crown jewel — wild Gesha variety, auction-grade cup.',
        'Grade 1 washed Gori Gesha from Bench Maji forests — the ancestral variety behind Panama''s famous Geisha. Jasmine, honeysuckle and stone fruit; the flagship lot. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 13800.0000, 8500.0000, 1.000,
        '60 kg jute bags with GrainPro liners; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-TEP-NG3-MT', 'tepi-natural-g3-1mt',
        'Tepi Natural G3 Green — 1 MT',
        'Tepi sun-dried — Sheka Zone commercial natural, earthy-sweet.',
        'Grade 3 unwashed Tepi from Sheka Zone. Heavy body, earthy sweetness; commercial natural for blends and dark roasts. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5600.0000, 3500.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    UNION ALL SELECT
        'GI-RC-BBK-NG3-MT', 'bebeka-natural-g3-1mt',
        'Bebeka Natural G3 Green — 1 MT',
        'Bebeka plantation natural — uniform commercial unwashed lot.',
        'Grade 3 natural from the Bebeka estate region (Bench Sheko). Plantation-grown coffee with uniform sizing; reliable commercial program lot. FOB Djibouti.',
        'RAW_COFFEE', 'ACTIVE', 1000.000, '090111', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5500.0000, 3400.0000, 1.000,
        '60 kg jute bags; ~17 bags/MT. FOB Djibouti.'
    -- ======================== ROASTED_COFFEE (whole bean) ====================
    UNION ALL SELECT
        'GI-PC-YRG-G1-MT', 'yirgacheffe-roasted-g1-1mt',
        'Yirgacheffe Roasted Whole Bean G1 — 1 MT',
        'Fresh-roasted Yirgacheffe G1 — floral, bright, export-ready.',
        'Washed Yirgacheffe G1 roasted to order in Addis Ababa (light-medium profile). Whole bean, degassed and packed with one-way valves for export. ECTA roasted-G1 benchmark ~$19.4k/MT. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 19400.0000, 12800.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-GUJ-G1-MT', 'guji-roasted-g1-1mt',
        'Guji Roasted Whole Bean G1 — 1 MT',
        'Roasted Guji G1 natural — blueberry and wine, espresso-ready.',
        'Natural Guji G1 roasted medium for espresso and filter. Whole bean packed immediately after roast; the fruit-forward flagship of our roasted range. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 19000.0000, 12500.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-SID-G1-MT', 'sidama-roasted-g1-1mt',
        'Sidama Roasted Whole Bean G1 — 1 MT',
        'Roasted Sidama G1 — the balanced all-purpose export roast.',
        'Washed Sidama G1 roasted to a medium (City+) profile: chocolate, berry, citrus balance. Suits filter, French press and espresso programs. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 18500.0000, 12200.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-HRR-G1-MT', 'harrar-roasted-g1-1mt',
        'Harrar Roasted Whole Bean G1 — 1 MT',
        'Roasted Harrar — the classic mocha cup for espresso and Turkish.',
        'Dry-processed Harrar G1 roasted medium-dark: blueberry, chocolate and red-wine notes. Whole bean for espresso bars and Middle East markets. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 18000.0000, 11900.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-LIM-G2-MT', 'limu-roasted-g2-1mt',
        'Limu Roasted Whole Bean G2 — 1 MT',
        'Roasted Limu G2 — sweet spice, everyday specialty roast.',
        'Washed Limu G2 roasted medium: chocolate, warm spice, rounded acidity. Value-priced whole-bean program for distributors and café chains. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 15400.0000, 10200.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-JEB-G3-MT', 'jebena-espresso-blend-g3-1mt',
        'Jebena Espresso Blend Roasted G3 — 1 MT',
        'House espresso blend — Djimma/Harar base, crema-rich, dark roast.',
        'Our B2B espresso workhorse: Djimma, Harar and Guji naturals blended and roasted dark for thick crema and chocolate body. Whole bean. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 14700.0000, 9700.0000, 1.000,
        '25 kg nitrogen-flushed foil bags with degassing valves; palletized ~40 bags/MT.'
    UNION ALL SELECT
        'GI-PC-JIM-G4-MT', 'jimma-roasted-ground-g4-1mt',
        'Jimma Roasted Ground Coffee G4 — 1 MT',
        'Ground roasted Djimma — the traditional jebena-buna roast.',
        'Djimma arabica roasted dark and ground to the classic Ethiopian ceremony profile (fine-medium). Packed for Turkish coffee and jebena brewing. FOB Djibouti.',
        'ROASTED_COFFEE', 'ACTIVE', 1000.000, '090121', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 13200.0000, 8700.0000, 1.000,
        '25 kg vacuum-sealed foil bags, ground; palletized ~40 bags/MT.'
    -- ============================ WHITE_PRODUCTS =============================
    UNION ALL SELECT
        'GI-WP-TEFF-GRN-MT', 'white-teff-grain-1mt',
        'White (Ivory) Teff Grain — 1 MT',
        'Ivory teff — the premium Ethiopian supergrain, export-legal quota.',
        'Machine-cleaned ivory teff (Eragrostis tef) from Amhara and Oromia belts. Naturally gluten-free; prized for injera and health foods. Ministry export floor $1,550/MT; premium grain listed at market. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '100890', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1850.0000, 1200.0000, 1.000,
        '50 kg new PP bags; moisture ≤12%, fumigated; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-TEFF-FLR-MT', 'ivory-teff-flour-1mt',
        'Ivory Teff Flour — 1 MT',
        'Stone-milled ivory teff flour — injera-grade, gluten-free.',
        'Fine-milled ivory teff flour for injera production and gluten-free baking lines. Milled to order, food-grade packaging. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '110290', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2450.0000, 1600.0000, 1.000,
        '25 kg food-grade kraft bags with PE liner; palletized.'
    UNION ALL SELECT
        'GI-WP-NAVY-BEAN-MT', 'white-pea-beans-1mt',
        'White Pea Beans (Navy) — 1 MT',
        'Ethiopian white pea beans — HPS export grade, 99% purity.',
        'Round white pea beans (Phaseolus vulgaris) from Arsi and Shoa. Machine-cleaned and hand-picked: purity ≥99%, moisture ≤14%, weevil-free, fumigated. Top Ethiopian pulse export. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '071333', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1050.0000, 700.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT; HPS or machine-cleaned on request.'
    UNION ALL SELECT
        'GI-WP-CHICKPEA-MT', 'kabuli-chickpeas-1mt',
        'Kabuli Chickpeas — 1 MT',
        'Large-cream kabuli chickpeas — Middle East and India demand.',
        'Kabuli-type chickpeas from Debre Zeit and surrounding belts. Cream seed coat, 7-9 mm caliber, moisture ≤12%, machine-cleaned. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '071320', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1200.0000, 800.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-FAVA-MT', 'fava-beans-1mt',
        'Fava Beans (Horse Beans) — 1 MT',
        'Fava beans — Egypt-bound staple, Ethiopian highland crop.',
        'Whole dried fava beans (Vicia faba) for foul manufacturing and food processing. Highland-grown, machine-cleaned, moisture ≤14%. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '071350', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 780.0000, 520.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-LENTIL-MT', 'red-lentils-1mt',
        'Red Lentils (Split) — 1 MT',
        'Ethiopian red lentils — fast-cooking split for food processors.',
        'Split red lentils from Central Ethiopian farms. Bright orange cotyledon, low admixture, machine-cleaned; staple for South Asian and Middle East trade. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '071340', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 950.0000, 630.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-MUNG-MT', 'mung-beans-1mt',
        'Mung Beans — 1 MT',
        'Green mung beans — sprouting-grade, Asian market staple.',
        'Whole green mung beans (Vigna radiata), export-cleaned and calibrated. Suitable for sprouting and food manufacturing; moisture ≤12%. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '071331', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1100.0000, 730.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-SORGHUM-MT', 'white-sorghum-1mt',
        'White Sorghum — 1 MT',
        'White food-grade sorghum — Sudan-route staple grain.',
        'White sorghum grain from eastern lowland belts, non-GMO. Machine-cleaned, moisture ≤12%; food and feed grade available. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '100790', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 450.0000, 300.0000, 1.000,
        '50 kg new PP bags or bulk in container liner; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-MAIZE-MT', 'white-maize-1mt',
        'White Maize — 1 MT',
        'White maize grain — food-grade, East African demand.',
        'Dried white maize from Oromia and SNNPR surplus belts. Cleaned, moisture ≤13%, aflatoxin-tested for food use. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '100590', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 380.0000, 255.0000, 1.000,
        '50 kg new PP bags or bulk in container liner; 20 bags/MT.'
    UNION ALL SELECT
        'GI-WP-HON-WHT-MT', 'tigray-white-honey-1mt',
        'Tigray White Honey — 1 MT',
        'Famous Tigray white honey — creamy, mild, premium export honey.',
        'Naturally creamed white honey from Tigray highlands where bees forage on tebeb (Basilicum). Mild flavour, butter-like texture; the premium Ethiopian honey for Gulf and EU buyers. Moisture ≤18%. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '040900', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 9500.0000, 6200.0000, 1.000,
        'New 300 kg food-grade steel drums; ~3.3 drums/MT.'
    UNION ALL SELECT
        'GI-WP-HON-MLT-MT', 'multiflora-honey-1mt',
        'Multi-Flora Amber Honey — 1 MT',
        'Highland multi-flora honey — versatile amber table honey.',
        'Amber multi-flora honey from Oromia and Amhara apiaries. Floral, balanced sweetness for retail packing and food ingredient use. Moisture ≤18%, HMF ≤15 mg/kg. FOB Djibouti.',
        'WHITE_PRODUCTS', 'ACTIVE', 1000.000, '040900', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5200.0000, 3400.0000, 1.000,
        'New 300 kg food-grade steel drums; ~3.3 drums/MT.'
    -- ============================ SPICY_PRODUCTS =============================
    UNION ALL SELECT
        'GI-SP-KOR-PD-MT', 'korerima-pods-1mt',
        'Korerima Pods (Ethiopian Cardamom) — 1 MT',
        'Aframomum corrorima — Ethiopia''s native cardamom, Kaffa forests.',
        'Whole sun-dried korerima (Ethiopian cardamom) pods from Kaffa, Sheka and Illubabor forests. Smoky, resinous, essential for berbere and niter kibbeh; also flavours coffee. Strong Gulf and Sudan demand. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090831', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 12500.0000, 8000.0000, 1.000,
        '25 kg jute/PP bags with PE liner; sorted whole capsules.'
    UNION ALL SELECT
        'GI-SP-BCUM-MT', 'black-cumin-tikur-azmud-1mt',
        'Black Cumin Seed (Tikur Azmud) — 1 MT',
        'Nigella sativa seeds — tikur azmud, bakery and remedy staple.',
        'Cleaned black cumin (nigella) seed from northern highlands. Peppery, oregano-like flavour used on breads and in spice blends; growing nutraceutical demand. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090999', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2900.0000, 1900.0000, 1.000,
        '25 kg PP bags with PE liner; purity ≥99%.'
    UNION ALL SELECT
        'GI-SP-AJW-MT', 'ajwain-nech-azmud-1mt',
        'Ajwain Seed (Nech Azmud) — 1 MT',
        'Bishop''s weed / ajwain — thyme-like seed for flatbreads.',
        'Nech azmud (Trachyspermum ammi) seed, sun-dried and cleaned. Thymol-rich seeds used in injera and bread; the Amharic twin of Indian ajwain. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090962', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2200.0000, 1400.0000, 1.000,
        '25 kg PP bags with PE liner; purity ≥99%.'
    UNION ALL SELECT
        'GI-SP-FEN-MT', 'fenugreek-abish-1mt',
        'Fenugreek Seed (Abish) — 1 MT',
        'Abish fenugreek — maple-sweet seed for wot and berbere.',
        'Cleaned fenugreek seed (Trigonella foenum-graecum), Amharic abish. Bittersweet maple notes; a base spice of berbere and legume stews. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091099', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1500.0000, 980.0000, 1.000,
        '25 kg PP bags with PE liner; purity ≥99%.'
    UNION ALL SELECT
        'GI-SP-COR-MT', 'coriander-dimbilal-1mt',
        'Coriander Seed (Dimbilal) — 1 MT',
        'Whole coriander seed — citrusy backbone of Ethiopian blends.',
        'Dried coriander seed (Coriandrum sativum), Amharic dimbilal. Citrus-peel aroma for berbere, mitmita and sauces; machine-cleaned export grade. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090921', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1300.0000, 850.0000, 1.000,
        '25 kg PP bags with PE liner; purity ≥99%.'
    UNION ALL SELECT
        'GI-SP-LPEP-MT', 'long-pepper-timiz-1mt',
        'Long Pepper (Timiz) — 1 MT',
        'Piper longum spikes — the ancient timiz pepper of wot stews.',
        'Dried long pepper spikes from southern forest farms. Hotter and more complex than black pepper; used in mitmita and classic recipes. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090411', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 4200.0000, 2700.0000, 1.000,
        '25 kg PP bags with PE liner; whole dried catkins.'
    UNION ALL SELECT
        'GI-SP-BPEP-MT', 'black-pepper-kundo-1mt',
        'Black Pepper (Kundo Berbere) — 1 MT',
        'Ethiopian black pepper — hot-sweet kundo berbere berries.',
        'Whole dried black pepper berries from southern garden plots. Sharp heat with sweet pine notes; steam-treated on request for microbiology specs. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090411', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 7000.0000, 4500.0000, 1.000,
        '25 kg PP bags with PE liner; 550 g/l bulk density.'
    UNION ALL SELECT
        'GI-SP-GING-MT', 'dried-ginger-zinjibil-1mt',
        'Dried Ginger (Zinjibil) — 1 MT',
        'Sun-dried ginger rhizomes — the everyday Ethiopian spice.',
        'Whole dried ginger rhizomes (Zingiber officinale), Amharic zinjibil. Spicy-citrus warmth for teas, stews and blends; split or whole per spec. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091011', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2200.0000, 1400.0000, 1.000,
        '25 kg PP bags with PE liner; moisture ≤12%.'
    UNION ALL SELECT
        'GI-SP-TURF-MT', 'turmeric-fingers-ird-1mt',
        'Turmeric Fingers (Ird) — 1 MT',
        'Whole dried turmeric fingers — Ethiopia''s top spice export.',
        'Cured turmeric fingers (Curcuma longa), Amharic ird. Deep orange flesh, 4-5% curcumin; Ethiopia''s leading spice export to India and the Gulf. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091030', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1900.0000, 1250.0000, 1.000,
        '25 kg PP bags with PE liner; polished fingers.'
    UNION ALL SELECT
        'GI-SP-TURP-MT', 'turmeric-powder-1mt',
        'Turmeric Powder — 1 MT',
        'Ground turmeric — food-processor grade, steam-treated option.',
        'Freshly ground Ethiopian turmeric powder, bright orange, for seasoning blenders and food industry. Mesh size to spec; ETO/steam treatment available. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091030', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2400.0000, 1550.0000, 1.000,
        '25 kg multiwall kraft/PP bags with PE liner.'
    UNION ALL SELECT
        'GI-SP-BRB-BLD-MT', 'berbere-blend-1mt',
        'Berbere Spice Blend — 1 MT',
        'The national blend — chili, garlic, korerima and 12+ spices.',
        'Traditional Ethiopian berbere: sun-dried red chilies with garlic, ginger, korerima, fenugreek, rue and long pepper. Stone-ground in Addis, packed fresh within 30 days of milling. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091099', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5200.0000, 3300.0000, 1.000,
        '25 kg multiwall kraft/PP bags with PE liner; custom recipes on request.'
    UNION ALL SELECT
        'GI-SP-MIT-MT', 'mitmita-blend-1mt',
        'Mitmita Spice Blend — 1 MT',
        'Fiery mitmita — chili, korerima and salt for kitfo and tibs.',
        'Hot finishing blend of bird''s-eye chili, korerima, cardamom and salt. Finer and hotter than berbere; for meat dishes, dips and snack seasoning. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '091099', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 5800.0000, 3700.0000, 1.000,
        '25 kg multiwall kraft/PP bags with PE liner.'
    UNION ALL SELECT
        'GI-SP-SHR-MT', 'shiro-powder-1mt',
        'Shiro Powder — 1 MT',
        'Shiro — spiced legume flour for Ethiopia''s beloved stew.',
        'Ready-mixed shiro: finely ground roasted chickpeas/peas blended with garlic, onion, berbere and salt. Bulk food-service and repacking grade. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '210690', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 3200.0000, 2050.0000, 1.000,
        '25 kg multiwall kraft/PP bags with PE liner.'
    UNION ALL SELECT
        'GI-SP-CHILI-MT', 'dried-chili-pods-1mt',
        'Dried Chili Pods (Mareko) — 1 MT',
        'Sun-dried Ethiopian chili pods — the raw base of berbere.',
        'Whole sun-dried red chili pods (Capsicum), the mareko peppers used to make berbere. Deep red colour, medium-high heat, stem-on or stemless. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090421', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2400.0000, 1550.0000, 1.000,
        '25 kg PP bags; moisture ≤11%; stemless option available.'
    UNION ALL SELECT
        'GI-SP-CFLK-MT', 'crushed-chili-flakes-1mt',
        'Crushed Chili Flakes — 1 MT',
        'Crushed Ethiopian chili — pizza flakes and seasoning base.',
        'Coarse-crushed dried chili with seeds, bright red. For pizza-style flakes, sauces and spice blending; heat level to spec. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '090422', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2600.0000, 1700.0000, 1.000,
        '25 kg PP bags with PE liner; 2-4 mm cut.'
    UNION ALL SELECT
        'GI-SP-RUE-MT', 'rue-seed-tena-adam-1mt',
        'Rue Seed (Tena Adam) — 1 MT',
        'Ruta chalepensis — the ''herb of grace'' for coffee ceremony.',
        'Dried rue seeds/leaf from Tigray and highland gardens. Traditionally added to coffee and used medicinally; niche but consistent export demand. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '121190', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 2800.0000, 1800.0000, 1.000,
        '25 kg PP bags with PE liner; shade-dried.'
    UNION ALL SELECT
        'GI-SP-MUST-MT', 'ethiopian-mustard-senafich-1mt',
        'Ethiopian Mustard Seed (Senafich) — 1 MT',
        'Senafich — Ethiopian kale-seed mustard for sauces.',
        'Brown mustard-type seed (senafich) from highland plots. Pungent, peppery; used in traditional sauces and dressings. FOB Djibouti.',
        'SPICY_PRODUCTS', 'ACTIVE', 1000.000, '120750', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1100.0000, 720.0000, 1.000,
        '25 kg PP bags with PE liner; purity ≥99%.'
    -- =============================== OIL_SEED ================================
    UNION ALL SELECT
        'GI-OS-SES-HUM-MT', 'humera-sesame-1mt',
        'Humera Whitish Sesame — 1 MT',
        'Premium Humera-type whitish sesame — ECX top grade.',
        'Whitish sesame from the Humera belt (northwest Tigray), the benchmark Ethiopian sesame. 99.5% purity, oil content 52-54%, machine-cleaned, hand-picked option. ECX Sep-2026 avg $1,369/MT; FOB Djibouti.',
        'OIL_SEED', 'ACTIVE', 1000.000, '120740', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1650.0000, 1100.0000, 1.000,
        '50 kg new PP/jute bags; 20 bags/MT; lab certificate on request.'
    UNION ALL SELECT
        'GI-OS-SES-WOL-MT', 'wollega-sesame-1mt',
        'Wollega Sesame — 1 MT',
        'Wollega mixed sesame — reliable tahini and oil-crush grade.',
        'Sesame from Wollega (western Oromia), slightly darker than Humera but high in oil. Crushing and tahini grade; machine-cleaned, moisture ≤7%. FOB Djibouti.',
        'OIL_SEED', 'ACTIVE', 1000.000, '120740', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1400.0000, 950.0000, 1.000,
        '50 kg new PP/jute bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-OS-NIG-MT', 'niger-seed-noug-1mt',
        'Niger Seed (Noug) — 1 MT',
        'Noug — Ethiopia''s native oilseed for oil mills and birdseed.',
        'Niger seed (Guizotia abyssinica), Amharic noug, from highland Oromia. 35-45% oil for expelling; also birdseed grade for EU/US. Machine-cleaned, moisture ≤10%. FOB Djibouti.',
        'OIL_SEED', 'ACTIVE', 1000.000, '120799', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1450.0000, 960.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
    UNION ALL SELECT
        'GI-OS-FLX-MT', 'linseed-telba-1mt',
        'Linseed / Flaxseed (Telba) — 1 MT',
        'Telba — Ethiopian flaxseed, brown, high omega-3 oil seed.',
        'Brown linseed (Linum usitatissimum), Amharic telba, from northern highlands. Food and crush grade; omega-3 rich. Machine-cleaned, moisture ≤10%. FOB Djibouti.',
        'OIL_SEED', 'ACTIVE', 1000.000, '120400', 'ET',
        (SELECT id FROM tax_class WHERE code = 'EXPORT'),
        'USD', 1050.0000, 700.0000, 1.000,
        '50 kg new PP bags; 20 bags/MT.'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku IN (
    'GI-RC-YRG-WG1-MT','GI-RC-YRG-WG2-MT','GI-RC-KOC-WG1-MT','GI-RC-YRG-NG1-MT',
    'GI-RC-GUJ-NG1-MT','GI-RC-GUJ-WG1-MT','GI-RC-GUJ-WG2-MT','GI-RC-GUJ-NG3-MT',
    'GI-RC-SID-WG1-MT','GI-RC-SID-NG1-MT','GI-RC-SID-WG2-MT','GI-RC-SID-NG2-MT',
    'GI-RC-HRR-NG1-MT','GI-RC-HRR-NG4-MT','GI-RC-LIM-WG1-MT','GI-RC-LIM-WG2-MT',
    'GI-RC-JIM-WG2-MT','GI-RC-JIM-NG5-MT','GI-RC-LEK-WG1-MT','GI-RC-KAF-WG2-MT',
    'GI-RC-BMJ-WG3-MT','GI-RC-BAL-WG2-MT','GI-RC-ARS-NG3-MT','GI-RC-GIM-WG2-MT',
    'GI-RC-GES-WG1-MT','GI-RC-TEP-NG3-MT','GI-RC-BBK-NG3-MT',
    'GI-PC-YRG-G1-MT','GI-PC-GUJ-G1-MT','GI-PC-SID-G1-MT','GI-PC-HRR-G1-MT',
    'GI-PC-LIM-G2-MT','GI-PC-JEB-G3-MT','GI-PC-JIM-G4-MT',
    'GI-WP-TEFF-GRN-MT','GI-WP-TEFF-FLR-MT','GI-WP-NAVY-BEAN-MT','GI-WP-CHICKPEA-MT',
    'GI-WP-FAVA-MT','GI-WP-LENTIL-MT','GI-WP-MUNG-MT','GI-WP-SORGHUM-MT',
    'GI-WP-MAIZE-MT','GI-WP-HON-WHT-MT','GI-WP-HON-MLT-MT',
    'GI-SP-KOR-PD-MT','GI-SP-BCUM-MT','GI-SP-AJW-MT','GI-SP-FEN-MT','GI-SP-COR-MT',
    'GI-SP-LPEP-MT','GI-SP-BPEP-MT','GI-SP-GING-MT','GI-SP-TURF-MT','GI-SP-TURP-MT',
    'GI-SP-BRB-BLD-MT','GI-SP-MIT-MT','GI-SP-SHR-MT','GI-SP-CHILI-MT','GI-SP-CFLK-MT',
    'GI-SP-RUE-MT','GI-SP-MUST-MT',
    'GI-OS-SES-HUM-MT','GI-OS-SES-WOL-MT','GI-OS-NIG-MT','GI-OS-FLX-MT'
));

-- -----------------------------------------------------------------------------
-- 3. Primary images — verified photos of the actual product type.
--    Sources: Wikimedia Commons (CC / public domain — attribution carried in
--    alt_text) and Unsplash (free licence, no attribution required).
--    Wikimedia hotlinked thumbs only serve canonical widths (20/40/60/120/
--    250/330/500/960/1280/1920/3840 — see mediawiki.org Common thumbnail
--    sizes), so: url/detail = 960px, card_thumbnail_url = 500px.
-- -----------------------------------------------------------------------------
INSERT INTO product_media
    (product_id, url, alt_text, media_type, is_primary, sort_order,
     card_thumbnail_url, detail_thumbnail_url)
SELECT p.id, m.url,
       p.name || ' (' || m.credit || ')',
       'IMAGE', TRUE, 0, m.card, m.detail
FROM product p
JOIN (
    VALUES
    -- sku, url(800px), card(400px), detail(800px), credit
    ('GI-RC-YRG-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/500px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'green coffee beans in jute sack — photo: DeaPeaJay, CC BY-SA 2.0, Wikimedia Commons'),
    ('GI-RC-YRG-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/500px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'green coffee beans — photo: Fernando Rebolo, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-RC-KOC-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/500px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'green coffee beans — photo: Isai Symens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-YRG-NG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/500px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'sun-dried natural coffee cherries — photo: Ralff Nestor Nacor, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-GUJ-NG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/500px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'coffee cherries sun-drying — photo: Josep M. Gracia, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-GUJ-WG1-MT',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=800&q=80',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=400&q=80',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=1200&q=80',
     'green coffee beans — photo via Unsplash'),
    ('GI-RC-GUJ-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/500px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'green coffee beans — photo: Fernando Rebolo, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-RC-GUJ-NG3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/500px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'sun-dried natural coffee cherries — photo: Ralff Nestor Nacor, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-SID-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/500px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'green coffee beans in jute sack — photo: DeaPeaJay, CC BY-SA 2.0, Wikimedia Commons'),
    ('GI-RC-SID-NG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/500px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'coffee cherries sun-drying — photo: Josep M. Gracia, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-SID-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/500px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'green coffee beans — photo: Isai Symens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-SID-NG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/500px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'coffee cherries on raised drying beds — photo: Philip Asedri Dradrio, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-HRR-NG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/500px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'sun-dried natural coffee cherries — photo: Ralff Nestor Nacor, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-HRR-NG4-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/500px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'coffee cherries on raised drying beds — photo: Philip Asedri Dradrio, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-LIM-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/500px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'green coffee beans — photo: Fernando Rebolo, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-RC-LIM-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/500px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'green coffee beans in jute sack — photo: DeaPeaJay, CC BY-SA 2.0, Wikimedia Commons'),
    ('GI-RC-JIM-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/500px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'green coffee beans — photo: Isai Symens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-JIM-NG5-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/500px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'coffee cherries sun-drying — photo: Josep M. Gracia, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-LEK-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/500px-Green_Bean_Coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c5/Green_Bean_Coffee.jpg/960px-Green_Bean_Coffee.jpg',
     'green coffee beans in jute sack — photo: DeaPeaJay, CC BY-SA 2.0, Wikimedia Commons'),
    ('GI-RC-KAF-WG2-MT',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=800&q=80',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=400&q=80',
     'https://images.unsplash.com/photo-1703646618632-5eeb27fc6734?w=1200&q=80',
     'green coffee beans — photo via Unsplash'),
    ('GI-RC-BMJ-WG3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/500px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'green coffee beans — photo: Fernando Rebolo, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-RC-BAL-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/500px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'green coffee beans — photo: Isai Symens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-ARS-NG3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/500px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/59/Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg/960px-Dried_Coffee_Cherries_in_IFEX_PH_2025.jpg',
     'sun-dried natural coffee cherries — photo: Ralff Nestor Nacor, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-GIM-WG2-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/500px-Unroasted_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/3/31/Unroasted_coffee.jpg/960px-Unroasted_coffee.jpg',
     'green coffee beans — photo: Fernando Rebolo, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-RC-GES-WG1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/500px-Coffee_beans_unroasted.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/19/Coffee_beans_unroasted.jpg/960px-Coffee_beans_unroasted.jpg',
     'green coffee beans — photo: Isai Symens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-TEP-NG3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/500px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/74/WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg/960px-WAE_-_Coffee_cherries_drying_in_Wae_Rebo%2C_Flores%2C_Indonesia%2C_2019.jpg',
     'coffee cherries sun-drying — photo: Josep M. Gracia, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-RC-BBK-NG3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/500px-Women_drying_coffee.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e0/Women_drying_coffee.jpg/960px-Women_drying_coffee.jpg',
     'coffee cherries on raised drying beds — photo: Philip Asedri Dradrio, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-PC-YRG-G1-MT',
     'https://upload.wikimedia.org/wikipedia/commons/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg/500px-Roasted_coffee_beans_%28Unsplash%29.jpg',
     'https://upload.wikimedia.org/wikipedia/commons/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg',
     'medium-roast coffee beans — photo: Daniel Ruswick, CC0, Wikimedia Commons'),
    ('GI-PC-GUJ-G1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cb/Macro_close_up_look_of_roasted_coffee_beans.jpg/960px-Macro_close_up_look_of_roasted_coffee_beans.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cb/Macro_close_up_look_of_roasted_coffee_beans.jpg/500px-Macro_close_up_look_of_roasted_coffee_beans.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cb/Macro_close_up_look_of_roasted_coffee_beans.jpg/960px-Macro_close_up_look_of_roasted_coffee_beans.jpg',
     'roasted coffee beans macro — photo: Jameswasswa, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-PC-SID-G1-MT',
     'https://images.unsplash.com/photo-1773934926697-dfd9c67d93da?w=800&q=80',
     'https://images.unsplash.com/photo-1773934926697-dfd9c67d93da?w=400&q=80',
     'https://images.unsplash.com/photo-1773934926697-dfd9c67d93da?w=1200&q=80',
     'roasted coffee beans — photo via Unsplash'),
    ('GI-PC-HRR-G1-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/960px-Roasted_Coffee_Beans_Texture.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/500px-Roasted_Coffee_Beans_Texture.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/960px-Roasted_Coffee_Beans_Texture.jpg',
     'dark roasted coffee beans — photo: freestock.ca, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-PC-LIM-G2-MT',
     'https://upload.wikimedia.org/wikipedia/commons/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg/500px-Roasted_coffee_beans_%28Unsplash%29.jpg',
     'https://upload.wikimedia.org/wikipedia/commons/c/c4/Roasted_coffee_beans_%28Unsplash%29.jpg',
     'medium-roast coffee beans — photo: Daniel Ruswick, CC0, Wikimedia Commons'),
    ('GI-PC-JEB-G3-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7f/Beans_Dropping_from_a_Coffee_Roaster.JPG/960px-Beans_Dropping_from_a_Coffee_Roaster.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7f/Beans_Dropping_from_a_Coffee_Roaster.JPG/500px-Beans_Dropping_from_a_Coffee_Roaster.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7f/Beans_Dropping_from_a_Coffee_Roaster.JPG/960px-Beans_Dropping_from_a_Coffee_Roaster.JPG',
     'beans dropping from coffee roaster — photo: Jkafader, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-PC-JIM-G4-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/960px-Roasted_Coffee_Beans_Texture.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/500px-Roasted_Coffee_Beans_Texture.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/53/Roasted_Coffee_Beans_Texture.jpg/960px-Roasted_Coffee_Beans_Texture.jpg',
     'dark roasted coffee beans — photo: freestock.ca, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-WP-TEFF-GRN-MT',
     'https://images.unsplash.com/photo-1719785045940-938a0d735a78?w=800&q=80',
     'https://images.unsplash.com/photo-1719785045940-938a0d735a78?w=400&q=80',
     'https://images.unsplash.com/photo-1719785045940-938a0d735a78?w=1200&q=80',
     'teff grain — photo via Unsplash'),
    ('GI-WP-TEFF-FLR-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/78/Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg/960px-Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/78/Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg/500px-Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/78/Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg/960px-Corn_flour_being_sifted_in_a_wooden_bowl_used_for_baking_on_a_kitchen_counter.jpg',
     'fine-milled flour — photo: Shixart1985, CC BY 2.0, Wikimedia Commons'),
    ('GI-WP-NAVY-BEAN-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/93/Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg/960px-Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/93/Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg/500px-Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/93/Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg/960px-Phaseolus_vulgaris_white_beans%2C_witte_boon.jpg',
     'white beans — photo: Rasbak, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-WP-CHICKPEA-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/48/Dried_chickpeas_for_sale_-_market_in_Israel.jpg/960px-Dried_chickpeas_for_sale_-_market_in_Israel.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/48/Dried_chickpeas_for_sale_-_market_in_Israel.jpg/500px-Dried_chickpeas_for_sale_-_market_in_Israel.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/48/Dried_chickpeas_for_sale_-_market_in_Israel.jpg/960px-Dried_chickpeas_for_sale_-_market_in_Israel.jpg',
     'dried chickpeas — photo: Danielrosehill, CC BY 4.0, Wikimedia Commons'),
    ('GI-WP-FAVA-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/83/Fava_Beans_Dried.JPG/960px-Fava_Beans_Dried.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/83/Fava_Beans_Dried.JPG/500px-Fava_Beans_Dried.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/83/Fava_Beans_Dried.JPG/960px-Fava_Beans_Dried.JPG',
     'dried fava beans — photo: Miansari66, CC0, Wikimedia Commons'),
    ('GI-WP-LENTIL-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Split_Red_Lentil.jpg/960px-Split_Red_Lentil.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Split_Red_Lentil.jpg/500px-Split_Red_Lentil.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Split_Red_Lentil.jpg/960px-Split_Red_Lentil.jpg',
     'split red lentils — photo: Sanjay Acharya, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-WP-MUNG-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Mung_beans_%28Vigna_radiata%29.jpg/960px-Mung_beans_%28Vigna_radiata%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Mung_beans_%28Vigna_radiata%29.jpg/500px-Mung_beans_%28Vigna_radiata%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Mung_beans_%28Vigna_radiata%29.jpg/960px-Mung_beans_%28Vigna_radiata%29.jpg',
     'mung beans — photo: Ivar Leidus, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-WP-SORGHUM-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2a/Sorghum_seed.jpg/960px-Sorghum_seed.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2a/Sorghum_seed.jpg/500px-Sorghum_seed.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2a/Sorghum_seed.jpg/960px-Sorghum_seed.jpg',
     'white sorghum seeds — photo: Evon2023, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-WP-MAIZE-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/69/White_Maize.jpg/960px-White_Maize.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/69/White_Maize.jpg/500px-White_Maize.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/6/69/White_Maize.jpg/960px-White_Maize.jpg',
     'white maize kernels — photo: Gaurav Dhwaj Khadka, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-WP-HON-WHT-MT',
     'https://upload.wikimedia.org/wikipedia/commons/7/7f/Honey_%28Italian-miele%29_in_a_jar.jpg',
     'https://upload.wikimedia.org/wikipedia/commons/7/7f/Honey_%28Italian-miele%29_in_a_jar.jpg',
     'https://upload.wikimedia.org/wikipedia/commons/7/7f/Honey_%28Italian-miele%29_in_a_jar.jpg',
     'creamed white honey in jar — photo: Davide Vizzini, CC BY 2.0, Wikimedia Commons'),
    ('GI-WP-HON-MLT-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/24/Honey_for_sale_in_Ethiopia.jpg/960px-Honey_for_sale_in_Ethiopia.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/24/Honey_for_sale_in_Ethiopia.jpg/500px-Honey_for_sale_in_Ethiopia.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/24/Honey_for_sale_in_Ethiopia.jpg/960px-Honey_for_sale_in_Ethiopia.jpg',
     'honey for sale in Ethiopia — photo: ShanaliCT, CC BY 4.0, Wikimedia Commons'),
    ('GI-SP-KOR-PD-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/KororimaWhole01.jpg/960px-KororimaWhole01.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/KororimaWhole01.jpg/500px-KororimaWhole01.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/2/2e/KororimaWhole01.jpg/960px-KororimaWhole01.jpg',
     'korerima pods, Shashamane Ethiopia — photo: Joe Castleman (Gyrofrog), CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-SP-BCUM-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/de/Nigella_sativa_seeds.jpg/960px-Nigella_sativa_seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/de/Nigella_sativa_seeds.jpg/500px-Nigella_sativa_seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/de/Nigella_sativa_seeds.jpg/960px-Nigella_sativa_seeds.jpg',
     'black cumin (nigella) seeds — photo: Gaurav Dhwaj Khadka, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-AJW-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0e/Ajwain.JPG/960px-Ajwain.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0e/Ajwain.JPG/500px-Ajwain.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0e/Ajwain.JPG/960px-Ajwain.JPG',
     'ajwain seeds — photo: Miansari66, public domain, Wikimedia Commons'),
    ('GI-SP-FEN-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/16/Fenugreek_Seeds_01.jpg/960px-Fenugreek_Seeds_01.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/16/Fenugreek_Seeds_01.jpg/500px-Fenugreek_Seeds_01.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/1/16/Fenugreek_Seeds_01.jpg/960px-Fenugreek_Seeds_01.jpg',
     'fenugreek seeds — photo: CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-COR-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Coriander_Seeds.jpg/960px-Coriander_Seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Coriander_Seeds.jpg/500px-Coriander_Seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/8/86/Coriander_Seeds.jpg/960px-Coriander_Seeds.jpg',
     'coriander seeds — photo: Sanjay Acharya, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-LPEP-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/09/Langer_Pfeffer_AW_20151224.jpg/960px-Langer_Pfeffer_AW_20151224.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/09/Langer_Pfeffer_AW_20151224.jpg/500px-Langer_Pfeffer_AW_20151224.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/09/Langer_Pfeffer_AW_20151224.jpg/960px-Langer_Pfeffer_AW_20151224.jpg',
     'dried long pepper spikes — photo: Alice Wiegand, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-BPEP-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ec/Market_Aix-en-Provence_20100828_Black_pepper.jpg/960px-Market_Aix-en-Provence_20100828_Black_pepper.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ec/Market_Aix-en-Provence_20100828_Black_pepper.jpg/500px-Market_Aix-en-Provence_20100828_Black_pepper.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ec/Market_Aix-en-Provence_20100828_Black_pepper.jpg/960px-Market_Aix-en-Provence_20100828_Black_pepper.jpg',
     'black peppercorns — photo: Georges Seguin (Okki), CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-SP-GING-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cf/Ginger_root.jpg/960px-Ginger_root.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cf/Ginger_root.jpg/500px-Ginger_root.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cf/Ginger_root.jpg/960px-Ginger_root.jpg',
     'dried ginger rhizome — photo: BogTar201213, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-TURF-MT',
     'https://upload.wikimedia.org/wikipedia/commons/0/09/Dried_Waigaon_Turmeric_fingers.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/0/09/Dried_Waigaon_Turmeric_fingers.jpg/500px-Dried_Waigaon_Turmeric_fingers.jpg',
     'https://upload.wikimedia.org/wikipedia/commons/0/09/Dried_Waigaon_Turmeric_fingers.jpg',
     'dried turmeric fingers — photo: Shobha Tai Gaydhane, CC0, Wikimedia Commons'),
    ('GI-SP-TURP-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/90/Kunyit_Bubuk.jpg/960px-Kunyit_Bubuk.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/90/Kunyit_Bubuk.jpg/500px-Kunyit_Bubuk.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/9/90/Kunyit_Bubuk.jpg/960px-Kunyit_Bubuk.jpg',
     'turmeric and turmeric powder — photo: Herusutimbul, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-BRB-BLD-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/dc/Berberespice.jpg/960px-Berberespice.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/dc/Berberespice.jpg/500px-Berberespice.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/dc/Berberespice.jpg/960px-Berberespice.jpg',
     'berbere spice powder — photo: Badagnani, CC BY 3.0, Wikimedia Commons'),
    ('GI-SP-MIT-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg/960px-Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg/500px-Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg/960px-Red_Chili_Powder_%28Lall_Mirch%29_%2849695826571%29.jpg',
     'hot red chili powder — photo: Ajay Suresh, CC BY 2.0, Wikimedia Commons'),
    ('GI-SP-SHR-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5e/Gram_flour_AvL.jpg/960px-Gram_flour_AvL.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5e/Gram_flour_AvL.jpg/500px-Gram_flour_AvL.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5e/Gram_flour_AvL.jpg/960px-Gram_flour_AvL.jpg',
     'legume flour — photo: AlexanderVanLoon, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-SP-CHILI-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b3/BerbereRedPeppers.JPG/960px-BerbereRedPeppers.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b3/BerbereRedPeppers.JPG/500px-BerbereRedPeppers.JPG',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b3/BerbereRedPeppers.JPG/960px-BerbereRedPeppers.JPG',
     'dried berbere chili peppers — photo: Gyrofrog, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-SP-CFLK-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b2/Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg/960px-Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b2/Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg/500px-Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b2/Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg/960px-Inle_Lake%2C_Dried_red_chili_%28chilli%29_pepper%2C_Capsicum_annuum%2C_Myanmar.jpg',
     'crushed dried chili — photo: Vyacheslav Argenberg, CC BY 4.0, Wikimedia Commons'),
    ('GI-SP-RUE-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fe/Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg/960px-Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fe/Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg/500px-Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fe/Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg/960px-Ruta_chalepensis_Dried_fruits_Tigray_Region_Ethiopia.jpg',
     'dried rue fruits, Tigray Ethiopia — photo: Didier Descouens, CC BY-SA 4.0, Wikimedia Commons'),
    ('GI-SP-MUST-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Black-mustard-seeds.jpg/960px-Black-mustard-seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Black-mustard-seeds.jpg/500px-Black-mustard-seeds.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Black-mustard-seeds.jpg/960px-Black-mustard-seeds.jpg',
     'mustard seeds — photo: Sanjay Acharya, CC BY-SA 3.0, Wikimedia Commons'),
    ('GI-OS-SES-HUM-MT',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=800&q=80',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=400&q=80',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=1200&q=80',
     'whitish sesame seeds — photo via Unsplash'),
    ('GI-OS-SES-WOL-MT',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=800&q=80',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=400&q=80',
     'https://images.unsplash.com/photo-1549530708-be1b34900690?w=1200&q=80',
     'sesame seeds — photo via Unsplash'),
    ('GI-OS-NIG-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/Niger_Seed.jpg/960px-Niger_Seed.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/Niger_Seed.jpg/500px-Niger_Seed.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a8/Niger_Seed.jpg/960px-Niger_Seed.jpg',
     'niger seeds (noug) — photo: Gaurav Dhwaj Khadka, CC BY 4.0, Wikimedia Commons'),
    ('GI-OS-FLX-MT',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Linum_usitatissimum_%28linseed%29_in_jar.jpg/960px-Linum_usitatissimum_%28linseed%29_in_jar.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Linum_usitatissimum_%28linseed%29_in_jar.jpg/500px-Linum_usitatissimum_%28linseed%29_in_jar.jpg',
     'https://thumb.wikimedia.org/wikipedia/commons/thumb/7/71/Linum_usitatissimum_%28linseed%29_in_jar.jpg/960px-Linum_usitatissimum_%28linseed%29_in_jar.jpg',
     'linseed (flaxseed) — photo: Guglani, CC0, Wikimedia Commons')
) AS m(sku, url, card, detail, credit)
    ON m.sku = p.sku
WHERE NOT EXISTS (
    SELECT 1 FROM product_media pm
    WHERE pm.product_id = p.id AND pm.is_primary = TRUE
);

-- -----------------------------------------------------------------------------
-- 4. Wholesale tiers for the new products — commodity-realistic discounts.
--    Entry min_quantity is in tons: 19 MT ~ one 20ft container of bagged
--    coffee; 100 MT ~ a 5-container program.
--      T1 (roaster/importer): -3% @ 19 MT, -5% @ 100 MT
--      T2 (distributor):      -5% @ 19 MT, -8% @ 100 MT
--      VIP (key account):     -7% @ 19 MT, -11% @ 100 MT
-- -----------------------------------------------------------------------------
INSERT INTO price_list_entry (price_list_id, product_id, price, min_quantity)
SELECT pl.id, p.id,
       ROUND(p.base_price * m.factor, 4),
       m.min_qty
FROM product p
JOIN price_list pl ON TRUE
JOIN customer_group g ON g.id = pl.customer_group_id
JOIN (
    VALUES
        ('WHOLESALE_T1', 0.97, 19.000),  ('WHOLESALE_T1', 0.95, 100.000),
        ('WHOLESALE_T2', 0.95, 19.000),  ('WHOLESALE_T2', 0.92, 100.000),
        ('VIP',          0.93, 19.000),  ('VIP',          0.89, 100.000)
) AS m(grp, factor, min_qty) ON m.grp = g.code
WHERE p.sku LIKE 'GI-%' AND p.slug LIKE '%-1mt'
  AND NOT EXISTS (
      SELECT 1 FROM price_list_entry ple
      WHERE ple.price_list_id = pl.id
        AND ple.product_id = p.id
        AND ple.min_quantity = m.min_qty
  );

-- -----------------------------------------------------------------------------
-- 5. Inventory for the new products (quantities in metric tons).
-- -----------------------------------------------------------------------------
INSERT INTO inventory (product_id, quantity, reserved_quantity, restock_threshold)
SELECT p.id,
       (CASE p.category
           WHEN 'RAW_COFFEE'     THEN 150.000
           WHEN 'ROASTED_COFFEE' THEN  40.000
           WHEN 'WHITE_PRODUCTS' THEN 250.000
           WHEN 'SPICY_PRODUCTS' THEN  45.000
           WHEN 'OIL_SEED'       THEN 180.000
       END),
       0.000,
       10.000
FROM product p
WHERE p.slug LIKE '%-1mt'
  AND NOT EXISTS (SELECT 1 FROM inventory inv WHERE inv.product_id = p.id);

-- -----------------------------------------------------------------------------
-- 6. Sea-freight shipping method — the air rates are parcel pricing and are
--    nonsensical for tonnage ($6.50/kg x 1,000 kg = $6,535 per ton). A 20-ft
--    FCL of bagged coffee Addis->Djibouti->port runs roughly $450 + $0.12/kg,
--    which prices one FCL (~19 t) near a realistic $2,700 all-in.
-- -----------------------------------------------------------------------------
INSERT INTO shipping_method
    (code, name, description, currency, base_cost, per_kg_rate,
     min_days, max_days, active, sort_order)
VALUES
    ('SEA_FREIGHT_FCL',
     'Sea freight — FCL via Djibouti',
     'Ocean freight in 20-ft/40-ft containers via the Port of Djibouti. Priced per shipped kilogram; typically 25-45 days port-to-port. Recommended for per-ton products; air methods remain available for samples.',
     'USD', 450.0000, 0.1200, 25, 45, TRUE, 40)
ON CONFLICT (code, currency) DO NOTHING;
