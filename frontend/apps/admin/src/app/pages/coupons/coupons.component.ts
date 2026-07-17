import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CouponDto,
  CouponDiscountType,
  CreateCouponPayload,
  UpdateCouponPayload,
} from '../../core/catalog/catalog.model';
import { CouponsService } from '../../core/catalog/coupons.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-coupons',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './coupons.component.html',
  styleUrl: './coupons.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CouponsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CouponsService);

  protected readonly coupons = signal<CouponDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  protected readonly editingId = signal<number | null>(null);
  private editingOriginal: CouponDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code:           ['', [Validators.required, Validators.pattern(/^[A-Z0-9_-]+$/), Validators.maxLength(64)]],
    description:    [''],
    discountType:   ['PERCENT' as CouponDiscountType, Validators.required],
    discountValue:  ['', [Validators.required, Validators.min(0)]],
    currency:       ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    minSubtotal:    [''],
    freeShipping:   this.fb.nonNullable.control<boolean>(false),
    maxRedemptions: this.fb.nonNullable.control<number | null>(null),
    maxPerCustomer: this.fb.nonNullable.control<number | null>(null),
    active:         this.fb.nonNullable.control<boolean>(true),
    startsAt:       [''],
    expiresAt:      [''],
  });

  protected readonly editForm = this.fb.nonNullable.group({
    code:           ['', [Validators.required, Validators.pattern(/^[A-Z0-9_-]+$/), Validators.maxLength(64)]],
    description:    [''],
    discountType:   ['PERCENT' as CouponDiscountType, Validators.required],
    discountValue:  ['', [Validators.required, Validators.min(0)]],
    currency:       ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    minSubtotal:    [''],
    freeShipping:   this.fb.nonNullable.control<boolean>(false),
    maxRedemptions: this.fb.nonNullable.control<number | null>(null),
    maxPerCustomer: this.fb.nonNullable.control<number | null>(null),
    active:         this.fb.nonNullable.control<boolean>(true),
    startsAt:       [''],
    expiresAt:      [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list(0, 100).subscribe({
      next: (res) => {
        this.coupons.set(res.items);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load coupons.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Create
  // ---------------------------------------------------------------------------

  protected toggleCreate(): void {
    this.showCreate.set(!this.showCreate());
    this.editingId.set(null);
    if (this.showCreate()) {
      this.form.reset({
        code: '', description: '', discountType: 'PERCENT', discountValue: '',
        currency: 'USD', minSubtotal: '', freeShipping: false,
        maxRedemptions: null, maxPerCustomer: null, active: true,
        startsAt: '', expiresAt: '',
      });
    }
  }

  protected submitCreate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const payload: CreateCouponPayload = {
      code: v.code,
      description: v.description || undefined,
      discountType: v.discountType,
      discountValue: v.discountValue,
      currency: v.currency,
      minSubtotal: v.minSubtotal || undefined,
      freeShipping: v.freeShipping,
      maxRedemptions: v.maxRedemptions ?? undefined,
      maxPerCustomer: v.maxPerCustomer ?? undefined,
      active: v.active,
      startsAt: v.startsAt || undefined,
      expiresAt: v.expiresAt || undefined,
    };

    this.service.create(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.showCreate.set(false);
        this.successMessage.set('Coupon created.');
        this.load();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create coupon.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Edit
  // ---------------------------------------------------------------------------

  protected startEdit(c: CouponDto): void {
    this.editingId.set(c.id);
    this.editingOriginal = c;
    this.showCreate.set(false);
    this.editForm.reset({
      code: c.code,
      description: c.description ?? '',
      discountType: c.discountType,
      discountValue: c.discountValue,
      currency: c.currency,
      minSubtotal: c.minSubtotal ?? '',
      freeShipping: c.freeShipping,
      maxRedemptions: c.maxRedemptions,
      maxPerCustomer: c.maxPerCustomer,
      active: c.active,
      startsAt: c.startsAt ?? '',
      expiresAt: c.expiresAt ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.editingOriginal = null;
  }

  protected submitEdit(): void {
    if (this.editForm.invalid || this.editingOriginal == null) {
      this.editForm.markAllAsTouched();
      return;
    }
    const id = this.editingId()!;
    const v = this.editForm.getRawValue();
    const orig = this.editingOriginal;

    const payload: UpdateCouponPayload = {};
    if (v.code !== orig.code) payload.code = v.code;
    if (v.description !== (orig.description ?? '')) payload.description = v.description || undefined;
    if (v.discountType !== orig.discountType) payload.discountType = v.discountType;
    if (v.discountValue !== orig.discountValue) payload.discountValue = v.discountValue;
    if (v.currency !== orig.currency) payload.currency = v.currency;
    if (v.minSubtotal !== (orig.minSubtotal ?? '')) payload.minSubtotal = v.minSubtotal || undefined;
    if (v.freeShipping !== orig.freeShipping) payload.freeShipping = v.freeShipping;
    if (v.maxRedemptions !== orig.maxRedemptions) payload.maxRedemptions = v.maxRedemptions ?? undefined;
    if (v.maxPerCustomer !== orig.maxPerCustomer) payload.maxPerCustomer = v.maxPerCustomer ?? undefined;
    if (v.active !== orig.active) payload.active = v.active;
    if (v.startsAt !== (orig.startsAt ?? '')) payload.startsAt = v.startsAt || undefined;
    if (v.expiresAt !== (orig.expiresAt ?? '')) payload.expiresAt = v.expiresAt || undefined;

    this.saving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.service.update(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.editingId.set(null);
        this.editingOriginal = null;
        this.successMessage.set('Coupon updated.');
        this.load();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update coupon.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  protected remove(c: CouponDto): void {
    if (!confirm(`Delete coupon "${c.code}"? This cannot be undone.`)) return;
    this.deletingId.set(c.id);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.service.delete(c.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Coupon deleted.');
        this.load();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete coupon.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  protected dismissBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  protected formatDiscount(c: CouponDto): string {
    if (c.discountType === 'PERCENT') {
      return `${c.discountValue}% off`;
    }
    return `${c.currency} ${c.discountValue} off`;
  }
}
