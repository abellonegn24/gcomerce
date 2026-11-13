import { ChangeDetectionStrategy, Component, DOCUMENT, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, timer } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';

import { CartService } from '../../core/cart/cart.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderDetail } from '../../core/orders/order.model';
import { OrderService } from '../../core/orders/order.service';

/**
 * /checkout/return?order=<uuid> — landing page after CyberSource redirects
 * the browser back to us on success.
 *
 * The gateway's server-to-server webhook may have flipped the order to PAID
 * by the time we arrive, but not always — network latency, review holds,
 * etc. We poll {@code GET /me/orders/{uuid}} every 3s (up to 30s total) so
 * the customer sees the status flip live without having to reload.
 */
@Component({
  selector: 'gi-checkout-return',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './checkout-return.component.html',
  styleUrl: './checkout-return.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutReturnComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  private readonly cartService = inject(CartService);
  private readonly doc = inject(DOCUMENT);

  private pollSub: Subscription | null = null;

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly downloadingInvoice = signal<boolean>(false);
  protected readonly stillPending = computed<boolean>(() => {
    const o = this.order();
    return o?.status === 'PENDING_PAYMENT';
  });

  ngOnInit(): void {
    const orderUuid = this.route.snapshot.queryParamMap.get('order');
    const provider = this.route.snapshot.queryParamMap.get('provider');
    if (!orderUuid) {
      this.errorMessage.set('This page needs an order reference to proceed.');
      this.loading.set(false);
      return;
    }

    // Cart is emptied server-side at placement; make sure the local snapshot
    // reflects that if the customer bounced back before the effect fired.
    this.cartService.reset();

    // For MPGS, proactively confirm the payment by pulling the order status
    // from the gateway. This handles the case where the MPGS webhook hasn't
    // been configured — the checkout component already calls this once, but
    // we call it again here in case the customer navigated directly.
    if (provider === 'MPGS') {
      this.orderService.confirmMpgsPayment(orderUuid).subscribe({
        next: (order) => {
          this.order.set(order);
          this.loading.set(false);
          // If still pending after confirmation, start polling.
          if (order.status === 'PENDING_PAYMENT') {
            this.startPolling(orderUuid);
          }
        },
        error: () => {
          // Confirmation failed — fall back to polling the order status.
          this.startPolling(orderUuid);
        },
      });
    } else {
      this.startPolling(orderUuid);
    }
  }

  private startPolling(orderUuid: string): void {
    // Initial fetch + then poll up to 10 times (30s total) while still pending.
    let attempts = 0;
    this.pollSub = timer(0, 3000)
      .pipe(
        switchMap(() => this.orderService.get(orderUuid)),
        takeWhile(
          (order) => {
            attempts++;
            this.order.set(order);
            this.loading.set(false);
            return order.status === 'PENDING_PAYMENT' && attempts < 10;
          },
          true, // emit the final value that fails the predicate
        ),
      )
      .subscribe({
        error: (err: NormalizedApiError) => {
          this.errorMessage.set(err.message || 'Could not load your order.');
          this.loading.set(false);
        },
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  /**
   * Gateway label for the pending-payment copy — "MPGS", "CyberSource", or a
   * generic fallback so we never name the wrong provider.
   */
  protected providerLabel(): string {
    const o = this.order();
    const raw = o?.paymentProvider ?? o?.paymentMethod;
    if (raw === 'MPGS') return 'MPGS';
    if (raw === 'CYBERSOURCE') return 'CyberSource';
    return 'the payment gateway';
  }

  /** Download the server-generated invoice PDF for the paid order. */
  protected downloadInvoice(): void {
    const o = this.order();
    if (!o || !o.paidAt) return;
    this.downloadingInvoice.set(true);
    this.orderService.downloadInvoice(o.uuid).subscribe({
      next: (blob) => {
        this.downloadingInvoice.set(false);
        const url = URL.createObjectURL(blob);
        const a = this.doc.createElement('a');
        a.href = url;
        a.download = `invoice-${o.invoiceNumber}.pdf`;
        this.doc.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.downloadingInvoice.set(false);
        this.errorMessage.set('Could not download the invoice. Please try again.');
      },
    });
  }

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
