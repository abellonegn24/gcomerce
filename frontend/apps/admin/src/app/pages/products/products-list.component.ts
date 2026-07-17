import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ProductsService } from '../../core/catalog/products.service';
import {
  CATEGORY_LABELS,
  ProductCategoryCode,
  ProductListItem,
  ProductStatus,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

const PAGE_SIZE = 20;

/** Category filter chips shown above the table. `null` = all. */
const CATEGORY_FILTERS: Array<{ code: ProductCategoryCode | null; label: string }> = [
  { code: null,                label: 'All' },
  { code: 'RAW_COFFEE',        label: 'Raw Coffee' },
  { code: 'PROCESSED_COFFEE',  label: 'Processed' },
  { code: 'WHITE_PRODUCTS',    label: 'White' },
  { code: 'SPICY_PRODUCTS',    label: 'Spicy' },
];

const STATUS_FILTERS: Array<{ code: ProductStatus | null; label: string }> = [
  { code: null,       label: 'Any status' },
  { code: 'DRAFT',    label: 'Draft' },
  { code: 'ACTIVE',   label: 'Active' },
  { code: 'ARCHIVED', label: 'Archived' },
];

@Component({
  selector: 'gi-admin-products',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './products-list.component.html',
  styleUrl: './products-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductsListComponent implements OnInit {
  private readonly service = inject(ProductsService);

  protected readonly categories = CATEGORY_FILTERS;
  protected readonly statuses = STATUS_FILTERS;
  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly items = signal<ProductListItem[]>([]);
  protected readonly page = signal<number>(0);
  protected readonly total = signal<number>(0);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly q = signal<string>('');
  protected readonly status = signal<ProductStatus | null>(null);
  protected readonly category = signal<ProductCategoryCode | null>(null);

  protected readonly pageCount = computed<number>(() =>
    Math.max(1, Math.ceil(this.total() / PAGE_SIZE))
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
        category: this.category(),
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (res) => {
          this.items.set(res.page.items);
          this.total.set(res.page.total);
          this.loading.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.errorMessage.set(err.message || 'Could not load products.');
          this.loading.set(false);
        },
      });
  }

  protected applyFilter(cat: ProductCategoryCode | null): void {
    this.category.set(cat);
    this.page.set(0);
    this.load();
  }

  protected applyStatus(s: ProductStatus | null): void {
    this.status.set(s);
    this.page.set(0);
    this.load();
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

  protected formatPrice(currency: string, amount: string): string {
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
