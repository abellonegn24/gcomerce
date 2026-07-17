import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderDetail, OrderStatus } from '../../core/orders/order.model';
import { OrderService } from '../../core/orders/order.service';

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

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly cancelling = signal<boolean>(false);
  protected readonly startingPayment = signal<boolean>(false);

  /**
   * If the customer arrived from the checkout page's payment-not-configured
   * fallback we flag it so the UI hints at what to do next.
   */
  protected readonly gatewayMissing = signal<boolean>(false);

  protected readonly canCancel = computed<boolean>(
    () => this.order()?.status === 'PENDING_PAYMENT',
  );

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

  protected retryPayment(): void {
    const o = this.order();
    if (!o) return;
    this.startingPayment.set(true);
    this.errorMessage.set(null);
    this.orderService.paymentPayload(o.uuid).subscribe({
      next: (payload) => this.autoPostToGateway(payload.gatewayUrl, payload.fields),
      error: (err: NormalizedApiError) => {
        this.startingPayment.set(false);
        this.errorMessage.set(err.message || 'Could not restart payment.');
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
