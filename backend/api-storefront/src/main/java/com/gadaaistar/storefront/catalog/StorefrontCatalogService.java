package com.gadaaistar.storefront.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.PagedResponse;
import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.PriceList;
import com.gadaaistar.persistence.catalog.PriceListEntry;
import com.gadaaistar.persistence.catalog.PriceListEntryRepository;
import com.gadaaistar.persistence.catalog.PriceListRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductRepository;
import com.gadaaistar.persistence.customer.Customer;
import com.gadaaistar.persistence.customer.CustomerRepository;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Storefront-side read model.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>Read only ACTIVE products with their primary image and stock state.</li>
 *   <li>Resolve <em>display price</em> for the caller:
 *       <ul>
 *         <li>Anonymous / RETAIL customer → {@code product.basePrice} in {@code baseCurrency}.</li>
 *         <li>B2B customer with an assigned group → the entry in the group's
 *             active price list whose {@code minQuantity} is largest but still
 *             &lt;= 1 (we surface the entry-level "starting from" price on
 *             cards and the full tier ladder on the detail page).</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@ApplicationScoped
public class StorefrontCatalogService {

    @Inject ProductRepository          products;
    @Inject PriceListRepository        priceLists;
    @Inject PriceListEntryRepository   priceEntries;
    @Inject InventoryRepository        inventories;
    @Inject CustomerRepository         customers;
    @Inject SecurityContextHolder      security;

    // -------------------------------------------------------------------------
    // Public reads
    // -------------------------------------------------------------------------

    public StorefrontCatalogDtos.ListResponse list(ProductCategory category, int page, int size) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(48, size));

        var rows  = products.listActive(category, page, size);
        var total = products.countActive(category);
        var priceList = resolvePriceListForCaller();

        var items = rows.stream()
                .map(p -> toCard(p, priceList))
                .toList();

        return new StorefrontCatalogDtos.ListResponse(
                PagedResponse.of(items, page, size, total)
        );
    }

    public StorefrontCatalogDtos.ListResponse search(String q, ProductCategory category, int page, int size) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(48, size));

        var rows  = products.searchActive(q, category, page, size);
        var total = products.searchActiveCount(q, category);
        var priceList = resolvePriceListForCaller();

        var items = rows.stream()
                .map(p -> toCard(p, priceList))
                .toList();

        return new StorefrontCatalogDtos.ListResponse(
                PagedResponse.of(items, page, size, total)
        );
    }

    public StorefrontCatalogDtos.ProductDetail getBySlug(String slug) {
        var p = products.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Product", slug));
        if (!p.isPublic()) {
            // Same 404 as unknown — don't leak DRAFT/ARCHIVED existence.
            throw new NotFoundException("Product", slug);
        }
        return toDetail(p, resolvePriceListForCaller());
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private StorefrontCatalogDtos.ProductCard toCard(Product p, PriceList priceList) {
        var inv         = inventories.findByProduct(p.id).orElse(null);
        var displayPx   = priceForQuantity(p, priceList, BigDecimal.ONE);
        var discountPct = discountVs(p.basePrice, displayPx);

        return new StorefrontCatalogDtos.ProductCard(
                p.id, p.sku, p.slug, p.name, p.shortDescription,
                p.category,
                p.baseCurrency,
                p.basePrice,
                displayPx,
                discountPct,
                p.minOrderQuantity,
                inv != null && inv.available().signum() > 0,
                p.primaryImageUrl(),
                p.primaryCardThumbnailUrl()
        );
    }

    private StorefrontCatalogDtos.ProductDetail toDetail(Product p, PriceList priceList) {
        var inv         = inventories.findByProduct(p.id).orElse(null);
        var displayPx   = priceForQuantity(p, priceList, BigDecimal.ONE);
        var discountPct = discountVs(p.basePrice, displayPx);

        List<StorefrontCatalogDtos.TieredPrice> tiers = new ArrayList<>();
        if (priceList != null) {
            for (var e : priceEntries.findByPriceList(priceList.id)) {
                if (e.product.id.equals(p.id)) {
                    tiers.add(new StorefrontCatalogDtos.TieredPrice(e.minQuantity, e.price));
                }
            }
        }

        var urls = p.media.stream().map(m -> m.url).toList();

        var variantSummaries = p.variants.stream()
                .filter(v -> Boolean.TRUE.equals(v.active))
                .map(v -> {
                    var vInv = inventories.findByVariant(v.id).orElse(null);
                    var vAvail = vInv == null ? BigDecimal.ZERO : vInv.available();
                    return new StorefrontCatalogDtos.VariantSummary(
                            v.id, v.sku, v.name,
                            v.priceOverride, v.weightKgOverride,
                            true,
                            v.sortOrder == null ? 0 : v.sortOrder,
                            v.attributes.stream()
                                    .map(a -> new StorefrontCatalogDtos.VariantAttribute(a.attrKey, a.attrValue))
                                    .toList(),
                            vAvail.signum() > 0,
                            vAvail
                    );
                })
                .toList();

        return new StorefrontCatalogDtos.ProductDetail(
                p.id, p.sku, p.slug, p.name,
                p.shortDescription, p.description,
                p.category,
                p.baseCurrency, p.basePrice, displayPx, discountPct,
                tiers,
                p.weightKg, p.originCountry, p.hsCode,
                p.minOrderQuantity, p.packagingNotes,
                urls, p.primaryImageUrl(),
                p.primaryDetailThumbnailUrl(),
                inv != null && inv.available().signum() > 0,
                inv == null ? BigDecimal.ZERO : inv.available(),
                variantSummaries
        );
    }

    // -------------------------------------------------------------------------
    // Pricing logic
    // -------------------------------------------------------------------------

    private BigDecimal priceForQuantity(Product p, PriceList priceList, BigDecimal quantity) {
        if (priceList == null) return p.basePrice;
        return priceEntries.resolvePrice(priceList.id, p.id, quantity)
                .map(e -> e.price)
                .orElse(p.basePrice);
    }

    private static BigDecimal discountVs(BigDecimal base, BigDecimal display) {
        if (base == null || display == null || base.signum() <= 0) return BigDecimal.ZERO;
        if (display.compareTo(base) >= 0) return BigDecimal.ZERO;
        return base.subtract(display)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    /**
     * Find the price list that applies to the current caller.
     *   - Anonymous → null (base retail price wins).
     *   - Customer with a customer_group linked to an active price list in the
     *     product's base currency → that list.
     *   - Otherwise → null.
     */
    private PriceList resolvePriceListForCaller() {
        var maybeCustomerId = security.currentPrincipalId();
        if (maybeCustomerId.isEmpty()) return null;

        Optional<Customer> maybeCustomer = customers.findByIdOptional(maybeCustomerId.get());
        if (maybeCustomer.isEmpty() || maybeCustomer.get().group == null) return null;

        var groupId = maybeCustomer.get().group.id;
        // For now hard-code USD as the resolution currency — matches the seed
        // data. Real multi-currency comes in Phase 3 with FX rates.
        return priceLists.findFirstActiveForCustomerGroup(groupId, "USD").orElse(null);
    }

    // -------------------------------------------------------------------------
    // Utility used by the resource layer when constructing "starting from" text.
    // -------------------------------------------------------------------------
    @SuppressWarnings("unused")
    private static PriceListEntry unusedForClasspath(PriceListEntry keep) { return keep; }
}
