import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CATEGORY_OPTIONS,
  CreateProductPayload,
  ProductDetail,
  STATUS_OPTIONS,
  UpdateProductPayload,
} from '../../core/catalog/catalog.model';

/**
 * Reactive-form component used for both {@code /products/new} and the
 * "Details" tab under {@code /products/:id}.
 *
 * Fires:
 *   - {@code save} — with a {@link CreateProductPayload} in create mode or
 *     an {@link UpdateProductPayload} in edit mode.
 *   - {@code cancel} — user clicked cancel; parent decides where to route.
 *
 * The parent is responsible for calling {@link ProductsService.create} or
 * {@link ProductsService.update} and then displaying success / error state.
 * We surface {@code submitting} and {@code errorMessage} inputs so the
 * parent can drive the UI without re-emitting form state.
 */
@Component({
  selector: 'gi-admin-product-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormComponent implements OnChanges {
  private readonly fb = inject(FormBuilder);

  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initial: ProductDetail | null = null;
  @Input() submitting = false;
  @Input() errorMessage: string | null = null;

  @Output() readonly save = new EventEmitter<CreateProductPayload | UpdateProductPayload>();
  @Output() readonly cancel = new EventEmitter<void>();

  protected readonly categories = CATEGORY_OPTIONS;
  protected readonly statuses = STATUS_OPTIONS;

  // Prevent multi-submits on double-click.
  protected readonly localBusy = signal<boolean>(false);

  // We deliberately allow all fields optional in the form model; validation
  // is enforced via Validators. String-typed money fields keep precision.
  protected readonly form = this.fb.nonNullable.group({
    sku:              ['', [Validators.required, Validators.maxLength(64),
                            Validators.pattern(/^[A-Z0-9._-]+$/)]],
    slug:             ['', [Validators.required, Validators.maxLength(200),
                            Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)]],
    name:             ['', [Validators.required, Validators.maxLength(255)]],
    shortDescription: ['', [Validators.maxLength(500)]],
    description:      [''],

    category:         ['RAW_COFFEE', [Validators.required]],
    status:           ['DRAFT',      [Validators.required]],

    weightKg:         ['', []],
    hsCode:           ['', [Validators.maxLength(32)]],
    originCountry:    ['ET', [Validators.pattern(/^[A-Za-z]{2}$/)]],

    baseCurrency:     ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    basePrice:        ['0.00', [Validators.required, Validators.pattern(/^\d+(?:\.\d{1,4})?$/)]],
    costPrice:        ['',      [Validators.pattern(/^\d+(?:\.\d{1,4})?$/)]],
    minOrderQuantity: ['1',     [Validators.pattern(/^\d+(?:\.\d{1,3})?$/)]],
    packagingNotes:   ['',      [Validators.maxLength(500)]],
  });

  ngOnChanges(changes: SimpleChanges): void {
    if ('initial' in changes && this.initial) {
      this.applyInitial(this.initial);
    }
    if ('mode' in changes && this.mode === 'edit') {
      this.form.controls.sku.disable({ emitEvent: false });
    } else if ('mode' in changes && this.mode === 'create') {
      this.form.controls.sku.enable({ emitEvent: false });
    }
  }

  private applyInitial(p: ProductDetail): void {
    this.form.reset({
      sku: p.sku,
      slug: p.slug,
      name: p.name,
      shortDescription: p.shortDescription ?? '',
      description: p.description ?? '',
      category: p.category,
      status: p.status,
      weightKg: p.weightKg ?? '',
      hsCode: p.hsCode ?? '',
      originCountry: p.originCountry ?? 'ET',
      baseCurrency: p.baseCurrency,
      basePrice: p.basePrice,
      costPrice: p.costPrice ?? '',
      minOrderQuantity: p.minOrderQuantity ?? '1',
      packagingNotes: p.packagingNotes ?? '',
    });
    // SKU cannot be changed after creation.
    this.form.controls.sku.disable({ emitEvent: false });
  }

  protected submit(): void {
    if (this.submitting || this.localBusy()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.localBusy.set(true);
    setTimeout(() => this.localBusy.set(false), 250);

    const raw = this.form.getRawValue();
    const trim = (s: string): string | undefined => (s.trim() === '' ? undefined : s.trim());

    if (this.mode === 'create') {
      const payload: CreateProductPayload = {
        sku: raw.sku.trim().toUpperCase(),
        slug: raw.slug.trim().toLowerCase(),
        name: raw.name.trim(),
        shortDescription: trim(raw.shortDescription),
        description: trim(raw.description),
        category: raw.category as CreateProductPayload['category'],
        status: raw.status as CreateProductPayload['status'],
        weightKg: trim(raw.weightKg),
        hsCode: trim(raw.hsCode),
        originCountry: trim(raw.originCountry)?.toUpperCase(),
        baseCurrency: raw.baseCurrency.trim().toUpperCase(),
        basePrice: raw.basePrice.trim(),
        costPrice: trim(raw.costPrice),
        minOrderQuantity: trim(raw.minOrderQuantity),
        packagingNotes: trim(raw.packagingNotes),
      };
      this.save.emit(payload);
      return;
    }

    // edit
    const payload: UpdateProductPayload = {
      slug: raw.slug.trim().toLowerCase(),
      name: raw.name.trim(),
      shortDescription: trim(raw.shortDescription),
      description: trim(raw.description),
      category: raw.category as UpdateProductPayload['category'],
      status: raw.status as UpdateProductPayload['status'],
      weightKg: trim(raw.weightKg),
      hsCode: trim(raw.hsCode),
      originCountry: trim(raw.originCountry)?.toUpperCase(),
      baseCurrency: raw.baseCurrency.trim().toUpperCase(),
      basePrice: raw.basePrice.trim(),
      costPrice: trim(raw.costPrice),
      minOrderQuantity: trim(raw.minOrderQuantity),
      packagingNotes: trim(raw.packagingNotes),
    };
    this.save.emit(payload);
  }

  protected onCancel(): void {
    this.cancel.emit();
  }
}
