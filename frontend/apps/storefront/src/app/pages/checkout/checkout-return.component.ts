import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
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

  private pollSub: Subscription | null = null;

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly stillPending = computed<boolean>(() => {
    const o = this.order();
    return o?.status === 'PENDING_PAYMENT';
  });

  ngOnInit(): void {
    const orderUuid = this.route.snapshot.queryParamMap.get('order');
    if (!orderUuid) {
      this.errorMessage.set('This page needs an order reference to proceed.');
      this.loading.set(false);
      return;
    }

    // Cart is emptied server-side at placement; make sure the local snapshot
    // reflects that if the customer bounced back before the effect fired.
    this.cartService.reset();

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
