package com.gadaaistar.admin.quotes;

import java.util.List;

import com.gadaaistar.common.QuoteStatus;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
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
 * Admin quote management API.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@code GET  /quotes} — list quotes (optionally filtered by status).</li>
 *   <li>{@code GET  /quotes/{uuid}} — view quote detail.</li>
 *   <li>{@code POST /quotes/{uuid}/review} — start reviewing a submitted quote.</li>
 *   <li>{@code PATCH /quotes/{uuid}/items/{lineId}} — edit a line's proposed price.</li>
 *   <li>{@code POST /quotes/{uuid}/send} — send the quote to the customer.</li>
 *   <li>{@code POST /quotes/{uuid}/reject} — reject the quote.</li>
 *   <li>{@code POST /quotes/{uuid}/convert} — convert an accepted quote to an order.</li>
 * </ol>
 */
@Path("/api/admin/v1/quotes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Quotes (Admin)", description = "B2B quote review, pricing, and conversion.")
public class AdminQuoteResource {

    @Inject AdminQuoteService   service;
    @Inject AdminQuoteMapper    mapper;
    @Inject SecurityContextHolder security;

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "Paged list of all quotes, optionally filtered by status.")
    public PagedQuotes list(@QueryParam("status") QuoteStatus status,
                            @QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("20") int size) {
        security.requireAdmin();
        var clamped = Math.min(Math.max(size, 1), 100);
        var pageIdx = Math.max(page, 0);
        var rows  = service.list(status, pageIdx, clamped).stream().map(mapper::toSummary).toList();
        var total = service.count(status);
        return new PagedQuotes(rows, pageIdx, clamped, total);
    }

    @GET
    @Path("/{uuid}")
    @Operation(summary = "Full detail on a single quote — lines, customer, pricing.")
    public AdminQuoteDtos.QuoteDetail get(@PathParam("uuid") String uuid) {
        security.requireAdmin();
        return mapper.toDetail(service.get(uuid));
    }

    // -------------------------------------------------------------------------
    // Review
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/review")
    @Operation(summary = "Start reviewing a submitted quote — transitions to UNDER_REVIEW.")
    public AdminQuoteDtos.QuoteDetail startReview(@PathParam("uuid") String uuid) {
        security.requireAdmin();
        return mapper.toDetail(service.startReview(uuid));
    }

    // -------------------------------------------------------------------------
    // Edit line
    // -------------------------------------------------------------------------

    @PATCH
    @Path("/{uuid}/items/{lineId}")
    @Operation(summary = "Update the proposed unit price on a single quote line.")
    public AdminQuoteDtos.QuoteDetail updateLine(@PathParam("uuid") String uuid,
                                                  @PathParam("lineId") Long lineId,
                                                  @Valid AdminQuoteDtos.UpdateQuoteItemRequest req) {
        security.requireAdmin();
        return mapper.toDetail(service.updateLine(uuid, lineId, req));
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/send")
    @Operation(summary = "Send the quote to the customer with proposed pricing and validity period.")
    public AdminQuoteDtos.QuoteDetail send(@PathParam("uuid") String uuid,
                                            @Valid AdminQuoteDtos.SendQuoteRequest req) {
        security.requireAdmin();
        return mapper.toDetail(service.send(uuid, req));
    }

    // -------------------------------------------------------------------------
    // Reject
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/reject")
    @Operation(summary = "Reject the quote request.")
    public AdminQuoteDtos.QuoteDetail reject(@PathParam("uuid") String uuid,
                                              @Valid AdminQuoteDtos.RejectQuoteRequest req) {
        security.requireAdmin();
        return mapper.toDetail(service.reject(uuid, req));
    }

    // -------------------------------------------------------------------------
    // Convert to order
    // -------------------------------------------------------------------------

    @POST
    @Path("/{uuid}/convert")
    @Operation(summary = "Convert an accepted quote into a PENDING_PAYMENT order.")
    public Response convert(@PathParam("uuid") String uuid,
                            @Valid AdminQuoteDtos.ConvertQuoteRequest req) {
        security.requireAdmin();
        var order = service.convertToOrder(uuid, req);
        return Response.status(Response.Status.CREATED)
                .entity(new ConvertedOrder(order.uuid, order.invoiceNumber, order.grandTotal, order.currency))
                .build();
    }

    // -------------------------------------------------------------------------
    // Inline response records
    // -------------------------------------------------------------------------

    public record PagedQuotes(List<AdminQuoteDtos.QuoteSummary> items, int page, int size, long total) {}

    public record ConvertedOrder(String orderUuid, String invoiceNumber,
                                  java.math.BigDecimal grandTotal, String currency) {}
}
