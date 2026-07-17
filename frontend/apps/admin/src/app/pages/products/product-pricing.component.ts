import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { PriceListsService } from '../../core/catalog/price-lists.service';
import {
  PriceListDetail,
  PriceListDto,
  PriceListEntryDto,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * Pricing tab panel for {@code /products/:id}.
 *
 * Groups every price-list entry that references this product across all
 * price lists. Each row is (price list × min-quantity → price). Upsert is
 * keyed by (priceListId, productId, minQuantity), so re-submitting an
 * existing min-quantity for the same list updates its price in place.
 */
interface RowsByList {
  priceList: PriceListDto;
  entries: PriceListEntryDto[];
}

@Component({
  selector: 'gi-admin-product-pricing',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-pricing.component.html',
  styleUrl: './product-pricing.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPricingComponent implements OnInit, OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PriceListsService);

  @Input({ required: true }) productId!: number;

  protected readonly priceLists = signal<PriceListDto[]>([]);
  protected readonly rows = signal<RowsByList[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    priceListId: this.fb.nonNullable.control<number | null>(null, [Validators.required]),
    minQuantity: ['1',    [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    price:       ['0.00', [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,4})?$/)]],
  });

  protected readonly hasAnyList = computed<boolean>(() => this.priceLists().length > 0);

  ngOnInit(): void { this.reload(); }

  ngOnChanges(changes: SimpleChanges): void {
    if ('productId' in changes && !changes['productId'].firstChange) this.reload();
  }

  // ---------------------------------------------------------------------------

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list().subscribe({
      next: (lists) => {
        this.priceLists.set(lists);
        if (lists.length === 0) {
          this.rows.set([]);
          this.loading.set(false);
          return;
        }
        // Fetch every list in parallel and filter each one down to entries
        // that reference this product.
        forkJoin(lists.map((pl) => this.service.get(pl.id))).subscribe({
          next: (details) => {
            const rows: RowsByList[] = details
              .map((d: PriceListDetail) => ({
                priceList: d.priceList,
                entries: d.entries
                  .filter((e) => e.productId === this.productId)
                  .sort((a, b) => Number(a.minQuantity) - Number(b.minQuantity)),
              }))
              .filter((r) => r.entries.length > 0);
            this.rows.set(rows);
            this.loading.set(false);
            // Default the dropdown to the first list.
            if (this.form.controls.priceListId.value === null) {
              this.form.controls.priceListId.setValue(lists[0].id);
            }
          },
          error: (err: NormalizedApiError) => {
            this.errorMessage.set(err.message || 'Could not load price list entries.');
            this.loading.set(false);
          },
        });
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load price lists.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------

  protected submitEntry(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    if (raw.priceListId === null) return;

    this.clearBanners();
    this.saving.set(true);
    this.service
      .upsertEntry(raw.priceListId, {
        productId: this.productId,
        price: raw.price.trim(),
        minQuantity: raw.minQuantity.trim(),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.successMessage.set('Price tier saved.');
          this.form.patchValue({ minQuantity: '1', price: '0.00' });
          this.reload();
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not save that price tier.');
        },
      });
  }

  protected removeEntry(row: RowsByList, entry: PriceListEntryDto): void {
    if (this.saving()) return;
    if (!confirm(`Remove tier ${entry.minQuantity}+ @ ${entry.price} from ${row.priceList.name}?`)) return;

    this.clearBanners();
    this.saving.set(true);
    this.service.deleteEntry(row.priceList.id, entry.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Price tier removed.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not remove that price tier.');
      },
    });
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  protected formatPrice(currency: string, amount: string): string {
    const value = Number(amount);
    if (!isFinite(value)) return `${currency} ${amount}`;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 4,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }
}
