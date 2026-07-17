package com.gadaaistar.admin.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.catalog.PriceListRepository;
import com.gadaaistar.persistence.customer.CustomerGroup;
import com.gadaaistar.persistence.customer.CustomerGroupRepository;
import com.gadaaistar.persistence.customer.CustomerRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * CRUD service for the admin {@code /customer-groups} page.
 *
 * <p>Business rules:
 * <ul>
 *   <li>{@code code} is globally unique (case-insensitive) — the seed
 *       migration inserts RETAIL / WHOLESALE_T1 / WHOLESALE_T2 / VIP.</li>
 *   <li>{@code moqMultiplier} defaults to {@code 1.000} on create when
 *       omitted; it must always be &ge; 1 (a value of 0.5 would let B2B
 *       customers buy <em>less</em> than the retail MOQ which is nonsensical).</li>
 *   <li>Deletion is blocked if any customer or price-list still references
 *       the group. Ops must reassign those first — a silent CASCADE would
 *       demote every B2B account to retail without warning.</li>
 * </ul>
 */
@ApplicationScoped
public class CustomerGroupsService {

    private static final Logger LOG = Logger.getLogger(CustomerGroupsService.class);

    @Inject CustomerGroupRepository groups;
    @Inject CustomerRepository      customers;
    @Inject PriceListRepository     priceLists;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<CustomerGroupsDtos.CustomerGroupDto> list() {
        return groups.listSorted().stream()
                .map(this::toDto)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public CustomerGroupsDtos.CustomerGroupDto create(CustomerGroupsDtos.CreateCustomerGroupRequest req) {
        var code = req.code().trim().toUpperCase();
        if (groups.codeExists(code)) {
            throw new ConflictException("CUSTOMER_GROUP_CODE_TAKEN",
                    "A customer group with code '" + code + "' already exists.",
                    Map.of("code", code));
        }

        var group = new CustomerGroup();
        group.code          = code;
        group.name          = req.name().trim();
        group.description   = req.description() == null || req.description().isBlank()
                              ? null : req.description().trim();
        group.priceTier     = req.priceTier();
        group.moqMultiplier = req.moqMultiplier() == null ? BigDecimal.ONE : req.moqMultiplier();
        group.active        = req.active() == null ? Boolean.TRUE : req.active();
        groups.persist(group);

        LOG.infof("Created customer group id=%d code=%s (tier=%s, moq×=%s)",
                group.id, group.code, group.priceTier, group.moqMultiplier);
        return toDto(group);
    }

    @Transactional
    public CustomerGroupsDtos.CustomerGroupDto update(long id,
                                                      CustomerGroupsDtos.UpdateCustomerGroupRequest req) {
        var group = groups.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("CustomerGroup", id));

        if (req.code() != null && !req.code().isBlank()) {
            var newCode = req.code().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(group.code) && groups.codeExists(newCode)) {
                throw new ConflictException("CUSTOMER_GROUP_CODE_TAKEN",
                        "A customer group with code '" + newCode + "' already exists.",
                        Map.of("code", newCode));
            }
            group.code = newCode;
        }
        if (req.name() != null && !req.name().isBlank()) {
            group.name = req.name().trim();
        }
        // Description: PATCH semantics — value means "set", clear flag means
        // "wipe", both null means "leave alone".
        if (Boolean.TRUE.equals(req.clearDescription())) {
            group.description = null;
        } else if (req.description() != null) {
            var trimmed = req.description().trim();
            group.description = trimmed.isEmpty() ? null : trimmed;
        }
        if (req.priceTier() != null) {
            group.priceTier = req.priceTier();
        }
        if (req.moqMultiplier() != null) {
            group.moqMultiplier = req.moqMultiplier();
        }
        if (req.active() != null) {
            group.active = req.active();
        }

        LOG.infof("Updated customer group id=%d code=%s (tier=%s, moq×=%s, active=%s)",
                group.id, group.code, group.priceTier, group.moqMultiplier, group.active);
        return toDto(group);
    }

    @Transactional
    public void delete(long id) {
        var group = groups.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("CustomerGroup", id));

        long customerCount  = customers.countInGroup(id);
        long priceListCount = priceLists.count("customerGroup.id", id);
        if (customerCount > 0 || priceListCount > 0) {
            throw new ConflictException("CUSTOMER_GROUP_IN_USE",
                    "Reassign customers and delete price lists first — "
                            + customerCount + " customer(s) and "
                            + priceListCount + " price list(s) still reference this group.",
                    Map.of("customerCount", customerCount,
                           "priceListCount", priceListCount));
        }

        groups.delete(group);
        LOG.infof("Deleted customer group id=%d code=%s", id, group.code);
    }

    // -------------------------------------------------------------------------

    private CustomerGroupsDtos.CustomerGroupDto toDto(CustomerGroup g) {
        long customerCount = g.id == null ? 0L : customers.countInGroup(g.id);
        return new CustomerGroupsDtos.CustomerGroupDto(
                g.id,
                g.code,
                g.name,
                g.description,
                g.priceTier,
                g.moqMultiplier,
                g.active,
                customerCount,
                g.createdAt,
                g.updatedAt
        );
    }
}
