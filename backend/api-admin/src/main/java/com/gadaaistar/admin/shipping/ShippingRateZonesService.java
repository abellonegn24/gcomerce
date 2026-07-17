package com.gadaaistar.admin.shipping;

import java.util.List;
import java.util.Map;

import com.gadaaistar.common.exceptions.ConflictException;
import com.gadaaistar.common.exceptions.NotFoundException;
import com.gadaaistar.persistence.shipping.ShippingMethod;
import com.gadaaistar.persistence.shipping.ShippingMethodRepository;
import com.gadaaistar.persistence.shipping.ShippingRateZone;
import com.gadaaistar.persistence.shipping.ShippingRateZoneRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

/**
 * Admin CRUD for per-country shipping rate zones.
 *
 * <p>A zone overrides the base_cost and/or per_kg_rate of a shipping method
 * for a specific destination country. When no zone exists, the method's
 * default rates apply.
 */
@ApplicationScoped
public class ShippingRateZonesService {

    private static final Logger LOG = Logger.getLogger(ShippingRateZonesService.class);

    @Inject ShippingRateZoneRepository  zones;
    @Inject ShippingMethodRepository    methods;

    // -------------------------------------------------------------------------

    public List<ShippingDtos.ShippingRateZoneDto> listByMethod(long methodId) {
        resolveMethod(methodId);
        return zones.listByMethod(methodId).stream()
                .map(ShippingRateZonesService::toDto)
                .toList();
    }

    @Transactional
    public ShippingDtos.ShippingRateZoneDto create(ShippingDtos.CreateShippingRateZoneRequest req) {
        var method = resolveMethod(req.shippingMethodId());
        var country = req.countryCode().trim().toUpperCase();

        if (zones.existsForMethodAndCountry(method.id, country)) {
            throw new ConflictException("SHIPPING_ZONE_DUPLICATE",
                    "A rate zone for method '" + method.code + "' and country '" + country + "' already exists.",
                    Map.of("shippingMethodId", method.id, "countryCode", country));
        }

        var zone = new ShippingRateZone();
        zone.shippingMethod = method;
        zone.countryCode    = country;
        zone.baseCost       = req.baseCost();
        zone.perKgRate      = req.perKgRate();
        zone.minDays        = req.minDays();
        zone.maxDays        = req.maxDays();
        zone.active         = req.active() == null ? Boolean.TRUE : req.active();
        zones.persist(zone);

        LOG.infof("Created shipping rate zone id=%d method=%s country=%s",
                zone.id, method.code, country);
        return toDto(zone);
    }

    @Transactional
    public ShippingDtos.ShippingRateZoneDto update(long zoneId, ShippingDtos.UpdateShippingRateZoneRequest req) {
        var zone = zones.findByIdOptional(zoneId)
                .orElseThrow(() -> new NotFoundException("ShippingRateZone", zoneId));

        if (Boolean.TRUE.equals(req.clearBaseCost())) {
            zone.baseCost = null;
        } else if (req.baseCost() != null) {
            zone.baseCost = req.baseCost();
        }

        if (Boolean.TRUE.equals(req.clearPerKgRate())) {
            zone.perKgRate = null;
        } else if (req.perKgRate() != null) {
            zone.perKgRate = req.perKgRate();
        }

        if (Boolean.TRUE.equals(req.clearDeliveryDays())) {
            zone.minDays = null;
            zone.maxDays = null;
        } else {
            if (req.minDays() != null) zone.minDays = req.minDays();
            if (req.maxDays() != null) zone.maxDays = req.maxDays();
        }

        if (req.active() != null) zone.active = req.active();

        LOG.infof("Updated shipping rate zone id=%d country=%s active=%s",
                zone.id, zone.countryCode, zone.active);
        return toDto(zone);
    }

    @Transactional
    public void delete(long zoneId) {
        var zone = zones.findByIdOptional(zoneId)
                .orElseThrow(() -> new NotFoundException("ShippingRateZone", zoneId));
        zones.delete(zone);
        LOG.infof("Deleted shipping rate zone id=%d country=%s", zoneId, zone.countryCode);
    }

    // -------------------------------------------------------------------------

    private ShippingMethod resolveMethod(long methodId) {
        return methods.findByIdOptional(methodId)
                .orElseThrow(() -> new NotFoundException("ShippingMethod", methodId));
    }

    static ShippingDtos.ShippingRateZoneDto toDto(ShippingRateZone z) {
        return new ShippingDtos.ShippingRateZoneDto(
                z.id,
                z.shippingMethod == null ? null : z.shippingMethod.id,
                z.shippingMethod == null ? null : z.shippingMethod.code,
                z.countryCode,
                z.baseCost,
                z.perKgRate,
                z.minDays,
                z.maxDays,
                Boolean.TRUE.equals(z.active),
                z.createdAt,
                z.updatedAt
        );
    }
}
