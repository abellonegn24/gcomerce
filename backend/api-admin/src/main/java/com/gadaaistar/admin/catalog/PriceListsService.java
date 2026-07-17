package com.gadaaistar.admin.catalog;

import java.util.List;
import java.util.Optional;

import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.PriceList;
import com.gadaaistar.persistence.catalog.PriceListEntry;
import com.gadaaistar.persistence.catalog.PriceListEntryRepository;
import com.gadaaistar.persistence.catalog.PriceListRepository;
import com.gadaaistar.persistence.catalog.ProductRepository;
import com.gadaaistar.persistence.customer.CustomerGroupRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

@ApplicationScoped
public class PriceListsService {

    private static final Logger LOG = Logger.getLogger(PriceListsService.class);

    @Inject PriceListRepository        priceLists;
    @Inject PriceListEntryRepository   entries;
    @Inject ProductRepository          products;
    @Inject CustomerGroupRepository    groups;
    @Inject CatalogMapper              mapper;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<CatalogDtos.PriceListDto> list() {
        return priceLists.streamAll()
                .toList()
                .stream()
                .map(pl -> mapper.toDto(pl, (int) entries.count("priceList.id", pl.id)))
                .toList();
    }

    public CatalogDtos.PriceListDetail get(long id) {
        var pl = priceLists.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("PriceList", id));
        return mapper.toDetail(pl, entries.findByPriceList(pl.id));
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public CatalogDtos.PriceListDto create(CatalogDtos.CreatePriceListRequest req) {
        var group = groups.findByIdOptional(req.customerGroupId())
                .orElseThrow(() -> new NotFoundException("CustomerGroup", req.customerGroupId()));

        var pl = new PriceList();
        pl.customerGroup = group;
        pl.name          = req.name();
        pl.currency      = req.currency().toUpperCase();
        pl.active        = req.active() == null ? Boolean.TRUE : req.active();
        pl.effectiveFrom = req.effectiveFrom();
        pl.effectiveTo   = req.effectiveTo();
        priceLists.persist(pl);

        LOG.infof("Created price list id=%d for group=%s (%s)", pl.id, group.code, pl.currency);
        return mapper.toDto(pl, 0);
    }

    @Transactional
    public void delete(long id) {
        var pl = priceLists.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("PriceList", id));
        entries.deleteByPriceList(pl.id);
        priceLists.delete(pl);
        LOG.infof("Deleted price list id=%d and its entries", id);
    }

    /**
     * Partial update. Only fields present in the request are touched. The
     * bound customer group is intentionally immutable — retargeting a list
     * from RETAIL to WHOLESALE_T1 would silently move every tier and is
     * almost always a mistake; delete + recreate is safer.
     */
    @Transactional
    public CatalogDtos.PriceListDto update(long id, CatalogDtos.UpdatePriceListRequest req) {
        var pl = priceLists.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("PriceList", id));

        if (req.name() != null && !req.name().isBlank()) {
            pl.name = req.name().trim();
        }
        if (req.currency() != null && !req.currency().isBlank()) {
            pl.currency = req.currency().trim().toUpperCase();
        }
        if (req.active() != null) {
            pl.active = req.active();
        }
        // Effective-window fields: null means "leave as-is"; use the
        // clearXxx booleans to explicitly wipe the value.
        if (Boolean.TRUE.equals(req.clearEffectiveFrom())) {
            pl.effectiveFrom = null;
        } else if (req.effectiveFrom() != null) {
            pl.effectiveFrom = req.effectiveFrom();
        }
        if (Boolean.TRUE.equals(req.clearEffectiveTo())) {
            pl.effectiveTo = null;
        } else if (req.effectiveTo() != null) {
            pl.effectiveTo = req.effectiveTo();
        }

        int entryCount = (int) entries.count("priceList.id", pl.id);
        LOG.infof("Updated price list id=%d (name='%s', currency=%s, active=%s, effective=%s→%s)",
                pl.id, pl.name, pl.currency, pl.active, pl.effectiveFrom, pl.effectiveTo);
        return mapper.toDto(pl, entryCount);
    }

    // -------------------------------------------------------------------------
    // Entries
    // -------------------------------------------------------------------------

    @Transactional
    public CatalogDtos.PriceListEntryDto upsertEntry(long priceListId, CatalogDtos.UpsertPriceEntryRequest req) {
        var pl = priceLists.findByIdOptional(priceListId)
                .orElseThrow(() -> new NotFoundException("PriceList", priceListId));
        var product = products.findByIdOptional(req.productId())
                .orElseThrow(() -> new NotFoundException("Product", req.productId()));

        Optional<PriceListEntry> existing = entries.find(
                "priceList.id = ?1 AND product.id = ?2 AND minQuantity = ?3",
                pl.id, product.id, req.minQuantity()
        ).firstResultOptional();

        PriceListEntry entry;
        if (existing.isPresent()) {
            entry = existing.get();
            entry.price = req.price();
        } else {
            entry = new PriceListEntry();
            entry.priceList = pl;
            entry.product   = product;
            entry.price     = req.price();
            entry.minQuantity = req.minQuantity();
            entries.persist(entry);
        }
        return mapper.toDto(entry);
    }

    @Transactional
    public void deleteEntry(long priceListId, long entryId) {
        var entry = entries.findByIdOptional(entryId)
                .orElseThrow(() -> new NotFoundException("PriceListEntry", entryId));
        if (!entry.priceList.id.equals(priceListId)) {
            throw new NotFoundException("PriceListEntry", entryId);
        }
        entries.delete(entry);
    }
}
