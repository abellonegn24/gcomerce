import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CustomersService } from '../../core/customers/customers.service';
import {
  CUSTOMER_STATUS_LABELS,
  CUSTOMER_STATUS_OPTIONS,
  CUSTOMER_TYPE_OPTIONS,
  CustomerRow,
  CustomerStatus,
  CustomerType,
  customerStatusVariant,
} from '../../core/customers/customer.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

const PAGE_SIZE = 20;

const STATUS_FILTERS: Array<{ code: CustomerStatus | null; label: string }> = [
  { code: null, label: 'Any status' },
  ...CUSTOMER_STATUS_OPTIONS,
];

const TYPE_FILTERS: Array<{ code: CustomerType | null; label: string }> = [
  { code: null, label: 'B2C + B2B' },
  ...CUSTOMER_TYPE_OPTIONS,
];

/**
 * /customers — admin customer roster.
 * Paged 20/row, filtered by free text (email / name / company), type, and
 * status. Rows are routerLinks to the customer detail screen.
 */
@Component({
  selector: 'gi-admin-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './customers-list.component.html',
  styleUrl: './customers-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomersListComponent implements OnInit {
  private readonly service = inject(CustomersService);

  protected readonly statusFilters = STATUS_FILTERS;
  protected readonly typeFilters = TYPE_FILTERS;
  protected readonly statusLabels = CUSTOMER_STATUS_LABELS;
  protected readonly statusVariant = customerStatusVariant;

  protected readonly items = signal<CustomerRow[]>([]);
  protected readonly total = signal<number>(0);
  protected readonly page = signal<number>(0);
  protected readonly hasNext = signal<boolean>(false);
  protected readonly hasPrev = signal<boolean>(false);

  protected readonly q = signal<string>('');
  protected readonly type = signal<CustomerType | null>(null);
  protected readonly status = signal<CustomerStatus | null>(null);

  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly pageCount = computed<number>(() =>
    Math.max(1, Math.ceil(this.total() / PAGE_SIZE)),
  );

  ngOnInit(): void {
    this.reload();
  }

  // ---------------------------------------------------------------------------

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .list({
        q: this.q().trim() || undefined,
        type: this.type(),
        status: this.status(),
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (res) => {
          this.items.set(res.items);
          this.total.set(res.page.total);
          this.hasNext.set(res.page.hasNext);
          this.hasPrev.set(res.page.hasPrev);
          this.loading.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.errorMessage.set(err.message || 'Could not load customers.');
          this.loading.set(false);
        },
      });
  }

  protected onSearchInput(v: string): void {
    this.q.set(v);
  }

  protected submitSearch(): void {
    this.page.set(0);
    this.reload();
  }

  protected onTypeChange(v: CustomerType | null): void {
    this.type.set(v);
    this.page.set(0);
    this.reload();
  }

  protected onStatusChange(v: CustomerStatus | null): void {
    this.status.set(v);
    this.page.set(0);
    this.reload();
  }

  protected clearFilters(): void {
    this.q.set('');
    this.type.set(null);
    this.status.set(null);
    this.page.set(0);
    this.reload();
  }

  protected nextPage(): void {
    if (!this.hasNext()) return;
    this.page.update((p) => p + 1);
    this.reload();
  }

  protected prevPage(): void {
    if (!this.hasPrev()) return;
    this.page.update((p) => Math.max(0, p - 1));
    this.reload();
  }
}
