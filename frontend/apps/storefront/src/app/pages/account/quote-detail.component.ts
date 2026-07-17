import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { QuoteDetail, QuoteStatus } from '../../core/quotes/quote.model';
import { QuoteService } from '../../core/quotes/quote.service';

/**
 * /account/quotes/:uuid — detail view for a single quote request.
 * Shows line items, proposed pricing, and an "Accept quote" button when the
 * quote is in SENT status.
 */
@Component({
  selector: 'gi-account-quote-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './quote-detail.component.html',
  styleUrl: './quote-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountQuoteDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly quoteService = inject(QuoteService);

  protected readonly quote = signal<QuoteDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly accepting = signal<boolean>(false);

  protected readonly canAccept = computed<boolean>(
    () => this.quote()?.status === 'SENT',
  );

  protected readonly statusLabel = computed<string>(() => {
    const s = this.quote()?.status;
    if (!s) return '';
    return s.replace(/_/g, ' ').toLowerCase();
  });

  ngOnInit(): void {
    const uuid = this.route.snapshot.paramMap.get('uuid');
    if (!uuid) {
      this.errorMessage.set('Missing quote reference.');
      this.loading.set(false);
      return;
    }
    this.load(uuid);
  }

  protected acceptQuote(): void {
    const q = this.quote();
    if (!q) return;
    this.accepting.set(true);
    this.errorMessage.set(null);
    this.quoteService.accept(q.uuid).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.accepting.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.accepting.set(false);
        this.errorMessage.set(err.message || 'Could not accept this quote.');
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

  protected toNumber(value: string | number): number {
    return typeof value === 'string' ? Number(value) : value;
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

  private load(uuid: string): void {
    this.loading.set(true);
    this.quoteService.get(uuid).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.loading.set(false);
        if (err.status === 404) {
          void this.router.navigate(['/account/quotes']);
          return;
        }
        this.errorMessage.set(err.message || 'Could not load this quote.');
      },
    });
  }
}
