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

import { VariantsService } from '../../core/catalog/variants.service';
import {
  VariantDto,
  VariantInventoryDto,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

interface AttrRow {
  key: string;
  value: string;
}

@Component({
  selector: 'gi-admin-product-variants',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-variants.component.html',
  styleUrl: './product-variants.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductVariantsComponent implements OnInit, OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(VariantsService);

  @Input({ required: true }) productId!: number;

  protected readonly variants = signal<VariantDto[]>([]);
  protected readonly inventoryMap = signal<Map<number, VariantInventoryDto>>(new Map());
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly editingId = signal<number | null>(null);
  protected readonly showForm = signal<boolean>(false);
  protected readonly showInvModal = signal<boolean>(false);
  protected readonly invVariant = signal<VariantDto | null>(null);
  protected readonly invMode = signal<'adjust' | 'set'>('adjust');

  protected readonly hasVariants = computed(() => this.variants().length > 0);

  protected readonly form = this.fb.nonNullable.group({
    sku:  ['', [Validators.required, Validators.pattern(/^[A-Z0-9._-]+$/), Validators.maxLength(64)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
    priceOverride: ['', [Validators.pattern(/^\d+(?:\.\d{1,4})?$/)]],
    weightKgOverride: ['', [Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    active: [true],
    sortOrder: [0, [Validators.pattern(/^\d+$/)]],
  });

  protected readonly attrRows = signal<AttrRow[]>([]);

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
    this.service.list(this.productId).subscribe({
      next: (list) => {
        this.variants.set(list);
        this.loading.set(false);
        this.loadInventory();
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load variants.');
        this.loading.set(false);
      },
    });
  }

  private loadInventory(): void {
    this.service.listInventory(this.productId).subscribe({
      next: (rows) => {
        const map = new Map<number, VariantInventoryDto>();
        for (const r of rows) map.set(r.variantId, r);
        this.inventoryMap.set(map);
      },
      error: () => {
        // Non-fatal — variant list still works without inventory detail.
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Create / Edit form
  // ---------------------------------------------------------------------------

  protected openCreate(): void {
    this.editingId.set(null);
    this.showForm.set(true);
    this.form.reset({ sku: '', name: '', priceOverride: '', weightKgOverride: '', active: true, sortOrder: 0 });
    this.attrRows.set([]);
    this.clearBanners();
  }

  protected openEdit(v: VariantDto): void {
    this.editingId.set(v.id);
    this.showForm.set(true);
    this.form.reset({
      sku: v.sku,
      name: v.name,
      priceOverride: v.priceOverride ?? '',
      weightKgOverride: v.weightKgOverride ?? '',
      active: v.active,
      sortOrder: v.sortOrder,
    });
    this.attrRows.set(v.attributes.map(a => ({ key: a.key, value: a.value })));
    this.clearBanners();
  }

  protected cancelForm(): void {
    this.showForm.set(false);
    this.editingId.set(null);
  }

  protected addAttrRow(): void {
    this.attrRows.update(rows => [...rows, { key: '', value: '' }]);
  }

  protected removeAttrRow(idx: number): void {
    this.attrRows.update(rows => rows.filter((_, i) => i !== idx));
  }

  protected submitForm(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const attrs = this.attrRows()
      .filter(r => r.key.trim() && r.value.trim())
      .map(r => ({ key: r.key.trim(), value: r.value.trim() }));

    const payload: Record<string, unknown> = {
      sku: raw.sku.trim().toUpperCase(),
      name: raw.name.trim(),
      active: raw.active,
      sortOrder: Number(raw.sortOrder) || 0,
      attributes: attrs.length > 0 ? attrs : undefined,
    };
    if (raw.priceOverride.trim()) payload['priceOverride'] = raw.priceOverride.trim();
    if (raw.weightKgOverride.trim()) payload['weightKgOverride'] = raw.weightKgOverride.trim();

    this.clearBanners();
    this.saving.set(true);
    const id = this.editingId();
    const op = id != null
      ? this.service.update(this.productId, id, payload)
      : this.service.create(this.productId, payload as unknown as Parameters<typeof this.service.create>[1]);

    op.subscribe({
      next: (saved) => {
        this.saving.set(false);
        if (id != null) {
          this.variants.update(list => list.map(v => v.id === id ? saved : v));
          this.successMessage.set(`Variant "${saved.name}" updated.`);
        } else {
          this.variants.update(list => [...list, saved]);
          this.successMessage.set(`Variant "${saved.name}" created.`);
        }
        this.showForm.set(false);
        this.editingId.set(null);
        this.loadInventory();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not save the variant.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  protected deleteVariant(v: VariantDto): void {
    if (!confirm(`Delete variant "${v.name}" (${v.sku})?\n\nThis removes the variant, its attributes, and its inventory row. This cannot be undone.`)) {
      return;
    }
    this.clearBanners();
    this.service.delete(this.productId, v.id).subscribe({
      next: () => {
        this.variants.update(list => list.filter(x => x.id !== v.id));
        this.inventoryMap.update(m => { const c = new Map(m); c.delete(v.id); return c; });
        this.successMessage.set(`Variant "${v.name}" deleted.`);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not delete the variant.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Inventory modal
  // ---------------------------------------------------------------------------

  protected openInventory(v: VariantDto, mode: 'adjust' | 'set'): void {
    this.invVariant.set(v);
    this.invMode.set(mode);
    this.showInvModal.set(true);
    this.adjustForm.reset({ delta: '', reason: '' });
    const inv = this.inventoryMap().get(v.id);
    this.overwriteForm.reset({
      quantity: inv?.quantity ?? '',
      restockThreshold: inv?.restockThreshold ?? '',
    });
    this.clearBanners();
  }

  protected closeInventory(): void {
    this.showInvModal.set(false);
    this.invVariant.set(null);
  }

  protected submitInventory(): void {
    const v = this.invVariant();
    if (!v) return;
    if (this.saving()) return;

    if (this.invMode() === 'adjust') {
      if (this.adjustForm.invalid) {
        this.adjustForm.markAllAsTouched();
        return;
      }
      const raw = this.adjustForm.getRawValue();
      this.saving.set(true);
      this.clearBanners();
      this.service.adjustInventory(this.productId, v.id, {
        delta: raw.delta.trim(),
        reason: raw.reason.trim() || undefined,
      }).subscribe({
        next: (inv) => {
          this.saving.set(false);
          this.inventoryMap.update(m => { const c = new Map(m); c.set(v.id, inv); return c; });
          this.successMessage.set(`Adjusted ${v.name} by ${raw.delta}.`);
          this.showInvModal.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not adjust inventory.');
        },
      });
    } else {
      if (this.overwriteForm.invalid) {
        this.overwriteForm.markAllAsTouched();
        return;
      }
      const raw = this.overwriteForm.getRawValue();
      if (!confirm(`Overwrite ${v.name} quantity to ${raw.quantity}?`)) return;
      this.saving.set(true);
      this.clearBanners();
      this.service.setInventory(this.productId, v.id, {
        quantity: raw.quantity.trim(),
        restockThreshold: raw.restockThreshold.trim() || undefined,
      }).subscribe({
        next: (inv) => {
          this.saving.set(false);
          this.inventoryMap.update(m => { const c = new Map(m); c.set(v.id, inv); return c; });
          this.successMessage.set(`${v.name} inventory updated.`);
          this.showInvModal.set(false);
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not overwrite inventory.');
        },
      });
    }
  }

  // ---------------------------------------------------------------------------

  protected updateAttrKey(idx: number, value: string): void {
    this.attrRows.update(rows => rows.map((r, i) => i === idx ? { ...r, key: value } : r));
  }

  protected updateAttrValue(idx: number, value: string): void {
    this.attrRows.update(rows => rows.map((r, i) => i === idx ? { ...r, value: value } : r));
  }

  protected formatAttrs(attrs: { key: string; value: string }[]): string {
    return attrs.map(a => `${a.key}: ${a.value}`).join(' · ');
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
