package com.gadaaistar.storefront.tax;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Read-only tax-inspection endpoint. Handy for the storefront to display
 * "VAT-inclusive" or "zero-rated for export" indicators at cart / checkout.
 */
@Path("/api/storefront/v1/tax")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Tax", description = "Tax rule lookups (read-only)")
public class TaxResource {

    @Inject TaxService taxService;

    @GET
    @Path("/rules/{countryCode}")
    @Operation(summary = "Return the applicable VAT rule for the given ship-to country.")
    public TaxService.TaxDecision resolve(@PathParam("countryCode") String countryCode) {
        return taxService.resolveFor(countryCode);
    }
}
