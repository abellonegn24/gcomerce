package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.Product;
import com.gadaaistar.persistence.catalog.ProductRepository;
import com.gadaaistar.persistence.catalog.ProductVariant;
import com.gadaaistar.persistence.catalog.ProductVariantAttribute;
import com.gadaaistar.persistence.catalog.ProductVariantRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

@ApplicationScoped
public class VariantsService {

    private static final Logger LOG = Logger.getLogger(VariantsService.class);

    @Inject ProductRepository       products;
    @Inject ProductVariantRepository variants;
    @Inject InventoryRepository     inventories;
    @Inject CatalogMapper           mapper;

    public List<CatalogDtos.VariantDto> list(long productId) {
        var p = products.findByIdOptional(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        return p.variants.stream().map(mapper::toVariantDto).toList();
    }

    @Transactional
    public CatalogDtos.VariantDto create(long productId, CatalogDtos.CreateVariantRequest req) {
        var p = products.findByIdOptional(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));

        if (variants.skuExists(req.sku())) {
            throw new ConflictException("VARIANT_SKU_TAKEN",
                    "A variant with this SKU already exists.",
                    java.util.Map.of("sku", req.sku()));
        }

        var v = new ProductVariant();
        v.sku              = req.sku();
        v.name             = req.name();
        v.priceOverride    = req.priceOverride();
        v.weightKgOverride = req.weightKgOverride();
        v.active           = req.active() == null ? Boolean.TRUE : req.active();
        v.sortOrder        = req.sortOrder() == null ? p.variants.size() : req.sortOrder();

        if (req.attributes() != null) {
            for (var attr : req.attributes()) {
                var a = new ProductVariantAttribute();
                a.variant   = v;
                a.attrKey   = attr.key();
                a.attrValue = attr.value();
                v.attributes.add(a);
            }
        }

        p.variants.add(v);
        variants.persist(v);

        var inv = new Inventory();
        inv.product = p;
        inv.variant = v;
        inv.quantity = BigDecimal.ZERO;
        inv.reservedQuantity = BigDecimal.ZERO;
        inventories.persist(inv);

        LOG.infof("Created variant id=%d sku=%s for product=%d", v.id, v.sku, productId);
        return mapper.toVariantDto(v);
    }

    @Transactional
    public CatalogDtos.VariantDto update(long productId, long variantId, CatalogDtos.UpdateVariantRequest req) {
        var v = variants.findByIdOptional(variantId)
                .orElseThrow(() -> new NotFoundException("ProductVariant", variantId));
        if (!v.product.id.equals(productId)) {
            throw new NotFoundException("ProductVariant", variantId);
        }

        if (req.sku() != null && !req.sku().equals(v.sku)) {
            variants.findBySku(req.sku()).ifPresent(other -> {
                if (!other.id.equals(v.id)) {
                    throw new ConflictException("VARIANT_SKU_TAKEN",
                            "Another variant already uses this SKU.",
                            java.util.Map.of("sku", req.sku()));
                }
            });
            v.sku = req.sku();
        }
        if (req.name() != null)             v.name             = req.name();
        if (req.priceOverride() != null)    v.priceOverride    = req.priceOverride();
        if (req.weightKgOverride() != null) v.weightKgOverride = req.weightKgOverride();
        if (req.active() != null)           v.active           = req.active();
        if (req.sortOrder() != null)        v.sortOrder        = req.sortOrder();

        if (req.attributes() != null) {
            v.attributes.clear();
            for (var attr : req.attributes()) {
                var a = new ProductVariantAttribute();
                a.variant   = v;
                a.attrKey   = attr.key();
                a.attrValue = attr.value();
                v.attributes.add(a);
            }
        }

        LOG.infof("Updated variant id=%d sku=%s", v.id, v.sku);
        return mapper.toVariantDto(v);
    }

    @Transactional
    public void delete(long productId, long variantId) {
        var v = variants.findByIdOptional(variantId)
                .orElseThrow(() -> new NotFoundException("ProductVariant", variantId));
        if (!v.product.id.equals(productId)) {
            throw new NotFoundException("ProductVariant", variantId);
        }
        v.product.variants.remove(v);
        variants.delete(v);
        LOG.infof("Deleted variant id=%d sku=%s from product=%d", v.id, v.sku, productId);
    }

    public List<CatalogDtos.VariantInventoryDto> listVariantInventory(long productId) {
        var p = products.findByIdOptional(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        return inventories.findAllByProduct(p.id).stream()
                .filter(inv -> inv.variant != null)
                .map(mapper::toVariantInventoryDto)
                .toList();
    }

    @Transactional
    public CatalogDtos.VariantInventoryDto adjustVariantInventory(long productId, long variantId,
                                                                    CatalogDtos.AdjustInventoryRequest req) {
        var v = variants.findByIdOptional(variantId)
                .orElseThrow(() -> new NotFoundException("ProductVariant", variantId));
        if (!v.product.id.equals(productId)) {
            throw new NotFoundException("ProductVariant", variantId);
        }

        var inv = inventories.findByVariant(variantId).orElseGet(() -> {
            var i = new Inventory();
            i.product = v.product;
            i.variant = v;
            i.quantity = BigDecimal.ZERO;
            i.reservedQuantity = BigDecimal.ZERO;
            inventories.persist(i);
            return i;
        });

        var next = inv.quantity.add(req.delta());
        if (next.signum() < 0) {
            throw new com.gadaaistar.common.exceptions.BadRequestException("NEGATIVE_INVENTORY",
                    "Adjustment would make quantity negative (current="
                            + inv.quantity + ", delta=" + req.delta() + ").");
        }
        inv.quantity = next;
        LOG.infof("Variant inventory adjust variant=%d delta=%s → %s (reason=%s)",
                variantId, req.delta(), inv.quantity, req.reason());
        return mapper.toVariantInventoryDto(inv);
    }

    @Transactional
    public CatalogDtos.VariantInventoryDto setVariantInventory(long productId, long variantId,
                                                                CatalogDtos.SetInventoryRequest req) {
        var v = variants.findByIdOptional(variantId)
                .orElseThrow(() -> new NotFoundException("ProductVariant", variantId));
        if (!v.product.id.equals(productId)) {
            throw new NotFoundException("ProductVariant", variantId);
        }

        var inv = inventories.findByVariant(variantId).orElseGet(() -> {
            var i = new Inventory();
            i.product = v.product;
            i.variant = v;
            i.quantity = BigDecimal.ZERO;
            i.reservedQuantity = BigDecimal.ZERO;
            inventories.persist(i);
            return i;
        });

        inv.quantity = req.quantity();
        if (req.restockThreshold() != null) {
            inv.restockThreshold = req.restockThreshold();
        }
        LOG.infof("Variant inventory set variant=%d to %s (threshold=%s)",
                variantId, req.quantity(), req.restockThreshold());
        return mapper.toVariantInventoryDto(inv);
    }
}
