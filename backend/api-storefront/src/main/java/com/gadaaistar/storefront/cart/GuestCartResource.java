package com.gadaaistar.storefront.cart;

import com.gadaaistar.web.ratelimit.RateLimit;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Guest cart endpoints — no authentication required. The caller supplies a
 * client-generated session key via the {@code X-Guest-Session-Key} header
 * (a UUID stored in localStorage). On login, the frontend calls
 * {@link CartService#mergeGuestCart} to fold the guest cart into the
 * customer's permanent cart.
 */
@Path("/api/storefront/v1/guest-cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Guest Cart (Storefront)", description = "Session-keyed cart for anonymous shoppers.")
public class GuestCartResource {

    @Inject CartService cartService;

    @GET
    @Operation(summary = "Return the guest cart for the given session key, creating an empty one on first read.")
    public CartDtos.CartResponse getCart(@HeaderParam("X-Guest-Session-Key") String sessionKey) {
        return cartService.getForSession(requireSessionKey(sessionKey));
    }

    @POST
    @Path("/items")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Add a product to the guest cart.")
    public CartDtos.CartResponse addItem(
            @HeaderParam("X-Guest-Session-Key") String sessionKey,
            @Valid CartDtos.AddItemRequest req) {
        return cartService.addItemForSession(requireSessionKey(sessionKey), req);
    }

    @PATCH
    @Path("/items/{productId}")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Update quantity of a line in the guest cart. Quantity of 0 removes the line.")
    public CartDtos.CartResponse updateItem(
            @HeaderParam("X-Guest-Session-Key") String sessionKey,
            @PathParam("productId") long productId,
            @jakarta.ws.rs.QueryParam("variantId") Long variantId,
            @Valid CartDtos.UpdateItemRequest req) {
        return cartService.updateItemForSession(requireSessionKey(sessionKey), productId, variantId, req);
    }

    @DELETE
    @Path("/items/{productId}")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Remove a line from the guest cart.")
    public CartDtos.CartResponse removeItem(
            @HeaderParam("X-Guest-Session-Key") String sessionKey,
            @PathParam("productId") long productId,
            @jakarta.ws.rs.QueryParam("variantId") Long variantId) {
        return cartService.removeItemForSession(requireSessionKey(sessionKey), productId, variantId);
    }

    @DELETE
    @Operation(summary = "Empty the guest cart.")
    public CartDtos.CartResponse clearCart(@HeaderParam("X-Guest-Session-Key") String sessionKey) {
        return cartService.clearForSession(requireSessionKey(sessionKey));
    }

    // -------------------------------------------------------------------------

    private static String requireSessionKey(String key) {
        if (key == null || key.isBlank()) {
            throw new com.gadaaistar.common.exceptions.BadRequestException(
                    "MISSING_SESSION_KEY",
                    "The X-Guest-Session-Key header is required for guest cart operations.");
        }
        return key.trim();
    }
}
