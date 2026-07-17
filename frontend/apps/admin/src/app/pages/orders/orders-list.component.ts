import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { OrdersService } from '../../core/orders/orders.service';
import {
  ORDER_STATUS_OPTIONS,
  OrderRow,
  OrderStatus,
  orderStatusVariant,
} from '../../core/orders/order.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

const PAGE_SIZE = 20;

const STATUS_FILTERS: Array<{ code: OrderStatus | null; label: string }> = [
  { code: null,              label: 'All statuses' },
  ...ORDER_STATUS_OPTIONS,
];

/**
 * /orders — admin operator's view of every placed order.
 * Filters by status + free-text search (invoice number / customer email /
 * company name) and paginates in 20-row chunks.
 */
@Component({
  selector: 'gi-admin-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './orders-list.component.html',
  styleUrl: './orders-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersListComponent implements OnInit {
  private readonly service = inject(OrdersService);

  protected readonly statuses = STATUS_FILTERS;

  protected readonly items = signal<OrderRow[]>([]);
  protected readonly page = signal<number>(0);
  protected readonly total = signal<number>(0);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly q = signal<string>('');
  protected readonly status = signal<OrderStatus | null>(null);

  protected readonly pageCount = computed<number>(() =>
    Math.max(1, Math.ceil(this.total() / PAGE_SIZE)),
  );
  protected readonly hasNext = computed<boolean>(() => this.page() + 1 < this.pageCount());
  protected readonly hasPrev = computed<boolean>(() => this.page() > 0);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .list({
        q: this.q().trim() || undefined,
        status: this.status(),
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (res) => {
          this.items.set(res.items);
          this.total.set(res.total);
          this.loading.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.errorMessage.set(err.message || 'Could not load orders.');
          this.loading.set(false);
        },
      });
  }

  protected submitSearch(): void {
    this.page.set(0);
    this.load();
  }

  protected clearSearch(): void {
    this.q.set('');
    this.page.set(0);
    this.load();
  }

  protected applyStatus(s: OrderStatus | null): void {
    this.status.set(s);
    this.page.set(0);
    this.load();
  }

  protected nextPage(): void {
    if (!this.hasNext()) return;
    this.page.update((p) => p + 1);
    this.load();
  }

  protected prevPage(): void {
    if (!this.hasPrev()) return;
    this.page.update((p) => Math.max(0, p - 1));
    this.load();
  }

  protected statusVariant(s: OrderStatus): string {
    return orderStatusVariant(s);
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
