package com.gadaaistar.admin.shipping;

import java.util.List;
import java.util.Map;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.shipping.ShippingMethod;
import com.gadaaistar.persistence.shipping.ShippingMethodRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Admin-side CRUD for shipping methods. Storefront quoting has its own
 * service ({@code ShippingQuoteService}) — this class is strictly management.
 *
 * <p>Design notes:
 * <ul>
 *   <li>{@code code} is normalised to UPPER_SNAKE_CASE before persisting.
 *       Bean-validation already restricts input via a Pattern, but we
 *       {@code toUpperCase()} defensively so DB uniqueness comparisons are
 *       trivial.</li>
 *   <li>Currency is uppercased to ISO-3 form for the same reason.</li>
 *   <li>Duplicate {@code (code, currency)} is caught up-front instead of
 *       waiting for the DB unique-constraint violation, so the client sees
 *       a clean 409 with a code they can localise.</li>
 * </ul>
 */
@ApplicationScoped
public class ShippingMethodsService {

    private static final Logger LOG = Logger.getLogger(ShippingMethodsService.class);

    @Inject ShippingMethodRepository methods;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    public List<ShippingDtos.ShippingMethodDto> list() {
        return methods.listAll().stream().map(ShippingMethodsService::toDto).toList();
    }

    public ShippingDtos.ShippingMethodDto get(long id) {
        var m = methods.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("ShippingMethod", id));
        return toDto(m);
    }

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    @Transactional
    public ShippingDtos.ShippingMethodDto create(ShippingDtos.CreateShippingMethodRequest req) {
        var code     = req.code().trim().toUpperCase();
        var currency = req.currency().trim().toUpperCase();

        // Cheap pre-check for a friendlier error; a race can still trigger the
        // DB unique key, which propagates as a 500 — acceptable for admin UX.
        var existing = methods.find("code = ?1 AND currency = ?2", code, currency).firstResult();
        if (existing != null) {
            throw new ConflictException("SHIPPING_METHOD_DUPLICATE",
                    "A shipping method with code '" + code + "' already exists for " + currency + ".",
                    Map.of("code", code, "currency", currency));
        }

        var m = new ShippingMethod();
        m.code        = code;
        m.name        = req.name().trim();
        m.description = req.description() == null ? null : req.description().trim();
        m.currency    = currency;
        m.baseCost    = req.baseCost();
        m.perKgRate   = req.perKgRate();
        m.minDays     = req.minDays();
        m.maxDays     = req.maxDays();
        m.active      = req.active() == null ? Boolean.TRUE : req.active();
        m.sortOrder   = req.sortOrder() == null ? Integer.valueOf(100) : req.sortOrder();
        methods.persist(m);

        LOG.infof("Created shipping method id=%d code=%s currency=%s (base=%s + %s/kg)",
                m.id, m.code, m.currency, m.baseCost, m.perKgRate);
        return toDto(m);
    }

    @Transactional
    public ShippingDtos.ShippingMethodDto update(long id, ShippingDtos.UpdateShippingMethodRequest req) {
        var m = methods.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("ShippingMethod", id));

        // (code, currency) uniqueness — only re-check if either changed.
        var newCode     = req.code()     == null ? m.code     : req.code().trim().toUpperCase();
        var newCurrency = req.currency() == null ? m.currency : req.currency().trim().toUpperCase();
        if (!newCode.equals(m.code) || !newCurrency.equals(m.currency)) {
            var clash = methods.find("code = ?1 AND currency = ?2 AND id <> ?3",
                    newCode, newCurrency, m.id).firstResult();
            if (clash != null) {
                throw new ConflictException("SHIPPING_METHOD_DUPLICATE",
                        "Another shipping method with code '" + newCode + "' already exists for " + newCurrency + ".",
                        Map.of("code", newCode, "currency", newCurrency));
            }
        }
        m.code     = newCode;
        m.currency = newCurrency;

        if (req.name() != null && !req.name().isBlank()) {
            m.name = req.name().trim();
        }
        if (Boolean.TRUE.equals(req.clearDescription())) {
            m.description = null;
        } else if (req.description() != null) {
            m.description = req.description().trim().isEmpty() ? null : req.description().trim();
        }
        if (req.baseCost()  != null) m.baseCost  = req.baseCost();
        if (req.perKgRate() != null) m.perKgRate = req.perKgRate();
        if (Boolean.TRUE.equals(req.clearDeliveryDays())) {
            m.minDays = null;
            m.maxDays = null;
        } else {
            if (req.minDays() != null) m.minDays = req.minDays();
            if (req.maxDays() != null) m.maxDays = req.maxDays();
        }
        if (req.active()    != null) m.active    = req.active();
        if (req.sortOrder() != null) m.sortOrder = req.sortOrder();

        LOG.infof("Updated shipping method id=%d code=%s currency=%s active=%s",
                m.id, m.code, m.currency, m.active);
        return toDto(m);
    }

    @Transactional
    public void delete(long id) {
        var m = methods.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("ShippingMethod", id));
        // ON DELETE SET NULL on checkout_order.shipping_method_id means past
        // orders keep their snapshot code/name but lose the live FK. No need
        // to guard against that here.
        methods.delete(m);
        LOG.infof("Deleted shipping method id=%d code=%s", id, m.code);
    }

    // -------------------------------------------------------------------------

    static ShippingDtos.ShippingMethodDto toDto(ShippingMethod m) {
        return new ShippingDtos.ShippingMethodDto(
                m.id,
                m.code,
                m.name,
                m.description,
                m.currency,
                m.baseCost,
                m.perKgRate,
                m.minDays,
                m.maxDays,
                Boolean.TRUE.equals(m.active),
                m.sortOrder == null ? 100 : m.sortOrder,
                m.createdAt,
                m.updatedAt
        );
    }
}
