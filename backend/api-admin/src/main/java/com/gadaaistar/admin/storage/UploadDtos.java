package com.gadaaistar.admin.storage;

/**
 * DTOs exchanged with the admin frontend's upload widget.
 * Kept as a single file because the API is intentionally tiny.
 */
public final class UploadDtos {

    private UploadDtos() {}

    /**
     * Server-issued response after a successful upload. The frontend feeds
     * {@code url} straight into the existing {@code POST /products/{id}/media}
     * endpoint to attach the file as a product image.
     */
    public record UploadResponse(String url,
                                 String objectKey,
                                 String contentType,
                                 long sizeBytes,
                                 String cardThumbnailUrl,
                                 String detailThumbnailUrl) {}
}
