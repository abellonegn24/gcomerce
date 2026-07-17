package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.common.PagedResponse;
import com.gadaaistar.common.ProductCategory;
import com.gadaaistar.common.ProductStatus;
import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductMedia;
import com.gadaaistar.persistence.catalog.ProductMediaRepository;
import com.gadaaistar.persistence.catalog.ProductRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Admin-side product CRUD + media management.
 * Every write path is transactional and validates uniqueness on {@code sku} and {@code slug}.
 */
@ApplicationScoped
public class ProductsService {

    private static final Logger LOG = Logger.getLogger(ProductsService.class);

    @Inject ProductRepository       products;
    @Inject ProductMediaRepository  media;
    @Inject InventoryRepository     inventories;
    @Inject CatalogMapper           mapper;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public CatalogDtos.ProductListResponse list(String q, ProductStatus status,
                                                 ProductCategory category, int page, int size) {
        page = Math.max(0, page);
        size = Math.max(1, Math.min(200, size));

        var rows  = products.adminSearch(q, status, category, page, size);
        var total = products.adminSearchCount(q, status, category);

        var items = rows.stream()
                .map(p -> mapper.toListItem(p, inventories.findByProduct(p.id).orElse(null)))
                .toList();

        return new CatalogDtos.ProductListResponse(
                PagedResponse.of(items, page, size, total)
        );
    }

    public CatalogDtos.ProductDto get(long id) {
        var p = products.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        return mapper.toDto(p, inventories.findByProduct(p.id).orElse(null));
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public CatalogDtos.ProductDto create(CatalogDtos.CreateProductRequest req) {
        if (products.findBySku(req.sku()).isPresent()) {
            throw new ConflictException("SKU_TAKEN", "A product with this SKU already exists.",
                    java.util.Map.of("sku", req.sku()));
        }
        if (products.findBySlug(req.slug()).isPresent()) {
            throw new ConflictException("SLUG_TAKEN", "A product with this slug already exists.",
                    java.util.Map.of("slug", req.slug()));
        }

        var p = new Product();
        p.sku              = req.sku();
        p.slug             = req.slug();
        p.name             = req.name();
        p.shortDescription = req.shortDescription();
        p.description      = req.description();
        p.category         = req.category();
        p.status           = req.status();
        p.weightKg         = req.weightKg();
        p.hsCode           = req.hsCode();
        p.originCountry    = req.originCountry() == null ? "ET" : req.originCountry();
        p.taxClassId       = req.taxClassId();
        p.baseCurrency     = req.baseCurrency();
        p.basePrice        = req.basePrice();
        p.costPrice        = req.costPrice();
        p.minOrderQuantity = req.minOrderQuantity() == null ? BigDecimal.ONE : req.minOrderQuantity();
        p.packagingNotes   = req.packagingNotes();
        products.persist(p);

        // Seed a zero-quantity inventory row so the product shows up on
        // /inventory lists without a separate create call.
        var inv = new Inventory();
        inv.product = p;
        inv.quantity = BigDecimal.ZERO;
        inv.reservedQuantity = BigDecimal.ZERO;
        inventories.persist(inv);

        LOG.infof("Created product id=%d sku=%s slug=%s", p.id, p.sku, p.slug);
        return mapper.toDto(p, inv);
    }

    @Transactional
    public CatalogDtos.ProductDto update(long id, CatalogDtos.UpdateProductRequest req) {
        var p = products.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Product", id));

        if (req.slug() != null && !req.slug().equals(p.slug)) {
            products.findBySlug(req.slug()).ifPresent(other -> {
                if (!other.id.equals(p.id)) {
                    throw new ConflictException("SLUG_TAKEN",
                            "Another product already uses this slug.",
                            java.util.Map.of("slug", req.slug()));
                }
            });
            p.slug = req.slug();
        }

        if (req.name() != null)              p.name             = req.name();
        if (req.shortDescription() != null)  p.shortDescription = req.shortDescription();
        if (req.description() != null)       p.description      = req.description();
        if (req.category() != null)          p.category         = req.category();
        if (req.status() != null)            p.status           = req.status();
        if (req.weightKg() != null)          p.weightKg         = req.weightKg();
        if (req.hsCode() != null)            p.hsCode           = req.hsCode();
        if (req.originCountry() != null)     p.originCountry    = req.originCountry();
        if (req.taxClassId() != null)        p.taxClassId       = req.taxClassId();
        if (req.baseCurrency() != null)      p.baseCurrency     = req.baseCurrency();
        if (req.basePrice() != null)         p.basePrice        = req.basePrice();
        if (req.costPrice() != null)         p.costPrice        = req.costPrice();
        if (req.minOrderQuantity() != null)  p.minOrderQuantity = req.minOrderQuantity();
        if (req.packagingNotes() != null)    p.packagingNotes   = req.packagingNotes();

        LOG.infof("Updated product id=%d sku=%s", p.id, p.sku);
        return mapper.toDto(p, inventories.findByProduct(p.id).orElse(null));
    }

    @Transactional
    public void archive(long id) {
        var p = products.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        p.status = ProductStatus.ARCHIVED;
        LOG.infof("Archived product id=%d sku=%s", p.id, p.sku);
    }

    // -------------------------------------------------------------------------
    // Media
    // -------------------------------------------------------------------------

    @Transactional
    public List<CatalogDtos.ProductMediaDto> addMedia(long productId, CatalogDtos.CreateMediaRequest req) {
        var p = products.findByIdOptional(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        var m = new ProductMedia();
        m.product   = p;
        m.url       = req.url();
        m.altText   = req.altText();
        m.mediaType = req.mediaType() == null ? "IMAGE" : req.mediaType();
        m.isPrimary = Boolean.TRUE.equals(req.isPrimary());
        m.sortOrder = req.sortOrder() == null ? p.media.size() : req.sortOrder();
        m.cardThumbnailUrl   = req.cardThumbnailUrl();
        m.detailThumbnailUrl = req.detailThumbnailUrl();

        // Ensure at most one primary — if this new row claims it, demote the others.
        if (Boolean.TRUE.equals(m.isPrimary)) {
            for (var existing : p.media) existing.isPrimary = Boolean.FALSE;
        }
        p.media.add(m);
        media.persist(m);

        return p.media.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public void removeMedia(long productId, long mediaId) {
        var m = media.findByIdOptional(mediaId)
                .orElseThrow(() -> new NotFoundException("ProductMedia", mediaId));
        if (!m.product.id.equals(productId)) {
            throw new NotFoundException("ProductMedia", mediaId);
        }
        media.delete(m);
    }
}
