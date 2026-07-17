import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderDetail } from '../../core/orders/order.model';
import { OrderService } from '../../core/orders/order.service';

/**
 * /checkout/cancel?order=<uuid> — landing page when the customer explicitly
 * aborts the CyberSource hosted checkout. The order is still in
 * PENDING_PAYMENT server-side unless the webhook has already run — the
 * customer may choose to retry payment or cancel the order outright.
 */
@Component({
  selector: 'gi-checkout-cancel',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './checkout-cancel.component.html',
  styleUrl: './checkout-return.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutCancelComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly cancelling = signal<boolean>(false);

  ngOnInit(): void {
    const orderUuid = this.route.snapshot.queryParamMap.get('order');
    if (!orderUuid) {
      this.loading.set(false);
      return;
    }
    this.orderService.get(orderUuid).subscribe({
      next: (o) => {
        this.order.set(o);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.loading.set(false);
        this.errorMessage.set(err.message || 'Could not load your order.');
      },
    });
  }

  protected retryPayment(): void {
    const o = this.order();
    if (!o) return;
    this.cancelling.set(false);
    this.orderService.paymentPayload(o.uuid).subscribe({
      next: (payload) => this.autoPostToGateway(payload.gatewayUrl, payload.fields),
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not restart payment. Please try again.');
      },
    });
  }

  protected cancelOrder(): void {
    const o = this.order();
    if (!o) return;
    if (!confirm('Cancel this order? Any reserved stock will be released.')) return;
    this.cancelling.set(true);
    this.orderService.cancel(o.uuid, { reason: 'Customer cancelled from checkout return page.' }).subscribe({
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
}
