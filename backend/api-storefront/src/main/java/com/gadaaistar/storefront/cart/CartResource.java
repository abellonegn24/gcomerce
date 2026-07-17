package com.gadaaistar.storefront.cart;

import com.gadaaistar.storefront.ratelimit.RateLimit;
import com.gadaaistar.web.security.SecurityContextHolder;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Cart endpoints for the signed-in customer. All routes require an active
 * Bearer token (enforced via {@link SecurityContextHolder#requireCustomer()}).
 *
 * <p>Rate-limited under a shared {@code cart-mutate} bucket to blunt any
 * accidental client-side loops without punishing legitimate rapid quantity
 * tweaks.
 */
@Path("/api/storefront/v1/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Cart (Storefront)", description = "The signed-in customer's shopping cart.")
public class CartResource {

    @Inject CartService           cartService;
    @Inject SecurityContextHolder security;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @GET
    @Operation(summary = "Return the current customer's cart, creating an empty one on first read.")
    public CartDtos.CartResponse getCart() {
        var customerId = security.requireCustomer();
        return cartService.getForCustomer(customerId);
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    @POST
    @Path("/items")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Add a product (or increment its quantity) in the current cart.")
    public CartDtos.CartResponse addItem(@Valid CartDtos.AddItemRequest req) {
        var customerId = security.requireCustomer();
        return cartService.addItem(customerId, req);
    }

    @PATCH
    @Path("/items/{productId}")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Overwrite the quantity of an existing line. Quantity of 0 removes the line.")
    public CartDtos.CartResponse updateItem(@PathParam("productId") long productId,
                                             @jakarta.ws.rs.QueryParam("variantId") Long variantId,
                                             @Valid CartDtos.UpdateItemRequest req) {
        var customerId = security.requireCustomer();
        return cartService.updateItem(customerId, productId, variantId, req);
    }

    @DELETE
    @Path("/items/{productId}")
    @RateLimit(bucket = "cart-mutate", max = 60, windowSeconds = 60)
    @Operation(summary = "Remove a single line from the cart.")
    public CartDtos.CartResponse removeItem(@PathParam("productId") long productId,
                                             @jakarta.ws.rs.QueryParam("variantId") Long variantId) {
        var customerId = security.requireCustomer();
        return cartService.removeItem(customerId, productId, variantId);
    }

    @DELETE
    @Operation(summary = "Empty the cart in one call.")
    public CartDtos.CartResponse clearCart() {
        var customerId = security.requireCustomer();
        return cartService.clear(customerId);
    }

    @POST
    @Path("/merge")
    @Operation(summary = "Merge a guest session cart into the signed-in customer's cart.")
    public CartDtos.CartResponse mergeGuestCart(
            @jakarta.ws.rs.HeaderParam("X-Guest-Session-Key") String sessionKey) {
        var customerId = security.requireCustomer();
        return cartService.mergeGuestCart(customerId, sessionKey);
    }
}
