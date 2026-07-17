-- V12__media_thumbnails.sql
-- Add thumbnail URL columns to product_media for server-side generated
-- 300px (card) and 800px (detail) image variants. Nullable because:
--   1. Existing media rows predate thumbnail generation.
--   2. Video uploads don't get image thumbnails.
--   3. SVG uploads are skipped (vector format).

ALTER TABLE product_media
    ADD COLUMN card_thumbnail_url   VARCHAR(500) NULL,
    ADD COLUMN detail_thumbnail_url VARCHAR(500) NULL;
