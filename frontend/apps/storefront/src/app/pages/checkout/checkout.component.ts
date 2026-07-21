import { ChangeDetectionStrategy, Component, DOCUMENT, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AddressService } from '../../core/addresses/address.service';
import { Address, CreateAddressPayload } from '../../core/addresses/address.model';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderService } from '../../core/orders/order.service';
import { PaymentMethodOption } from '../../core/orders/order.model';
import { PaymentMethodsService } from '../../core/orders/payment-methods.service';
import { ShippingQuote } from '../../core/shipping/shipping.model';
import { ShippingService } from '../../core/shipping/shipping.service';

import { environment } from '../../../environments/environment';

/**
 * /checkout — the customer-facing checkout flow.
 *
 * Flow:
 *   1. Show the caller's cart lines + subtotal.
 *   2. Let them pick a saved address OR create a new one inline.
 *   3. Submit: server places the order (status=PENDING_PAYMENT).
 *   4. Fetch payment-payload → dynamically build a form pre-filled with the
 *      signed CyberSource fields → auto-submit to the hosted checkout page.
 *
 * The form has {@code method="post"} and {@code target="_self"} so the browser
 * navigates to CyberSource with a full page load — Angular router state is
 * left behind intentionally, since we come back via {@code /checkout/return}.
 */
