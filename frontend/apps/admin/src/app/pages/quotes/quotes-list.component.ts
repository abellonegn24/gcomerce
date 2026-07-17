import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminQuotesService } from '../../core/quotes/quotes.service';
import {
  QUOTE_STATUS_OPTIONS,
  QuoteStatus,
  QuoteSummary,
  quoteStatusVariant,
} from '../../core/quotes/quote.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

const PAGE_SIZE = 20;

const STATUS_FILTERS: Array<{ code: QuoteStatus | null; label: string }> = [
  { code: null, label: 'All statuses' },
  ...QUOTE_STATUS_OPTIONS,
];

@Component({
  selector: 'gi-admin-quotes-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './quotes-list.component.html',
  styleUrl: './quotes-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotesListComponent implements OnInit {
  private readonly service = inject(AdminQuotesService);

  protected readonly statuses = STATUS_FILTERS;

  protected readonly items = signal<QuoteSummary[]>([]);
  protected readonly page = signal<number>(0);
  protected readonly total = signal<number>(0);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly status = signal<QuoteStatus | null>(null);

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
          this.errorMessage.set(err.message || 'Could not load quotes.');
          this.loading.set(false);
        },
      });
  }

  protected applyStatus(s: QuoteStatus | null): void {
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

  protected statusVariant(s: QuoteStatus): string {
    return quoteStatusVariant(s);
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
