package com.gadaaistar.storefront.shipping;

import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Storefront-facing shipping quote endpoint. The signed-in customer hits
 * this from the checkout picker to see rates for their current cart.
 *
 * <p>There is no request body — the cart owns both the currency and the
 * weight math, so the endpoint takes no query params either. This means
 * quotes cannot go stale between page load and placement: the picker fetch,
 * the placement compute, and the persisted snapshot all see the same cart
 * state.
 */
@Path("/api/storefront/v1/me/shipping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Shipping (Storefront)",
     description = "Live shipping quotes for the signed-in customer's current cart.")
public class ShippingResource {

    @Inject ShippingQuoteService  service;
    @Inject SecurityContextHolder security;

    @GET
    @Path("/quotes")
    @Operation(summary = "List every currently-active shipping option for the caller's cart, "
                       + "with cost already computed against the cart's total weight. "
                       + "Pass countryCode to get per-country rate overrides.")
    public ShippingDtos.ShippingQuoteListResponse quotes(
            @QueryParam("countryCode") String countryCode) {
        var customerId = security.requireCustomer();
        return service.quotesForCustomer(customerId, countryCode);
    }
}