@Component({
  selector: 'gi-checkout',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly doc = inject(DOCUMENT);

  private readonly cartService = inject(CartService);
  private readonly addressService = inject(AddressService);
  private readonly orderService = inject(OrderService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly shippingService = inject(ShippingService);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);

  // --- Cart -----------------------------------------------------------------
  protected readonly cart = this.cartService.cart;
  protected readonly cartLoading = this.cartService.loading;

  protected readonly isCartEmpty = computed<boolean>(() => {
    const c = this.cart();
    return !this.cartLoading() && (c === null || c.items.length === 0);
  });

  // --- Addresses ------------------------------------------------------------
  protected readonly addresses = this.addressService.addresses;
  protected readonly addressesLoading = this.addressService.loading;

  /** Currently selected address id (drives the payload). */
  protected readonly selectedAddressId = signal<number | null>(null);

  /** True when the "add address" inline form is open. */
  protected readonly newAddressOpen = signal<boolean>(false);

  protected readonly newAddressForm = this.fb.nonNullable.group({
    fullName:    ['', [Validators.maxLength(200)]],
    company:     ['', [Validators.maxLength(255)]],
    streetLine1: ['', [Validators.required, Validators.maxLength(255)]],
    streetLine2: ['', [Validators.maxLength(255)]],
    city:        ['', [Validators.required, Validators.maxLength(120)]],
    region:      ['', [Validators.maxLength(120)]],
    postalCode:  ['', [Validators.maxLength(32)]],
    countryCode: ['ET', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    phone:       ['', [Validators.maxLength(64)]],
  });

  // --- Shipping -------------------------------------------------------------
  /** Pre-computed quotes for the current cart. Null while loading. */
  protected readonly shippingQuotes = signal<ShippingQuote[] | null>(null);
  /** Total cart weight the server used to compute the quotes (kg). */
  protected readonly shippingWeightKg = signal<string | number>(0);
  protected readonly shippingLoading = signal<boolean>(false);
  protected readonly shippingError = signal<string | null>(null);
  /**
   * Selected shipping method id. {@code null} means "no method chosen yet"
   * (blocks placement until the customer picks one, unless the quote list
   * came back empty — in which case placement proceeds with no shipping).
   */
  protected readonly selectedShippingId = signal<number | null>(null);

  /** Convenience — the resolved ShippingQuote for the current selection. */
  protected readonly selectedShipping = computed<ShippingQuote | null>(() => {
    const id = this.selectedShippingId();
    if (id === null) return null;
    return this.shippingQuotes()?.find((q) => q.id === id) ?? null;
  });

  /** Estimated total = cart subtotal + selected shipping - coupon discount (tax added at payment). */
  protected readonly estimatedTotal = computed<number>(() => {
    const c = this.cart();
    if (!c) return 0;
    const sub = Number(c.subtotal);
    const s = this.selectedShipping();
    const ship = s == null ? 0 : Number(s.cost);
    const preview = this.couponPreview();
    const discount = preview ? Number(preview.discountAmount) : 0;
    return (isFinite(sub) ? sub : 0) + (isFinite(ship) ? ship : 0) - (isFinite(discount) ? discount : 0);
  });

  // --- Coupon ---------------------------------------------------------------
  protected readonly couponCode = signal<string>('');
  protected readonly couponPreview = signal<CouponPreviewResponse | null>(null);
  protected readonly couponLoading = signal<boolean>(false);
  protected readonly couponError = signal<string | null>(null);

  // --- Payment methods ------------------------------------------------------
  protected readonly paymentMethods = signal<PaymentMethodOption[]>([]);
  protected readonly selectedPaymentMethod = signal<string>('MPGS');
  protected readonly paymentMethodsLoading = signal<boolean>(false);

  // --- Submission state -----------------------------------------------------
  protected readonly customerNotes = signal<string>('');
  protected readonly placing = signal<boolean>(false);
  protected readonly redirecting = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    // Cart may already be loaded via the AuthService-driven effect; hit the
    // API anyway to make sure we're in sync at checkout time. Once the
    // reload returns we fetch shipping quotes — those need a live cart.
    this.cartService.reload().subscribe({
      next: () => this.loadShippingQuotes(),
      error: () => this.loadShippingQuotes(), // still try — the cart may already be cached
    });
    this.addressService.list(true).subscribe({
      next: (list) => {
        // Pre-select the customer's default address, or the first if any.
        const preferred = list.find((a) => a.isDefault) ?? list[0] ?? null;
        if (preferred) this.selectedAddressId.set(preferred.id);
        if (list.length === 0) this.newAddressOpen.set(true);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load your addresses.');
      },
    });
    // Load available payment methods from the backend.
    this.paymentMethodsLoading.set(true);
    this.paymentMethodsService.list().subscribe({
      next: (methods) => {
        this.paymentMethodsLoading.set(false);
        this.paymentMethods.set(methods);
        // Prefer MPGS as the default payment method.
        const mpgs = methods.find((m) => m.id === 'MPGS');
        if (mpgs) {
          this.selectedPaymentMethod.set('MPGS');
        } else if (methods.length > 0) {
          this.selectedPaymentMethod.set(methods[0].id);
        }
      },
      error: () => {
        this.paymentMethodsLoading.set(false);
        // If the endpoint isn't available, fall back to MPGS.
        this.paymentMethods.set([{ id: 'MPGS', name: 'Mastercard (MPGS)', description: 'Pay via Mastercard Payment Gateway', logoUrl: null }]);
        this.selectedPaymentMethod.set('MPGS');
      },
    });
  }

  /**
   * Fetch the pre-computed shipping quotes for the caller's current cart.
   * Auto-selects the cheapest option — the customer can override before
   * placing.
   */
  private loadShippingQuotes(): void {
    this.shippingLoading.set(true);
    this.shippingError.set(null);
    this.shippingService.getQuotes().subscribe({
      next: (res) => {
        this.shippingLoading.set(false);
        this.shippingQuotes.set(res.quotes);
        this.shippingWeightKg.set(res.totalWeightKg);
        // Auto-select cheapest option by cost, ties broken by sortOrder.
        const cheapest = [...res.quotes].sort((a, b) => {
          const ca = Number(a.cost); const cb = Number(b.cost);
          if (ca !== cb) return ca - cb;
          return a.sortOrder - b.sortOrder;
        })[0];
        if (cheapest) this.selectedShippingId.set(cheapest.id);
      },
      error: (err: NormalizedApiError) => {
        this.shippingLoading.set(false);
        this.shippingQuotes.set([]);
        // Don't hard-fail the whole page — the customer can still place
        // with no shipping if the admin has configured zero methods, or
        // fall back to displaying an inline banner in the picker area.
        this.shippingError.set(err.message || 'Could not load shipping options.');
      },
    });
  }

  protected selectShipping(q: ShippingQuote): void {
    this.selectedShippingId.set(q.id);
  }

  // -------------------------------------------------------------------------

  protected selectAddress(a: Address): void {
    this.selectedAddressId.set(a.id);
  }

  protected openNewAddress(): void {
    this.newAddressOpen.set(true);
  }

  protected cancelNewAddress(): void {
    this.newAddressForm.reset({ countryCode: 'ET' });
    this.newAddressOpen.set(false);
  }

  protected saveNewAddress(): void {
    if (this.newAddressForm.invalid) {
      this.newAddressForm.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    const raw = this.newAddressForm.getRawValue();
    const list = this.addresses() ?? [];
    const payload: CreateAddressPayload = {
      addressType: 'BOTH',
      isDefault: list.length === 0,
      streetLine1: raw.streetLine1,
      city: raw.city,
      countryCode: raw.countryCode.toUpperCase(),
      ...(raw.fullName ? { fullName: raw.fullName } : {}),
      ...(raw.company ? { company: raw.company } : {}),
      ...(raw.streetLine2 ? { streetLine2: raw.streetLine2 } : {}),
      ...(raw.region ? { region: raw.region } : {}),
      ...(raw.postalCode ? { postalCode: raw.postalCode } : {}),
      ...(raw.phone ? { phone: raw.phone } : {}),
    };
    this.addressService.create(payload).subscribe({
      next: (created) => {
        this.selectedAddressId.set(created.id);
        this.cancelNewAddress();
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not save that address.');
      },
    });
  }

  // -------------------------------------------------------------------------

  protected applyCoupon(): void {
    const code = this.couponCode().trim();
    if (!code) return;
    this.couponLoading.set(true);
    this.couponError.set(null);
    this.http.get<CouponPreviewResponse>(
      `${environment.apiBaseUrl}/me/coupon-preview`,
      { params: { code } },
    ).subscribe({
      next: (res) => {
        this.couponLoading.set(false);
        this.couponPreview.set(res);
      },
      error: (err: NormalizedApiError) => {
        this.couponLoading.set(false);
        this.couponPreview.set(null);
        this.couponError.set(err.message || 'Invalid coupon code.');
      },
    });
  }

  protected removeCoupon(): void {
    this.couponCode.set('');
    this.couponPreview.set(null);
    this.couponError.set(null);
  }

  // -------------------------------------------------------------------------

  protected canPlaceOrder(): boolean {
    // Shipping is required only when the admin has configured at least one
    // method — an empty quote list means "no shipping options currently
    // configured", and we allow placement anyway (server sets shippingTotal
    // to 0 for that case). This lets a fresh install still transact.
    const quotes = this.shippingQuotes();
    const shippingSatisfied =
      quotes === null                                    // still loading
        ? false
        : quotes.length === 0
          ? true
          : this.selectedShippingId() !== null;
    return (
      !this.placing() &&
      !this.redirecting() &&
      !this.isCartEmpty() &&
      this.selectedAddressId() !== null &&
      shippingSatisfied &&
      this.auth.isAuthenticated()
    );
  }

  protected placeOrder(): void {
    const addressId = this.selectedAddressId();
    if (!addressId) {
      this.errorMessage.set('Please choose or add a shipping address first.');
      return;
    }
    this.errorMessage.set(null);
    this.placing.set(true);

    const shippingId = this.selectedShippingId();
    const couponCode = this.couponPreview()?.code;
    this.orderService
      .place({
        shipToAddressId: addressId,
        shippingMethodId: shippingId ?? undefined,
        couponCode: couponCode || undefined,
        customerNotes: this.customerNotes().trim() || undefined,
        paymentMethod: this.selectedPaymentMethod(),
      })
      .subscribe({
        next: (order) => {
          // Cart is emptied server-side on placement — mirror that locally
          // so the header badge updates instantly.
          this.cartService.reset();
          this.fetchPaymentPayloadAndRedirect(order.uuid);
        },
        error: (err: NormalizedApiError) => {
          this.placing.set(false);
          this.errorMessage.set(err.message || 'Could not place your order.');
        },
      });
  }

  // -------------------------------------------------------------------------

  private fetchPaymentPayloadAndRedirect(orderUuid: string): void {
    this.orderService.paymentPayload(orderUuid).subscribe({
      next: (payload) => {
        this.placing.set(false);
        this.redirecting.set(true);
        if (payload.provider === 'MPGS') {
          this.initMpgsCheckout(payload.gatewayUrl, payload.fields, orderUuid);
        } else {
          this.autoPostToGateway(payload.gatewayUrl, payload.fields);
        }
      },
      error: (err: NormalizedApiError) => {
        this.placing.set(false);
        if (err.code === 'PAYMENT_GATEWAY_NOT_CONFIGURED') {
          // Dev / staging path — take the customer to the order detail so
          // they can see the placed-but-unpaid state.
          void this.router.navigate(['/account/orders', orderUuid], {
            queryParams: { gatewayMissing: '1' },
          });
        } else {
          this.errorMessage.set(err.message || 'Could not start payment. Please try again.');
        }
      },
    });
  }

  /**
   * Load the MPGS Checkout.js script and configure the hosted checkout
   * lightbox. The script URL is provided by the backend in
   * {@link payload.gatewayUrl}; the session config (merchantId, sessionId,
   * successIndicator) comes in {@link payload.fields}.
   */
  private initMpgsCheckout(checkoutJsUrl: string, fields: Record<string, string>, orderUuid: string): void {
    const script = this.doc.createElement('script');
    script.src = checkoutJsUrl;
    script.onload = () => {
      const checkout = (window as any).Checkout;
      if (!checkout) {
        this.redirecting.set(false);
        this.errorMessage.set('MPGS Checkout.js failed to load. Please try again.');
        return;
      }
      checkout.configure({
        merchant: fields['merchantId'],
        session: {
          id: fields['sessionId'],
        },
        order: {
          amount: fields['orderAmount'] ?? undefined,
          currency: fields['orderCurrency'] ?? undefined,
          description: fields['merchantName'] ?? 'Gadaa Istar',
          id: fields['orderId'] ?? undefined,
        },
        interaction: {
          merchant: {
            name: fields['merchantName'] ?? 'Gadaa Istar',
          },
        },
      }, (result: any) => {
        // Checkout.js calls this callback after the payment interaction.
        // resultIndicator matches successIndicator on success.
        const successIndicator = fields['successIndicator'];
        if (result && result.resultIndicator && successIndicator
            && result.resultIndicator === successIndicator) {
          // Payment successful — call the backend to pull the MPGS order
          // status and mark the order PAID, then navigate to the return page.
          this.orderService.confirmMpgsPayment(orderUuid).subscribe({
            next: () => {
              void this.router.navigate(['/checkout/return'], {
                queryParams: { order: orderUuid, provider: 'MPGS' },
              });
            },
            error: () => {
              // Even if the confirmation call fails, navigate to the return
              // page — it polls the order status and will eventually pick up
              // the PAID state from the webhook or a retry.
              void this.router.navigate(['/checkout/return'], {
                queryParams: { order: orderUuid, provider: 'MPGS' },
              });
            },
          });
        } else {
          // Payment declined or cancelled.
          this.redirecting.set(false);
          this.errorMessage.set('Payment was not completed. Please try again.');
        }
      });
      // Some versions of Checkout.js don't auto-open the lightbox.
      // Call showLightbox() explicitly to ensure the payment modal appears.
      if (typeof checkout.showLightbox === 'function') {
        checkout.showLightbox();
      }
    };
    script.onerror = () => {
      this.redirecting.set(false);
      this.errorMessage.set('Could not load the payment page. Please try again.');
    };
    this.doc.head.appendChild(script);
  }

  /**
   * Dynamically create + submit a form to the gateway. The signature was
   * computed server-side against the exact key/value pairs we're posting; we
   * MUST NOT re-order them here, so we iterate the object in insertion order.
   */
  private autoPostToGateway(gatewayUrl: string, fields: Record<string, string>): void {
    const form = this.doc.createElement('form');
    form.method = 'POST';
    form.action = gatewayUrl;
    form.style.display = 'none';
    for (const [key, value] of Object.entries(fields)) {
      const input = this.doc.createElement('input');
      input.type = 'hidden';
      input.name = key;
      input.value = value ?? '';
      form.appendChild(input);
    }
    this.doc.body.appendChild(form);
    form.submit();
  }

  // -------------------------------------------------------------------------

  protected formatMoney(currency: string, amount: string | number): string {
    const value = typeof amount === 'string' ? Number(amount) : amount;
    if (!isFinite(value)) return `${currency} ${amount}`;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }
}

interface CouponPreviewResponse {
  code: string;
  description: string | null;
  discountType: string;
  discountValue: string;
  currency: string;
  discountAmount: string;
  freeShipping: boolean;
  subtotal: string;
  discountedSubtotal: string;
}
