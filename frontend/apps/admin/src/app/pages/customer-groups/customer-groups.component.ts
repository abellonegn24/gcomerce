import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CreateCustomerGroupPayload,
  CustomerGroupDto,
  PRICE_TIER_LABELS,
  PRICE_TIER_OPTIONS,
  UpdateCustomerGroupPayload,
} from '../../core/catalog/catalog.model';
import { CustomerGroupsService } from '../../core/catalog/customer-groups.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * /customer-groups — admin CRUD on the B2B pricing-tier groups.
 *
 * <p>Mirrors the /shipping-methods and /price-lists inline-edit pattern:
 * click Edit on a row → a full-width form expands underneath. The create
 * panel and edit panel are mutually exclusive.
 *
 * <p>Two fields deserve extra care on this page:
 * <ul>
 *   <li><strong>Price tier</strong> — controls which tier of a bound price
 *       list is applied to a customer. Changing it after price lists exist
 *       will retarget every customer in this group.</li>
 *   <li><strong>MOQ multiplier</strong> — multiplied against the product's
 *       {@code minOrderQuantity} at cart-add time. A wholesale group with
 *       multiplier 5 forces customers to buy at least 5× the retail MOQ.</li>
 * </ul>
 */
@Component({
  selector: 'gi-admin-customer-groups',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './customer-groups.component.html',
  styleUrl: './customer-groups.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerGroupsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CustomerGroupsService);

  protected readonly PRICE_TIER_LABELS = PRICE_TIER_LABELS;
  protected readonly PRICE_TIER_OPTIONS = PRICE_TIER_OPTIONS;

  protected readonly groups = signal<CustomerGroupDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  protected readonly editingId = signal<number | null>(null);
  private editingOriginal: CustomerGroupDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    code:          ['', [Validators.required, Validators.pattern(/^[A-Z0-9_]+$/), Validators.maxLength(64)]],
    name:          ['', [Validators.required, Validators.maxLength(200)]],
    description:   [''],
    priceTier:     this.fb.nonNullable.control<'RETAIL' | 'WHOLESALE_T1' | 'WHOLESALE_T2' | 'VIP'>('WHOLESALE_T1'),
    moqMultiplier: ['1.000', [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    active:        this.fb.nonNullable.control<boolean>(true),
  });

  protected readonly editForm = this.fb.nonNullable.group({
    code:          ['', [Validators.required, Validators.pattern(/^[A-Z0-9_]+$/), Validators.maxLength(64)]],
    name:          ['', [Validators.required, Validators.maxLength(200)]],
    description:   [''],
    priceTier:     this.fb.nonNullable.control<'RETAIL' | 'WHOLESALE_T1' | 'WHOLESALE_T2' | 'VIP'>('WHOLESALE_T1'),
    moqMultiplier: ['1.000', [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    active:        this.fb.nonNullable.control<boolean>(true),
  });

  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list(true).subscribe({
      next: (rows) => {
        this.groups.set(rows);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load customer groups.');
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
      priceTier: 'WHOLESALE_T1',
      moqMultiplier: '1.000',
      active: true,
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
    const payload: CreateCustomerGroupPayload = {
      code: raw.code.trim().toUpperCase(),
      name: raw.name.trim(),
      priceTier: raw.priceTier,
      moqMultiplier: raw.moqMultiplier,
      active: raw.active,
      ...(raw.description && raw.description.trim() ? { description: raw.description.trim() } : {}),
    };
    this.clearBanners();
    this.saving.set(true);
    this.service.create(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Customer group created.');
        this.showCreate.set(false);
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create that customer group.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Edit
  // ---------------------------------------------------------------------------

  protected openEdit(g: CustomerGroupDto): void {
    this.clearBanners();
    this.showCreate.set(false);
    this.editingId.set(g.id);
    this.editingOriginal = g;
    this.editForm.reset({
      code:          g.code,
      name:          g.name,
      description:   g.description ?? '',
      priceTier:     g.priceTier,
      moqMultiplier: g.moqMultiplier,
      active:        g.active,
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
    const payload: UpdateCustomerGroupPayload = {
      code:          raw.code.trim().toUpperCase(),
      name:          raw.name.trim(),
      priceTier:     raw.priceTier,
      moqMultiplier: raw.moqMultiplier,
      active:        raw.active,
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

    this.clearBanners();
    this.saving.set(true);
    this.service.update(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Customer group updated.');
        this.editingId.set(null);
        this.editingOriginal = null;
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that customer group.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  protected remove(g: CustomerGroupDto): void {
    if (this.deletingId() !== null) return;
    if (g.customerCount > 0) {
      // Server would reject too — but a heads-up is friendlier than a 409.
      alert(
        `"${g.name}" still has ${g.customerCount} customer(s) assigned. ` +
        `Reassign them to another group before deleting.`,
      );
      return;
    }
    if (!confirm(
      `Delete customer group "${g.name}" (${g.code})?\n\n` +
      `Any price lists still bound to this group will need to be deleted or ` +
      `retargeted first — the server will reject the delete otherwise. This ` +
      `cannot be undone.`,
    )) {
      return;
    }
    this.clearBanners();
    this.deletingId.set(g.id);
    this.service.delete(g.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Customer group deleted.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete that customer group.');
      },
    });
  }

  // ---------------------------------------------------------------------------

  protected multiplierLabel(g: CustomerGroupDto): string {
    // "1.000" → "1×", "5.000" → "5×", "2.500" → "2.5×"
    const n = Number(g.moqMultiplier);
    if (!Number.isFinite(n)) return `${g.moqMultiplier}×`;
    return `${n % 1 === 0 ? n.toFixed(0) : n.toString()}×`;
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
