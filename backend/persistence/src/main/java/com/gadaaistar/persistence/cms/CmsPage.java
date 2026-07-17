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
 * CMS page entity for static content (About, FAQ, Terms, Privacy, etc.).
 *
 * <p>Pages are managed by admins via the CMS section of the back-office.
 * The storefront fetches published pages by slug to render content.
 */
@Entity
@Table(name = "cms_page")
public class CmsPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 120, unique = true)
    public String slug;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(name = "body_html", columnDefinition = "LONGTEXT")
    public String bodyHtml;

    @Column(name = "seo_title", length = 200)
    public String seoTitle;

    @Column(name = "seo_description", length = 500)
    public String seoDescription;

    /** DRAFT or PUBLISHED. */
    @Column(nullable = false, length = 20)
    public String status = "DRAFT";

    @Column(name = "sort_order", nullable = false)
    public Integer sortOrder = 0;

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
