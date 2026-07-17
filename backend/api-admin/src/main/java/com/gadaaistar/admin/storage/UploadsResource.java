package com.gadaaistar.admin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.gadaaistar.common.exceptions.ValidationException;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Direct file uploads for product media. All under {@code /api/admin/v1/uploads}.
 *
 * <p>Accepts a single {@code multipart/form-data} file (part name {@code file})
 * and streams it to the configured S3-compatible bucket via {@link S3Service}.
 * Returns the resulting public URL, which the frontend then feeds into the
 * existing {@code POST /products/{id}/media} endpoint to actually attach the
 * upload to a product. Splitting the flow into two calls keeps the media DB
 * write independent of the storage backend (retryable, undo-able).
 *
 * <p>Access is restricted to SUPERADMIN / PRODUCT_MANAGER — same roles that
 * can mutate the catalog.
 */
@Path("/api/admin/v1/uploads")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Uploads", description = "Direct file uploads for product media (MinIO / S3).")
public class UploadsResource {

    private static final String[] WRITE_ROLES = { "SUPERADMIN", "PRODUCT_MANAGER" };

    /** Accepted image + video MIME types. Kept explicit to reject SVGs w/ script content. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "video/mp4",
            "video/quicktime",
            "video/webm"
    );

    @Inject S3Service              storage;
    @Inject ImageProcessingService  imageProcessor;
    @Inject SecurityContextHolder  security;

    @ConfigProperty(name = "gadaa.s3.max-upload-bytes")
    long maxUploadBytes;

    // ---------------------------------------------------------------------------

    @POST
    @Path("/product-media")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload a single image/video to object storage; returns the public URL.")
    public UploadDtos.UploadResponse uploadProductMedia(@RestForm("file") FileUpload file) {
        security.requireAdmin();
        security.requireAnyRole(WRITE_ROLES);

        if (file == null) {
            throw new ValidationException("Missing multipart field 'file'.");
        }

        long size = file.size();
        if (size <= 0) {
            throw new ValidationException("Uploaded file is empty.");
        }
        if (size > maxUploadBytes) {
            throw new ValidationException(
                    "File is too large (" + size + " bytes). Maximum is " + maxUploadBytes + " bytes.");
        }

        String contentType = normaliseContentType(file.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException(
                    "Unsupported content type '" + contentType + "'. Allowed: " + ALLOWED_CONTENT_TYPES + ".");
        }

        // Read the uploaded file into memory so we can both upload the
        // original and generate thumbnails from the same byte array.
        try (InputStream in = Files.newInputStream(file.uploadedFile())) {
            byte[] originalBytes = in.readAllBytes();

            // Upload the original via a fresh stream (the byte array is reused
            // for thumbnail generation below).
            try (var origIn = new java.io.ByteArrayInputStream(originalBytes)) {
                S3Service.UploadResult res = storage.uploadProductMedia(
                        origIn, size, contentType, file.fileName());

                // Generate + upload thumbnails for images (skipped for videos).
                String cardUrl = null;
                String detailUrl = null;
                var thumbSet = imageProcessor.generateThumbnails(originalBytes, contentType);
                if (thumbSet.card() != null) {
                    cardUrl = storage.uploadThumbnail(
                            res.objectKey(), thumbSet.card().suffix(),
                            thumbSet.card().bytes(), thumbSet.card().contentType());
                }
                if (thumbSet.detail() != null) {
                    detailUrl = storage.uploadThumbnail(
                            res.objectKey(), thumbSet.detail().suffix(),
                            thumbSet.detail().bytes(), thumbSet.detail().contentType());
                }

                return new UploadDtos.UploadResponse(
                        res.url(), res.objectKey(), res.contentType(), res.sizeBytes(),
                        cardUrl, detailUrl);
            }
        } catch (IOException e) {
            throw new ValidationException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------

    /** Trim optional charset ("image/jpeg; charset=binary") + lower-case. */
    static String normaliseContentType(String raw) {
        if (raw == null || raw.isBlank()) return "application/octet-stream";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        int semi = v.indexOf(';');
        return (semi >= 0 ? v.substring(0, semi) : v).trim();
    }
}
