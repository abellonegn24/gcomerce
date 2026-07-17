package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.util.List;

import com.gadaaistar.common.exceptions.BadRequestException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.Inventory;
import com.gadaaistar.persistence.catalog.InventoryRepository;
import com.gadaaistar.persistence.catalog.ProductRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);

    @Inject InventoryRepository  inventories;
    @Inject ProductRepository    products;
    @Inject CatalogMapper        mapper;

    public List<CatalogDtos.InventoryDto> list() {
        return inventories.streamAll().map(mapper::toDto).toList();
    }

    public CatalogDtos.InventoryDto getForProduct(long productId) {
        var inv = inventories.findByProduct(productId)
                .orElseThrow(() -> new NotFoundException("Inventory (product)", productId));
        return mapper.toDto(inv);
    }

    /**
     * Ensure an inventory row exists for the given product, creating a
     * zero-quantity one if this is the first admin action against it.
     */
    @Transactional
    Inventory ensureRow(long productId) {
        return inventories.findByProduct(productId).orElseGet(() -> {
            var p = products.findByIdOptional(productId)
                    .orElseThrow(() -> new NotFoundException("Product", productId));
            var inv = new Inventory();
            inv.product = p;
            inv.quantity = BigDecimal.ZERO;
            inv.reservedQuantity = BigDecimal.ZERO;
            inventories.persist(inv);
            return inv;
        });
    }

    @Transactional
    public CatalogDtos.InventoryDto adjust(long productId, CatalogDtos.AdjustInventoryRequest req) {
        var inv = ensureRow(productId);
        var next = inv.quantity.add(req.delta());
        if (next.signum() < 0) {
            throw new BadRequestException("NEGATIVE_INVENTORY",
                    "Adjustment would make quantity negative (current="
                            + inv.quantity + ", delta=" + req.delta() + ").");
        }
        inv.quantity = next;
        LOG.infof("Inventory adjust product=%d delta=%s → %s (reason=%s)",
                productId, req.delta(), inv.quantity, req.reason());
        return mapper.toDto(inv);
    }

    @Transactional
    public CatalogDtos.InventoryDto set(long productId, CatalogDtos.SetInventoryRequest req) {
        var inv = ensureRow(productId);
        inv.quantity = req.quantity();
        if (req.restockThreshold() != null) {
            inv.restockThreshold = req.restockThreshold();
        }
        LOG.infof("Inventory set product=%d to %s (threshold=%s)",
                productId, req.quantity(), req.restockThreshold());
        return mapper.toDto(inv);
    }
}
