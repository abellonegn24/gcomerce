import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { PriceListsService } from '../../core/catalog/price-lists.service';
import { CustomerGroupsService } from '../../core/catalog/customer-groups.service';
import {
  CreatePriceListPayload,
  CustomerGroupDto,
  PriceListDto,
  UpdatePriceListPayload,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * /price-lists — admin management of per-customer-group price sheets.
 *
 * Handles list + create + delete. Per-list entry management lives in the
 * product editor's pricing tab so the workflow follows the natural
 * "edit product → set its tier" mental model.
 */
@Component({
  selector: 'gi-admin-price-lists',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './price-lists.component.html',
  styleUrl: './price-lists.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriceListsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PriceListsService);
  private readonly groups = inject(CustomerGroupsService);

  protected readonly lists = signal<PriceListDto[]>([]);
  protected readonly customerGroups = signal<CustomerGroupDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  /** Id of the row whose inline edit form is currently expanded, if any. */
  protected readonly editingId = signal<number | null>(null);
  /** Snapshot of the original row so we can detect "clear" for effective dates. */
  private editingOriginal: PriceListDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    customerGroupId: this.fb.nonNullable.control<number | null>(null, [Validators.required]),
    name:            ['', [Validators.required, Validators.maxLength(200)]],
    currency:        ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    active:          this.fb.nonNullable.control<boolean>(true),
    effectiveFrom:   [''],
    effectiveTo:     [''],
  });

  /** Separate form for the inline row-edit — same shape as create, minus customer group. */
  protected readonly editForm = this.fb.nonNullable.group({
    name:          ['', [Validators.required, Validators.maxLength(200)]],
    currency:      ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    active:        this.fb.nonNullable.control<boolean>(true),
    effectiveFrom: [''],
    effectiveTo:   [''],
  });

  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    this.reload();
    this.groups.list().subscribe({
      next: (g) => this.customerGroups.set(g),
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load customer groups.');
      },
    });
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list().subscribe({
      next: (lists) => {
        this.lists.set(lists);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load price lists.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------

  protected openCreate(): void {
    this.clearBanners();
    this.editingId.set(null);   // don't leave a row-edit open behind the create panel
    this.editingOriginal = null;
    this.form.reset({
      customerGroupId: null,
      name: '',
      currency: 'USD',
      active: true,
      effectiveFrom: '',
      effectiveTo: '',
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
    if (raw.customerGroupId === null) return;

    const payload: CreatePriceListPayload = {
      customerGroupId: raw.customerGroupId,
      name: raw.name.trim(),
      currency: raw.currency.trim().toUpperCase(),
      active: raw.active,
      effectiveFrom: raw.effectiveFrom || undefined,
      effectiveTo: raw.effectiveTo || undefined,
    };
    this.clearBanners();
    this.saving.set(true);
    this.service.create(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Price list created.');
        this.showCreate.set(false);
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create that price list.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Edit (inline row expansion)
  // ---------------------------------------------------------------------------

  protected openEdit(list: PriceListDto): void {
    this.clearBanners();
    this.showCreate.set(false);
    this.editingId.set(list.id);
    this.editingOriginal = list;
    this.editForm.reset({
      name:          list.name,
      currency:      list.currency,
      active:        list.active,
      effectiveFrom: list.effectiveFrom ?? '',
      effectiveTo:   list.effectiveTo ?? '',
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
    const payload: UpdatePriceListPayload = {
      name:     raw.name.trim(),
      currency: raw.currency.trim().toUpperCase(),
      active:   raw.active,
    };
    // PATCH semantics: a value present in the form updates the field; a value
    // that transitioned from "set" to "" means the admin wants it cleared.
    if (raw.effectiveFrom) {
      payload.effectiveFrom = raw.effectiveFrom;
    } else if (orig?.effectiveFrom) {
      payload.clearEffectiveFrom = true;
    }
    if (raw.effectiveTo) {
      payload.effectiveTo = raw.effectiveTo;
    } else if (orig?.effectiveTo) {
      payload.clearEffectiveTo = true;
    }

    this.clearBanners();
    this.saving.set(true);
    this.service.update(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Price list updated.');
        this.editingId.set(null);
        this.editingOriginal = null;
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that price list.');
      },
    });
  }

  // ---------------------------------------------------------------------------

  protected remove(list: PriceListDto): void {
    if (this.deletingId() !== null) return;
    if (!confirm(`Delete price list "${list.name}"?\n\nThis will also remove all its entries. This cannot be undone.`)) {
      return;
    }
    this.clearBanners();
    this.deletingId.set(list.id);
    this.service.delete(list.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Price list deleted.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete that price list.');
      },
    });
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
