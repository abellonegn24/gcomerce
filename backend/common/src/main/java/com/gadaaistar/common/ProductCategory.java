package com.gadaaistar.common;

/**
 * Top-level product categories for the Gadaa Istar catalog.
 * Stored as VARCHAR in {@code product.category}.
 */
public enum ProductCategory {
    /** Unroasted, single-origin green coffee for roasters. */
    RAW_COFFEE("Raw Coffee", "raw-coffee"),
    /** Roasted coffee — whole bean and ground, ready to brew. */
    ROASTED_COFFEE("Roasted Coffee", "roasted-coffee"),
    /** Dairy, flours, and other white-agri exports (teff, injera flour, honey, …). */
    WHITE_PRODUCTS("White Products", "white-products"),
    /** Spice blends, chilies, and dry seasonings (berbere, mitmita, korerima, …). */
    SPICY_PRODUCTS("Spicy Products", "spicy-products"),
    /** Sesame, Niger seed, sunflower — export-grade oil seeds. */
    OIL_SEED("Oil Seed", "oil-seed"),
    /** Live animals and by-products — cattle, sheep, goats, poultry. */
    LIVESTOCK("Livestock", "livestock"),
    /** Gold, gemstones, and industrial minerals. */
    MINING("Mining", "mining");

    public final String displayName;
    public final String slug;

    ProductCategory(String displayName, String slug) {
        this.displayName = displayName;
        this.slug = slug;
    }

    public static ProductCategory fromSlug(String slug) {
        if (slug == null) return null;
        for (var c : values()) if (c.slug.equalsIgnoreCase(slug)) return c;
        return null;
    }
}
