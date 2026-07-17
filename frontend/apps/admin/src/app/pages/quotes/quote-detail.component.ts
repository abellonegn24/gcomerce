import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AdminQuotesService } from '../../core/quotes/quotes.service';
import {
  QuoteDetail,
  QuoteLine,
  QuoteStatus,
  quoteStatusVariant,
} from '../../core/quotes/quote.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-quote-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './quote-detail.component.html',
  styleUrl: './quote-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuoteDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(AdminQuotesService);

  protected readonly quote = signal<QuoteDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly actionLoading = signal<boolean>(false);

  // Line editing
  protected readonly editingLineId = signal<number | null>(null);
  protected readonly editPrice = signal<string>('');

  // Send form
  protected readonly sendAdminNotes = signal<string>('');
  protected readonly sendValidDays = signal<number>(30);
  protected readonly sendShipping = signal<string>('');
  protected readonly sendTax = signal<string>('');

  // Reject form
  protected readonly rejectNotes = signal<string>('');

  // Convert form
  protected readonly convertAddressId = signal<string>('');
  protected readonly convertShippingMethodId = signal<string>('');

  protected readonly canReview = computed<boolean>(() => this.quote()?.status === 'SUBMITTED');
  protected readonly canEdit = computed<boolean>(() =>
    this.quote()?.status === 'UNDER_REVIEW' || this.quote()?.status === 'SUBMITTED',
  );
  protected readonly canSend = computed<boolean>(() =>
    this.quote()?.status === 'UNDER_REVIEW' || this.quote()?.status === 'SUBMITTED',
  );
  protected readonly canReject = computed<boolean>(() =>
    this.quote()?.status === 'SUBMITTED' || this.quote()?.status === 'UNDER_REVIEW',
  );
  protected readonly canConvert = computed<boolean>(() => this.quote()?.status === 'ACCEPTED');

  ngOnInit(): void {
    const uuid = this.route.snapshot.paramMap.get('uuid');
    if (!uuid) {
      this.errorMessage.set('Missing quote id in the URL.');
      this.loading.set(false);
      return;
    }
    this.reload(uuid);
  }

  private reload(uuid: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.get(uuid).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load this quote.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  protected startReview(): void {
    const q = this.quote();
    if (!q) return;
    this.actionLoading.set(true);
    this.clearBanners();
    this.service.startReview(q.uuid).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.actionLoading.set(false);
        this.successMessage.set('Quote is now under review.');
      },
      error: (err: NormalizedApiError) => {
        this.actionLoading.set(false);
        this.errorMessage.set(err.message || 'Could not start review.');
      },
    });
  }

  protected beginEditLine(line: QuoteLine): void {
    this.editingLineId.set(line.id);
    this.editPrice.set(line.proposedUnitPrice);
  }

  protected cancelEditLine(): void {
    this.editingLineId.set(null);
    this.editPrice.set('');
  }

  protected saveLine(line: QuoteLine): void {
    const q = this.quote();
    if (!q) return;
    const price = this.editPrice().trim();
    const num = Number(price);
    if (!price || !isFinite(num) || num < 0) {
      this.errorMessage.set('Enter a valid non-negative price.');
      return;
    }
    this.clearBanners();
    this.service.updateLine(q.uuid, line.id, { proposedUnitPrice: price }).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.editingLineId.set(null);
        this.editPrice.set('');
        this.successMessage.set('Line price updated.');
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not update line price.');
      },
    });
  }

  protected sendQuote(): void {
    const q = this.quote();
    if (!q) return;
    this.actionLoading.set(true);
    this.clearBanners();
    this.service.send(q.uuid, {
      adminNotes: this.sendAdminNotes().trim() || undefined,
      validForDays: this.sendValidDays(),
      shippingTotal: this.sendShipping().trim() || undefined,
      taxTotal: this.sendTax().trim() || undefined,
    }).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.actionLoading.set(false);
        this.successMessage.set('Quote sent to customer.');
        this.sendAdminNotes.set('');
        this.sendShipping.set('');
        this.sendTax.set('');
      },
      error: (err: NormalizedApiError) => {
        this.actionLoading.set(false);
        this.errorMessage.set(err.message || 'Could not send quote.');
      },
    });
  }

  protected rejectQuote(): void {
    const q = this.quote();
    if (!q) return;
    this.actionLoading.set(true);
    this.clearBanners();
    this.service.reject(q.uuid, {
      adminNotes: this.rejectNotes().trim() || undefined,
    }).subscribe({
      next: (updated) => {
        this.quote.set(updated);
        this.actionLoading.set(false);
        this.successMessage.set('Quote rejected.');
        this.rejectNotes.set('');
      },
      error: (err: NormalizedApiError) => {
        this.actionLoading.set(false);
        this.errorMessage.set(err.message || 'Could not reject quote.');
      },
    });
  }

  protected convertQuote(): void {
    const q = this.quote();
    if (!q) return;
    const addrId = Number(this.convertAddressId());
    if (!isFinite(addrId) || addrId <= 0) {
      this.errorMessage.set('Please select a shipping address.');
      return;
    }
    this.actionLoading.set(true);
    this.clearBanners();
    const shippingId = this.convertShippingMethodId().trim();
    this.service.convert(q.uuid, {
      shipToAddressId: addrId,
      shippingMethodId: shippingId ? Number(shippingId) : undefined,
    }).subscribe({
      next: (result) => {
        this.actionLoading.set(false);
        this.successMessage.set(
          `Quote converted to order ${result.invoiceNumber}.`,
        );
        void this.router.navigate(['/orders', result.orderUuid]);
      },
      error: (err: NormalizedApiError) => {
        this.actionLoading.set(false);
        this.errorMessage.set(err.message || 'Could not convert quote to order.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  protected statusVariant(s: QuoteStatus): string {
    return quoteStatusVariant(s);
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

  protected asNumber(value: string | number | null | undefined): number {
    if (value === null || value === undefined) return 0;
    const n = typeof value === 'string' ? Number(value) : value;
    return isFinite(n) ? n : 0;
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
