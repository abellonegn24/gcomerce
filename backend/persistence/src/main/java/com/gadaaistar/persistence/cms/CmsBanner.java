package com.gadaaistar.persistence.cms;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * CMS banner entity for homepage hero sections, category headers, etc.
 *
 * <p>Banners are placement-scoped (e.g. HOMEPAGE_HERO, CATEGORY_TOP) and
 * can be scheduled via {@code activeFrom} / {@code activeTo}. The storefront
 * fetches active banners for a given placement to render.
 */
@Entity
@Table(name = "cms_banner")
public class CmsBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 40)
    public String placement;

    @Column(length = 200)
    public String title;

    @Column(name = "image_url", nullable = false, length = 500)
    public String imageUrl;

    @Column(name = "alt_text", length = 300)
    public String altText;

    @Column(name = "link_url", length = 500)
    public String linkUrl;

    @Column(name = "active_from")
    public Instant activeFrom;

    @Column(name = "active_to")
    public Instant activeTo;

    @Column(name = "sort_order", nullable = false)
    public Integer sortOrder = 0;

    @Column(nullable = false)
    public Boolean active = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Version
    @Column(nullable = false)
    public int version;

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
