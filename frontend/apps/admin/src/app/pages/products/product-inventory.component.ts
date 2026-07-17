import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { InventoryService } from '../../core/catalog/inventory.service';
import { InventoryDto } from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * Inventory tab panel for {@code /products/:id}.
 *
 * Offers two workflows:
 *   1. Signed-delta adjustment (e.g. +50 units on restock, −3 on a return).
 *   2. Overwrite quantity + restock threshold (used for stock takes).
 *
 * The panel fetches the live inventory row on init so the admin sees the
 * latest available/reserved counts even if the parent's copy is stale.
 */
@Component({
  selector: 'gi-admin-product-inventory',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-inventory.component.html',
  styleUrl: './product-inventory.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductInventoryComponent implements OnInit, OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(InventoryService);

  @Input({ required: true }) productId!: number;

  protected readonly inventory = signal<InventoryDto | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly adjustForm = this.fb.nonNullable.group({
    delta:  ['', [Validators.required, Validators.pattern(/^-?\d+(?:\.\d{1,3})?$/)]],
    reason: ['', [Validators.maxLength(200)]],
  });

  protected readonly overwriteForm = this.fb.nonNullable.group({
    quantity:         ['', [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    restockThreshold: ['', [Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
  });

  ngOnInit(): void {
    this.reload();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('productId' in changes && !changes['productId'].firstChange) {
      this.reload();
    }
  }

  // ---------------------------------------------------------------------------

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.getForProduct(this.productId).subscribe({
      next: (inv) => {
        this.inventory.set(inv);
        this.loading.set(false);
        this.seedOverwriteFromServer(inv);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load inventory.');
        this.loading.set(false);
      },
    });
  }

  private seedOverwriteFromServer(inv: InventoryDto): void {
    this.overwriteForm.patchValue({
      quantity: inv.quantity,
      restockThreshold: inv.restockThreshold,
    });
  }

  protected submitAdjust(): void {
    if (this.saving()) return;
    if (this.adjustForm.invalid) {
      this.adjustForm.markAllAsTouched();
      return;
    }
    const raw = this.adjustForm.getRawValue();
    this.clearBanners();
    this.saving.set(true);
    this.service
      .adjust(this.productId, {
        delta: raw.delta.trim(),
        reason: raw.reason.trim() || undefined,
      })
      .subscribe({
        next: (inv) => {
          this.saving.set(false);
          this.inventory.set(inv);
          this.seedOverwriteFromServer(inv);
          this.successMessage.set(`Adjusted by ${raw.delta}.`);
          this.adjustForm.reset({ delta: '', reason: '' });
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not apply that adjustment.');
        },
      });
  }

  protected submitOverwrite(): void {
    if (this.saving()) return;
    if (this.overwriteForm.invalid) {
      this.overwriteForm.markAllAsTouched();
      return;
    }
    const raw = this.overwriteForm.getRawValue();
    if (!confirm(`Overwrite on-hand quantity to ${raw.quantity}? This affects live sellable stock.`)) {
      return;
    }
    this.clearBanners();
    this.saving.set(true);
    this.service
      .set(this.productId, {
        quantity: raw.quantity.trim(),
        restockThreshold: raw.restockThreshold.trim() || undefined,
      })
      .subscribe({
        next: (inv) => {
          this.saving.set(false);
          this.inventory.set(inv);
          this.seedOverwriteFromServer(inv);
          this.successMessage.set('Inventory updated.');
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not overwrite inventory.');
        },
      });
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
