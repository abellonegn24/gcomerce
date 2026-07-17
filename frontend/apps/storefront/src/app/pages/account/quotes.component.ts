import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { QuoteService } from '../../core/quotes/quote.service';
import { QuoteStatus, QuoteSummary } from '../../core/quotes/quote.model';

/**
 * /account/quotes — list of the signed-in customer's quote requests.
 */
@Component({
  selector: 'gi-account-quotes',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './quotes.component.html',
  styleUrl: './quotes.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountQuotesComponent implements OnInit {
  private readonly quoteService = inject(QuoteService);

  protected readonly quotes = signal<QuoteSummary[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly page = signal<number>(0);
  protected readonly size = signal<number>(20);
  protected readonly total = signal<number>(0);

  protected readonly hasMore = computed<boolean>(() => {
    return (this.page() + 1) * this.size() < this.total();
  });

  protected readonly isEmpty = computed<boolean>(() => {
    return !this.loading() && this.quotes().length === 0 && !this.errorMessage();
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
    this.quoteService.list(pageIdx, this.size()).subscribe({
      next: (res) => {
        this.quotes.set(pageIdx === 0 ? res.items : [...this.quotes(), ...res.items]);
        this.page.set(res.page);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load your quotes.');
        this.loading.set(false);
      },
    });
  }

  protected statusClass(status: QuoteStatus): string {
    switch (status) {
      case 'SUBMITTED':
      case 'UNDER_REVIEW':
        return 'is-pending';
      case 'SENT':
        return 'is-info';
      case 'ACCEPTED':
        return 'is-ok';
      case 'CONVERTED':
        return 'is-done';
      case 'REJECTED':
      case 'EXPIRED':
        return 'is-cancelled';
      default:
        return '';
    }
  }

  protected statusLabel(status: QuoteStatus): string {
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
