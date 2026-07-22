import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { OrderService } from '../../core/orders/order.service';
import { OrderStatus, TransactionSummary } from '../../core/orders/order.model';

@Component({
  selector: 'gi-account-transactions',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountTransactionsComponent implements OnInit {
  private readonly orderService = inject(OrderService);

  protected readonly transactions = signal<TransactionSummary[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly page = signal<number>(0);
  protected readonly size = signal<number>(20);
  protected readonly total = signal<number>(0);

  protected readonly hasMore = computed<boolean>(() => {
    return (this.page() + 1) * this.size() < this.total();
  });

  protected readonly isEmpty = computed<boolean>(() => {
    return !this.loading() && this.transactions().length === 0 && !this.errorMessage();
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
    this.orderService.listAllTransactions(pageIdx, this.size()).subscribe({
      next: (res) => {
        this.transactions.set(pageIdx === 0 ? res.items : [...this.transactions(), ...res.items]);
        this.page.set(res.page);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load transactions.');
        this.loading.set(false);
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
