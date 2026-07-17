package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.PriceList;
import com.gadaaistar.persistence.catalog.PriceListEntry;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductMedia;
import com.gadaaistar.persistence.catalog.ProductVariant;
import com.gadaaistar.persistence.catalog.ProductVariantAttribute;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CatalogMapper {

    @Inject InventoryRepository inventories;

    // -------------------------------------------------------------------------
    // Product
    // -------------------------------------------------------------------------

    public CatalogDtos.ProductDto toDto(Product p, Inventory inv) {
        return new CatalogDtos.ProductDto(
                p.id, p.sku, p.slug, p.name,
                p.shortDescription, p.description,
                p.category, p.status,
                p.weightKg, p.hsCode, p.originCountry,
                p.taxClassId, p.baseCurrency, p.basePrice, p.costPrice,
                p.minOrderQuantity, p.packagingNotes,
                p.media.stream().map(this::toDto).toList(),
                p.variants.stream().map(this::toVariantDto).toList(),
                toInventorySummary(inv),
                p.createdAt, p.updatedAt
        );
    }

    public CatalogDtos.ProductListItem toListItem(Product p, Inventory inv) {
        return new CatalogDtos.ProductListItem(
                p.id, p.sku, p.slug, p.name,
                p.category, p.status,
                p.baseCurrency, p.basePrice,
                inv == null ? BigDecimal.ZERO : inv.available(),
                p.primaryImageUrl(),
                p.updatedAt
        );
    }

    public CatalogDtos.ProductMediaDto toDto(ProductMedia m) {
        return new CatalogDtos.ProductMediaDto(
                m.id, m.url, m.altText, m.mediaType,
                Boolean.TRUE.equals(m.isPrimary),
                m.sortOrder == null ? 0 : m.sortOrder,
                m.cardThumbnailUrl,
                m.detailThumbnailUrl
        );
    }

    public CatalogDtos.InventorySummary toInventorySummary(Inventory inv) {
        if (inv == null) {
            return new CatalogDtos.InventorySummary(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        }
        return new CatalogDtos.InventorySummary(
                inv.quantity, inv.reservedQuantity, inv.available(),
                inv.restockThreshold, inv.isLow()
        );
    }

    // -------------------------------------------------------------------------
    // Price list
    // -------------------------------------------------------------------------

    public CatalogDtos.PriceListDto toDto(PriceList pl, int entryCount) {
        return new CatalogDtos.PriceListDto(
                pl.id,
                pl.customerGroup.id,
                pl.customerGroup.code,
                pl.name,
                pl.currency,
                Boolean.TRUE.equals(pl.active),
                pl.effectiveFrom,
                pl.effectiveTo,
                entryCount,
                pl.createdAt,
                pl.updatedAt
        );
    }

    public CatalogDtos.PriceListEntryDto toDto(PriceListEntry e) {
        return new CatalogDtos.PriceListEntryDto(
                e.id,
                e.product.id, e.product.sku, e.product.name,
                e.price, e.minQuantity
        );
    }

    public CatalogDtos.PriceListDetail toDetail(PriceList pl, List<PriceListEntry> entries) {
        return new CatalogDtos.PriceListDetail(
                toDto(pl, entries.size()),
                entries.stream().map(this::toDto).toList()
        );
    }

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    public CatalogDtos.InventoryDto toDto(Inventory inv) {
        return new CatalogDtos.InventoryDto(
                inv.product.id, inv.product.sku, inv.product.name,
                inv.quantity, inv.reservedQuantity, inv.available(),
                inv.restockThreshold, inv.isLow(),
                inv.updatedAt
        );
    }

    // -------------------------------------------------------------------------
    // Variants
    // -------------------------------------------------------------------------

    public CatalogDtos.VariantDto toVariantDto(ProductVariant v) {
        var inv = inventories.findByVariant(v.id).orElse(null);
        return new CatalogDtos.VariantDto(
                v.id, v.sku, v.name,
                v.priceOverride, v.weightKgOverride,
                Boolean.TRUE.equals(v.active),
                v.sortOrder == null ? 0 : v.sortOrder,
                v.attributes.stream().map(this::toAttrDto).toList(),
                inv == null ? BigDecimal.ZERO : inv.available(),
                v.createdAt, v.updatedAt
        );
    }

    public CatalogDtos.VariantAttributeDto toAttrDto(ProductVariantAttribute a) {
        return new CatalogDtos.VariantAttributeDto(a.attrKey, a.attrValue);
    }

    public CatalogDtos.VariantInventoryDto toVariantInventoryDto(Inventory inv) {
        var v = inv.variant;
        return new CatalogDtos.VariantInventoryDto(
                v.id, v.sku, v.name,
                inv.quantity, inv.reservedQuantity, inv.available(),
                inv.restockThreshold, inv.isLow(),
                inv.updatedAt
        );
    }
}
