import { ChangeDetectionStrategy, Component, DOCUMENT, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderService } from '../../core/orders/order.service';
import { OrderStatus, OrderSummary } from '../../core/orders/order.model';

/**
 * /account/orders — real order history driven by GET /me/orders.
 */
@Component({
  selector: 'gi-account-orders',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountOrdersComponent implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly doc = inject(DOCUMENT);

  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  /** UUID of the order whose invoice is currently downloading, if any. */
  protected readonly downloadingInvoiceId = signal<string | null>(null);
  protected readonly page = signal<number>(0);
  protected readonly size = signal<number>(20);
  protected readonly total = signal<number>(0);

  protected readonly hasMore = computed<boolean>(() => {
    return (this.page() + 1) * this.size() < this.total();
  });

  protected readonly isEmpty = computed<boolean>(() => {
    return !this.loading() && this.orders().length === 0 && !this.errorMessage();
  });

  ngOnInit(): void {
    this.load(0);
  }

  protected loadNextPage(): void {
    if (!this.hasMore()) return;
    this.load(this.page() + 1);
  }

  private load(pageIdx: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.list(pageIdx, this.size()).subscribe({
      next: (res) => {
        // Append when paging forward, replace on first page.
        this.orders.set(pageIdx === 0 ? res.items : [...this.orders(), ...res.items]);
        this.page.set(res.page);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load your orders.');
        this.loading.set(false);
      },
    });
  }

  /**
   * Download the invoice PDF straight from the list — no need to open the
   * order first. The row itself is a router link, so the click must not
   * bubble into a navigation.
   */
  protected downloadInvoice(o: OrderSummary, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (!o.paidAt || this.downloadingInvoiceId()) return;
    this.downloadingInvoiceId.set(o.uuid);
    this.errorMessage.set(null);
    this.orderService.downloadInvoice(o.uuid).subscribe({
      next: (blob) => {
        this.downloadingInvoiceId.set(null);
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
        this.downloadingInvoiceId.set(null);
        this.errorMessage.set('Could not download the invoice. Please try again.');
      },
    });
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

  protected statusLabel(status: OrderStatus): string {
    return status.replace(/_/g, ' ').toLowerCase();
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
