package com.gadaaistar.storefront.quote;

import java.util.List;

import com.gadaaistar.storefront.ratelimit.RateLimit;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Storefront quote API for B2B customers.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@code POST /me/quotes} — create a quote request from the current cart.</li>
 *   <li>{@code GET  /me/quotes} — list the caller's quotes.</li>
 *   <li>{@code GET  /me/quotes/{uuid}} — view a quote's detail.</li>
 *   <li>{@code POST /me/quotes/{uuid}/accept} — accept a SENT quote.</li>
 * </ol>
 */
@Path("/api/storefront/v1/me/quotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
@Tag(name = "Quotes (Storefront)", description = "B2B quote requests.")
public class QuoteResource {

    @Inject QuoteService          quoteService;
    @Inject QuoteMapper           mapper;
    @Inject SecurityContextHolder security;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @POST
    @RateLimit(bucket = "quote-create", max = 5, windowSeconds = 300)
    @Operation(summary = "Create a quote request from the caller's current cart. B2B only.")
    public Response create(@Valid QuoteDtos.RequestQuoteRequest req) {
        var customerId = security.requireCustomer();
        var quote = quoteService.createFromCart(customerId, req);
        return Response.status(Response.Status.CREATED).entity(mapper.toDetail(quote)).build();
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "Paged list of the caller's quotes (most recent first).")
    public PagedQuotes list(@QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("20") int size) {
        var customerId = security.requireCustomer();
        var clamped = Math.min(Math.max(size, 1), 100);
        var pageIdx = Math.max(page, 0);
        var rows  = quoteService.listForCustomer(customerId, pageIdx, clamped)
                .stream().map(mapper::toSummary).toList();
        var total = quoteService.countForCustomer(customerId);
        return new PagedQuotes(rows, pageIdx, clamped, total);
    }

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Full detail — line items, pricing, status — for a single quote.")
    public QuoteDtos.QuoteDetail get(@PathParam("uuid") String uuid) {
        var customerId = security.requireCustomer();
        return mapper.toDetail(quoteService.getForCustomer(customerId, uuid));
    }

    // -------------------------------------------------------------------------
    // Accept
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/accept")
    @Operation(summary = "Accept a SENT quote. The admin will then convert it to an order.")
    public QuoteDtos.QuoteDetail accept(@PathParam("uuid") String uuid) {
        var customerId = security.requireCustomer();
        return mapper.toDetail(quoteService.accept(customerId, uuid));
    }

    // -------------------------------------------------------------------------
    // Inline response records
    // -------------------------------------------------------------------------

    public record PagedQuotes(List<QuoteDtos.QuoteSummary> items, int page, int size, long total) {}
}
