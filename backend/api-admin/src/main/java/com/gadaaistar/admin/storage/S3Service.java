package com.gadaaistar.admin.storage;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.gadaaistar.common.exceptions.ValidationException;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Thin wrapper around the MinIO Java SDK used for product-media uploads.
 *
 * <p>Bucket bootstrap (create + public-read policy) runs lazily on the first
 * upload rather than on startup so the admin app can still start when MinIO
 * is temporarily unavailable (dev workflow: {@code docker compose down &&
 * quarkus dev} to work on the API without the storage layer).
 *
 * <p>All object keys live under {@code products/YYYY/MM/<uuid>.<ext>} so the
 * bucket stays browsable in the MinIO console even at scale.
 */
@ApplicationScoped
public class S3Service {

    private static final Logger LOG = Logger.getLogger(S3Service.class);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM",   Locale.ROOT);

    @ConfigProperty(name = "gadaa.s3.endpoint")         String endpoint;
    @ConfigProperty(name = "gadaa.s3.public-base-url")  String publicBaseUrl;
    @ConfigProperty(name = "gadaa.s3.region")           String region;
    @ConfigProperty(name = "gadaa.s3.access-key")       String accessKey;
    @ConfigProperty(name = "gadaa.s3.secret-key")       String secretKey;
    @ConfigProperty(name = "gadaa.s3.bucket-media")     String bucketMedia;

    private MinioClient client;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        // Fail early on obvious misconfiguration (empty secret, unresolved env).
        if (endpoint == null || endpoint.isBlank()) {
            LOG.warn("gadaa.s3.endpoint is blank — uploads will fail until it's set.");
        }
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Upload the given stream to the media bucket under a fresh object key.
     *
     * @return the public URL suitable for a browser {@code <img src="...">}.
     */
    public UploadResult uploadProductMedia(InputStream body,
                                            long sizeBytes,
                                            String contentType,
                                            String originalFilename) {
        ensureBucketReady();
        String key = buildObjectKey(originalFilename, contentType);

        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucketMedia)
                    .object(key)
                    .stream(body, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            LOG.errorf(e, "MinIO putObject failed (bucket=%s key=%s)", bucketMedia, key);
            throw new ValidationException("Storage backend rejected the upload: " + e.getMessage());
        }

        String url = buildPublicUrl(key);
        LOG.infof("Uploaded product media → %s (%d bytes, %s)", url, sizeBytes, contentType);
        return new UploadResult(url, key, contentType, sizeBytes, null, null);
    }

    // ---------------------------------------------------------------------------
    // Bucket bootstrap
    // ---------------------------------------------------------------------------

    private void ensureBucketReady() {
        if (bucketReady.get()) return;
        synchronized (this) {
            if (bucketReady.get()) return;
            try {
                boolean exists = client.bucketExists(
                        BucketExistsArgs.builder().bucket(bucketMedia).build());
                if (!exists) {
                    LOG.infof("Creating MinIO bucket '%s' (region=%s)", bucketMedia, region);
                    client.makeBucket(MakeBucketArgs.builder()
                            .bucket(bucketMedia)
                            .region(region)
                            .build());
                }
                // Idempotent: setting the policy again on every restart is fine.
                client.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucketMedia)
                        .config(publicReadPolicy(bucketMedia))
                        .build());
                bucketReady.set(true);
                LOG.infof("MinIO bucket '%s' is ready (public-read).", bucketMedia);
            } catch (MinioException | java.io.IOException
                     | java.security.NoSuchAlgorithmException
                     | java.security.InvalidKeyException e) {
                LOG.errorf(e, "Could not initialise MinIO bucket '%s'", bucketMedia);
                throw new ValidationException("Storage bucket is not available. Check S3 settings.");
            }
        }
    }

    /**
     * Bucket policy JSON that allows anonymous {@code GetObject} on every key
     * in the bucket. This is the standard e-commerce pattern for product
     * images — they're served directly to browsers without signed URLs.
     */
    private static String publicReadPolicy(String bucket) {
        return "{\n"
             + "  \"Version\": \"2012-10-17\",\n"
             + "  \"Statement\": [\n"
             + "    {\n"
             + "      \"Effect\": \"Allow\",\n"
             + "      \"Principal\": { \"AWS\": [\"*\"] },\n"
             + "      \"Action\": [\"s3:GetObject\"],\n"
             + "      \"Resource\": [\"arn:aws:s3:::" + bucket + "/*\"]\n"
             + "    }\n"
             + "  ]\n"
             + "}";
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static String buildObjectKey(String originalFilename, String contentType) {
        var today = LocalDate.now();
        String ext = inferExtension(originalFilename, contentType);
        return "products/" + today.format(YEAR) + "/" + today.format(MONTH)
                + "/" + UUID.randomUUID() + ext;
    }

    /**
     * Prefer the extension from the original filename (preserves camelCase
     * ".JPG" if the user really wants that), else derive from the MIME type.
     * The dot is included on the return value so callers can just concatenate.
     */
    static String inferExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
                // Whitelist to keep object keys safe (no null bytes, no slashes).
                if (ext.matches("^\\.[a-z0-9]{2,5}$")) return ext;
            }
        }
        if (contentType == null) return "";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg"       -> ".jpg";
            case "image/png"        -> ".png";
            case "image/webp"       -> ".webp";
            case "image/gif"        -> ".gif";
            case "image/svg+xml"    -> ".svg";
            case "video/mp4"        -> ".mp4";
            case "video/quicktime"  -> ".mov";
            case "video/webm"       -> ".webm";
            default                 -> "";
        };
    }

    private String buildPublicUrl(String key) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + bucketMedia + "/" + key;
    }

    // ---------------------------------------------------------------------------

    public record UploadResult(String url, String objectKey, String contentType, long sizeBytes,
                                String cardThumbnailUrl, String detailThumbnailUrl) {}

    /**
     * Upload a thumbnail variant to the media bucket.
     *
     * @param objectKey  the original object key (the suffix will be inserted)
     * @param suffix     e.g. "_card" or "_detail"
     * @param bytes      the thumbnail image bytes
     * @param contentType  always "image/jpeg" for thumbnails
     * @return the public URL of the thumbnail, or null if the upload fails
     */
    public String uploadThumbnail(String objectKey, String suffix,
                                   byte[] bytes, String contentType) {
        ensureBucketReady();
        String thumbKey = ImageProcessingService.thumbnailKey(objectKey, suffix);
        try (var in = new java.io.ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucketMedia)
                    .object(thumbKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            LOG.warnf("MinIO putObject failed for thumbnail (key=%s): %s", thumbKey, e.getMessage());
            return null;
        }
        return buildPublicUrl(thumbKey);
    }
}
