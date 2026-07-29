import { ChangeDetectionStrategy, Component, DOCUMENT, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderDetail, OrderStatus, PaymentMethodOption } from '../../core/orders/order.model';
import { OrderService } from '../../core/orders/order.service';
import { PaymentMethodsService } from '../../core/orders/payment-methods.service';

/**
 * /account/orders/:uuid — line-by-line detail + status history for a single
 * placed order. Also handles the "retry payment" and "cancel" affordances for
 * a still-pending order.
 */
@Component({
  selector: 'gi-account-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountOrderDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly doc = inject(DOCUMENT);

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly cancelling = signal<boolean>(false);
  protected readonly startingPayment = signal<boolean>(false);
  protected readonly checkingPayment = signal<boolean>(false);

  // Payment method selection
  protected readonly paymentMethods = signal<PaymentMethodOption[]>([]);
  protected readonly paymentMethodsLoading = signal<boolean>(false);
  protected readonly selectedPaymentMethod = signal<string>('');

  /**
   * If the customer arrived from the checkout page's payment-not-configured
   * fallback we flag it so the UI hints at what to do next.
   */
  protected readonly gatewayMissing = signal<boolean>(false);

  protected readonly canCancel = computed<boolean>(
    () => this.order()?.status === 'PENDING_PAYMENT',
  );

  /**
   * True when the order is awaiting payment and has no payment method set yet
   * (e.g. orders created from quote conversion). The UI shows a payment method
   * picker instead of immediately redirecting to the gateway.
   */
  protected readonly needsPaymentMethodSelection = computed<boolean>(() => {
    const o = this.order();
    return !!o && o.status === 'PENDING_PAYMENT' && !o.paymentMethod;
  });

  ngOnInit(): void {
    const uuid = this.route.snapshot.paramMap.get('uuid');
    this.gatewayMissing.set(this.route.snapshot.queryParamMap.get('gatewayMissing') === '1');
    if (!uuid) {
      this.errorMessage.set('Missing order reference.');
      this.loading.set(false);
      return;
    }
    this.load(uuid);
  }

  private load(uuid: string): void {
    this.loading.set(true);
    this.orderService.get(uuid).subscribe({
      next: (o) => {
        this.order.set(o);
        this.loading.set(false);
        // If the order is awaiting payment without a payment method, load the
        // available payment methods so the customer can pick one.
        if (o.status === 'PENDING_PAYMENT' && !o.paymentMethod) {
          this.loadPaymentMethods();
        }
      },
      error: (err: NormalizedApiError) => {
        this.loading.set(false);
        if (err.status === 404) {
          void this.router.navigate(['/account/orders']);
          return;
        }
        this.errorMessage.set(err.message || 'Could not load this order.');
      },
    });
  }

  private loadPaymentMethods(): void {
    this.paymentMethodsLoading.set(true);
    this.paymentMethodsService.list().subscribe({
      next: (methods) => {
        this.paymentMethodsLoading.set(false);
        this.paymentMethods.set(methods);
        // Prefer MPGS (Mastercard) as the default payment method.
        if (!this.selectedPaymentMethod()) {
          const mpgs = methods.find((m) => m.id === 'MPGS');
          if (mpgs) {
            this.selectedPaymentMethod.set('MPGS');
          } else if (methods.length > 0) {
            this.selectedPaymentMethod.set(methods[0].id);
          }
        }
      },
      error: () => {
        this.paymentMethodsLoading.set(false);
      },
    });
  }

  protected retryPayment(): void {
    const o = this.order();
    if (!o) return;
    this.startingPayment.set(true);
    this.errorMessage.set(null);

    // If the order has no payment method yet (e.g. created from quote
    // conversion), set the selected method first, then fetch the payload.
    if (!o.paymentMethod) {
      const method = this.selectedPaymentMethod();
      if (!method) {
        this.startingPayment.set(false);
        this.errorMessage.set('Please select a payment method.');
        return;
      }
      this.orderService.setPaymentMethod(o.uuid, { paymentMethod: method }).subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.fetchPayloadAndRedirect(updated.uuid);
        },
        error: (err: NormalizedApiError) => {
          this.startingPayment.set(false);
          this.errorMessage.set(err.message || 'Could not set payment method.');
        },
      });
      return;
    }

    this.fetchPayloadAndRedirect(o.uuid);
  }

  private fetchPayloadAndRedirect(uuid: string): void {
    this.orderService.paymentPayload(uuid).subscribe({
      next: (payload) => {
        this.startingPayment.set(false);
        if (payload.provider === 'MPGS') {
          this.initMpgsCheckout(payload.gatewayUrl, payload.fields, uuid);
        } else {
          this.autoPostToGateway(payload.gatewayUrl, payload.fields);
        }
      },
      error: (err: NormalizedApiError) => {
        this.startingPayment.set(false);
        if (err.code === 'PAYMENT_GATEWAY_NOT_CONFIGURED') {
          this.errorMessage.set(
            'The selected payment gateway is not configured. Please choose another method or contact support.',
          );
        } else {
          this.errorMessage.set(err.message || 'Could not start payment. Please try again.');
        }
      },
    });
  }

  /**
   * Load the MPGS Checkout.js script and configure the hosted checkout
   * lightbox. Mirrors the checkout page's implementation — the script URL
   * comes from {@code payload.gatewayUrl} and the session config (merchantId,
   * sessionId, successIndicator) from {@code payload.fields}.
   */
  private initMpgsCheckout(checkoutJsUrl: string, fields: Record<string, string>, orderUuid: string): void {
    this.startingPayment.set(true);
    const script = this.doc.createElement('script');
    script.src = checkoutJsUrl;
    script.onload = () => {
      const checkout = (window as any).Checkout;
      if (!checkout) {
        this.startingPayment.set(false);
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
        const successIndicator = fields['successIndicator'];
        if (result && result.resultIndicator && successIndicator
            && result.resultIndicator === successIndicator) {
          this.orderService.confirmMpgsPayment(orderUuid).subscribe({
            next: () => {
              void this.router.navigate(['/checkout/return'], {
                queryParams: { order: orderUuid, provider: 'MPGS' },
              });
            },
            error: () => {
              void this.router.navigate(['/checkout/return'], {
                queryParams: { order: orderUuid, provider: 'MPGS' },
              });
            },
          });
        } else {
          this.startingPayment.set(false);
          this.errorMessage.set('Payment was not completed. Please try again.');
        }
      });
      if (typeof checkout.showLightbox === 'function') {
        checkout.showLightbox();
      }
    };
    script.onerror = () => {
      this.startingPayment.set(false);
      this.errorMessage.set('Could not load the payment page. Please try again.');
    };
    this.doc.head.appendChild(script);
  }

  /**
   * Pull the latest payment status from MPGS. Useful when the webhook is not
   * configured (e.g. local dev) — the customer can click to sync the order
   * status with what MPGS actually recorded.
   */
  protected refreshPayment(): void {
    const o = this.order();
    if (!o) return;
    this.checkingPayment.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.orderService.confirmMpgsPayment(o.uuid).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.checkingPayment.set(false);
        if (updated.status === 'PAID') {
          this.successMessage.set('Payment confirmed — your order is now marked as paid.');
        } else if (updated.status === 'PENDING_PAYMENT') {
          this.errorMessage.set('MPGS still shows this payment as pending. Try again in a moment.');
        } else if (updated.status === 'CANCELLED') {
          this.errorMessage.set('MPGS reports the payment was declined or cancelled.');
        }
      },
      error: (err: NormalizedApiError) => {
        this.checkingPayment.set(false);
        this.errorMessage.set(err.message || 'Could not refresh payment status from MPGS.');
      },
    });
  }

  protected cancelOrder(): void {
    const o = this.order();
    if (!o) return;
    if (!confirm('Cancel this order? Any reserved stock will be released.')) return;
    this.cancelling.set(true);
    this.orderService
      .cancel(o.uuid, { reason: 'Customer cancelled from account page.' })
      .subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.cancelling.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.cancelling.set(false);
          this.errorMessage.set(err.message || 'Could not cancel this order.');
        },
      });
  }

  private autoPostToGateway(gatewayUrl: string, fields: Record<string, string>): void {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = gatewayUrl;
    form.style.display = 'none';
    for (const [key, value] of Object.entries(fields)) {
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = key;
      input.value = value ?? '';
      form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
  }

  protected statusLabel(status: OrderStatus): string {
    return status.replace(/_/g, ' ').toLowerCase();
  }

  /** Template helper — Angular expressions can't call the global {@code Number}. */
  protected asNumber(value: string | number | null | undefined): number {
    if (value == null) return 0;
    const n = typeof value === 'string' ? Number(value) : value;
    return isFinite(n) ? n : 0;
  }

  protected statusClass(status: OrderStatus): string {
    switch (status) {
      case 'PAID':
      case 'FULFILLING':
      case 'SHIPPED':
        return 'is-ok';
      case 'DELIVERED':
        return 'is-done';
      case 'PENDING_PAYMENT':
      case 'DRAFT':
        return 'is-pending';
      case 'CANCELLED':
      case 'REFUNDED':
        return 'is-cancelled';
    }
  }

  protected formatMoney(currency: string, amount: string): string {
    const value = Number(amount);
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
