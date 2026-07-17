package com.gadaaistar.common;

/**
 * Lifecycle status of a product.
 * Stored as VARCHAR in {@code product.status}.
 */
public enum ProductStatus {
    /** In progress — admins can see & edit, storefront hides it. */
    DRAFT,
    /** Live on the storefront. */
    ACTIVE,
    /** Retired — historical orders keep working, but no new sales. */
    ARCHIVED;

    public boolean isVisibleOnStorefront() {
        return this == ACTIVE;
    }
}
