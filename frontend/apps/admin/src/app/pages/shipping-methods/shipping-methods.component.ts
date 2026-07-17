import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CreateShippingMethodPayload,
  ShippingMethodDto,
  UpdateShippingMethodPayload,
} from '../../core/shipping/shipping.model';
import { ShippingMethodsService } from '../../core/shipping/shipping-methods.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * /shipping-methods — admin management of the checkout rate card.
 *
 * Mirrors the /price-lists inline-edit pattern: click Edit on a row →
 * a full-width form expands underneath. Only one row can be edited at a
 * time and the create panel and edit panel are mutually exclusive.
 *
 * Note on the rate model — cost is derived server-side at checkout as
 *   {@code baseCost + perKgRate * totalCartWeightKg}.
 * The admin never types a raw shipping cost; they configure the rate card
 * and the storefront quote endpoint applies it to the customer's cart.
 */
@Component({
  selector: 'gi-admin-shipping-methods',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './shipping-methods.component.html',
  styleUrl: './shipping-methods.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShippingMethodsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ShippingMethodsService);

  protected readonly methods = signal<ShippingMethodDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  protected readonly editingId = signal<number | null>(null);
  private editingOriginal: ShippingMethodDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code:        ['', [Validators.required, Validators.pattern(/^[A-Z0-9_]+$/), Validators.maxLength(64)]],
    name:        ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    currency:    ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    baseCost:    ['0.00', [Validators.required, Validators.min(0)]],
    perKgRate:   ['0.00', [Validators.required, Validators.min(0)]],
    minDays:     this.fb.nonNullable.control<number | null>(null),
    maxDays:     this.fb.nonNullable.control<number | null>(null),
    active:      this.fb.nonNullable.control<boolean>(true),
    sortOrder:   [100],
  });

  protected readonly editForm = this.fb.nonNullable.group({
    code:        ['', [Validators.required, Validators.pattern(/^[A-Z0-9_]+$/), Validators.maxLength(64)]],
    name:        ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    currency:    ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    baseCost:    ['0.00', [Validators.required, Validators.min(0)]],
    perKgRate:   ['0.00', [Validators.required, Validators.min(0)]],
    minDays:     this.fb.nonNullable.control<number | null>(null),
    maxDays:     this.fb.nonNullable.control<number | null>(null),
    active:      this.fb.nonNullable.control<boolean>(true),
    sortOrder:   [100],
  });

  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list().subscribe({
      next: (rows) => {
        this.methods.set(rows);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load shipping methods.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Create
  // ---------------------------------------------------------------------------

  protected openCreate(): void {
    this.clearBanners();
    this.editingId.set(null);
    this.editingOriginal = null;
    this.form.reset({
      code: '',
      name: '',
      description: '',
      currency: 'USD',
      baseCost: '0.00',
      perKgRate: '0.00',
      minDays: null,
      maxDays: null,
      active: true,
      sortOrder: 100,
    });
    this.showCreate.set(true);
  }

  protected cancelCreate(): void {
    this.showCreate.set(false);
  }

  protected submitCreate(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: CreateShippingMethodPayload = {
      code: raw.code.trim().toUpperCase(),
      name: raw.name.trim(),
      currency: raw.currency.trim().toUpperCase(),
      baseCost: raw.baseCost,
      perKgRate: raw.perKgRate,
      active: raw.active,
      sortOrder: raw.sortOrder ?? 100,
      ...(raw.description && raw.description.trim() ? { description: raw.description.trim() } : {}),
      ...(raw.minDays != null ? { minDays: raw.minDays } : {}),
      ...(raw.maxDays != null ? { maxDays: raw.maxDays } : {}),
    };
    this.clearBanners();
    this.saving.set(true);
    this.service.create(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Shipping method created.');
        this.showCreate.set(false);
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create that shipping method.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Edit (inline row expansion)
  // ---------------------------------------------------------------------------

  protected openEdit(m: ShippingMethodDto): void {
    this.clearBanners();
    this.showCreate.set(false);
    this.editingId.set(m.id);
    this.editingOriginal = m;
    this.editForm.reset({
      code:        m.code,
      name:        m.name,
      description: m.description ?? '',
      currency:    m.currency,
      baseCost:    String(m.baseCost),
      perKgRate:   String(m.perKgRate),
      minDays:     m.minDays,
      maxDays:     m.maxDays,
      active:      m.active,
      sortOrder:   m.sortOrder,
    });
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.editingOriginal = null;
  }

  protected submitEdit(): void {
    const id = this.editingId();
    if (id === null || this.saving()) return;
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const raw = this.editForm.getRawValue();
    const orig = this.editingOriginal;
    const payload: UpdateShippingMethodPayload = {
      code:      raw.code.trim().toUpperCase(),
      name:      raw.name.trim(),
      currency:  raw.currency.trim().toUpperCase(),
      baseCost:  raw.baseCost,
      perKgRate: raw.perKgRate,
      active:    raw.active,
      sortOrder: raw.sortOrder ?? 100,
    };
    // PATCH semantics for the nullable text field:
    //   value provided → set it
    //   was set + now blank → clear it
    //   both empty → leave alone
    const descTrimmed = (raw.description ?? '').trim();
    if (descTrimmed) {
      payload.description = descTrimmed;
    } else if (orig?.description) {
      payload.clearDescription = true;
    }
    // Same idea for the transit-day range — treat as a paired field so the
    // clear flag either wipes both or leaves both alone.
    if (raw.minDays == null && raw.maxDays == null
        && (orig?.minDays != null || orig?.maxDays != null)) {
      payload.clearDeliveryDays = true;
    } else {
      if (raw.minDays != null) payload.minDays = raw.minDays;
      if (raw.maxDays != null) payload.maxDays = raw.maxDays;
    }

    this.clearBanners();
    this.saving.set(true);
    this.service.update(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Shipping method updated.');
        this.editingId.set(null);
        this.editingOriginal = null;
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that shipping method.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  protected remove(m: ShippingMethodDto): void {
    if (this.deletingId() !== null) return;
    if (!confirm(
      `Delete shipping method "${m.name}" (${m.code})?\n\n` +
      `Historical orders that used it keep their snapshot code and name, so ` +
      `invoices remain readable — but the storefront picker will no longer ` +
      `offer it. This cannot be undone.`,
    )) {
      return;
    }
    this.clearBanners();
    this.deletingId.set(m.id);
    this.service.delete(m.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Shipping method deleted.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete that shipping method.');
      },
    });
  }

  // ---------------------------------------------------------------------------

  protected transitLabel(m: ShippingMethodDto): string {
    if (m.minDays == null && m.maxDays == null) return '—';
    if (m.minDays != null && m.maxDays != null && m.minDays === m.maxDays) {
      return `${m.minDays} day${m.minDays === 1 ? '' : 's'}`;
    }
    if (m.minDays != null && m.maxDays != null) return `${m.minDays}–${m.maxDays} days`;
    if (m.minDays != null) return `${m.minDays}+ days`;
    return `up to ${m.maxDays} days`;
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
