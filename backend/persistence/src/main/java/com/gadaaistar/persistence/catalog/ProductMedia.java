package com.gadaaistar.persistence.catalog;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_media")
public class ProductMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    public Product product;

    @Column(nullable = false, length = 500)
    public String url;

    @Column(name = "alt_text", length = 255)
    public String altText;

    /** IMAGE / VIDEO. VARCHAR to avoid a JPA enum lookup for a two-value set. */
    @Column(name = "media_type", nullable = false, length = 16)
    public String mediaType = "IMAGE";

    @Column(name = "is_primary", nullable = false)
    public Boolean isPrimary = Boolean.FALSE;

    @Column(name = "sort_order", nullable = false)
    public Integer sortOrder = 0;

    /** URL of the 300px card thumbnail (generated on upload). Nullable for legacy/v video. */
    @Column(name = "card_thumbnail_url", length = 500)
    public String cardThumbnailUrl;

    /** URL of the 800px detail thumbnail (generated on upload). Nullable for legacy/video. */
    @Column(name = "detail_thumbnail_url", length = 500)
    public String detailThumbnailUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
