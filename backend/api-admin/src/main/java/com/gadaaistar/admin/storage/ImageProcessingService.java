package com.gadaaistar.admin.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;

import net.coobird.thumbnailator.Thumbnails;

import org.jboss.logging.Logger;

/**
 * Generates resized image variants for storefront display.
 *
 * <p>Two sizes are produced on upload:
 * <ul>
 *   <li><b>card</b> — 300×300 (used in product cards, wishlist, cart thumbnails)</li>
 *   <li><b>detail</b> — 800×800 (used in product detail page gallery)</li>
 * </ul>
 *
 * <p>The original file is preserved as-is for zoom/full-view and archival.
 * Thumbnails are JPEG (even if the source is PNG/WebP) to minimise bandwidth
 * for the common case — product photos. Output quality is 85%.
 *
 * <p>Non-image uploads (video) are skipped — {@link #generateThumbnails}
 * returns an empty {@link ThumbnailSet} and the caller proceeds with just
 * the original URL.
 */
@ApplicationScoped
public class ImageProcessingService {

    private static final Logger LOG = Logger.getLogger(ImageProcessingService.class);

    static final int CARD_SIZE  = 300;
    static final int DETAIL_SIZE = 800;
    static final float OUTPUT_QUALITY = 0.85f;

    /**
     * Result of thumbnail generation. Each variant is a byte array ready
     * for streaming to S3, along with the content type (always image/jpeg
     * for thumbnails).
     */
    public record ThumbnailVariant(String suffix, byte[] bytes, String contentType) {}

    public record ThumbnailSet(ThumbnailVariant card, ThumbnailVariant detail) {}

    /**
     * Generate card + detail thumbnails from the given image bytes.
     *
     * @param originalBytes  the raw uploaded file content
     * @param contentType    MIME type of the original (e.g. "image/png")
     * @return a {@link ThumbnailSet} with both variants, or an empty set
     *         if the content type is not an image or processing fails
     */
    public ThumbnailSet generateThumbnails(byte[] originalBytes, String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return new ThumbnailSet(null, null);
        }
        // Skip SVG — vector format, rasterising would lose scalability.
        if (contentType.equalsIgnoreCase("image/svg+xml")) {
            return new ThumbnailSet(null, null);
        }

        try {
            var card = generateVariant(originalBytes, CARD_SIZE);
            var detail = generateVariant(originalBytes, DETAIL_SIZE);
            return new ThumbnailSet(card, detail);
        } catch (IOException e) {
            LOG.warnf("Could not generate thumbnails for %s: %s", contentType, e.getMessage());
            return new ThumbnailSet(null, null);
        }
    }

    private ThumbnailVariant generateVariant(byte[] originalBytes, int size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(originalBytes))
                .size(size, size)
                .outputQuality(OUTPUT_QUALITY)
                .outputFormat("jpg")
                .toOutputStream(baos);
        return new ThumbnailVariant(
                size == CARD_SIZE ? "_card" : "_detail",
                baos.toByteArray(),
                "image/jpeg"
        );
    }

    /**
     * Derive the thumbnail object key from the original key.
     * e.g. {@code products/2025/07/abc.jpg} → {@code products/2025/07/abc_card.jpg}
     */
    static String thumbnailKey(String originalKey, String suffix) {
        int dot = originalKey.lastIndexOf('.');
        if (dot < 0) return originalKey + suffix;
        return originalKey.substring(0, dot) + suffix + originalKey.substring(dot);
    }
}
