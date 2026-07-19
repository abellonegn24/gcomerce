import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
  computed,
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
  UPLOAD_ACCEPT_ATTR,
  UploadResponse,
  UpdateProductPayload,
} from '../../core/catalog/catalog.model';
import { UploadsService } from '../../core/catalog/uploads.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

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
  private readonly uploads = inject(UploadsService);

  @Input() mode: 'create' | 'edit' = 'create';
  @Input() initial: ProductDetail | null = null;
  @Input() submitting = false;
  @Input() errorMessage: string | null = null;
  @Input() successMessage: string | null = null;

  @Output() readonly save = new EventEmitter<CreateProductPayload | UpdateProductPayload>();
  @Output() readonly cancel = new EventEmitter<void>();
  @Output() readonly imageChange = new EventEmitter<UploadResponse | null>();

  @ViewChild('filePicker') protected filePicker?: ElementRef<HTMLInputElement>;

  protected readonly categories = CATEGORY_OPTIONS;
  protected readonly statuses = STATUS_OPTIONS;

  // Prevent multi-submits on double-click.
  protected readonly localBusy = signal<boolean>(false);

  protected readonly acceptTypes = UPLOAD_ACCEPT_ATTR;

  // Upload state
  protected readonly uploading = signal<boolean>(false);
  protected readonly uploadPercent = signal<number>(0);
  protected readonly uploadedFileName = signal<string | null>(null);
  protected readonly dragActive = signal<boolean>(false);
  protected readonly uploadError = signal<string | null>(null);
  protected readonly pendingImage = signal<UploadResponse | null>(null);

  protected readonly currentPrimaryImage = computed<string | null>(() => {
    const p = this.initial;
    if (!p || !p.media || p.media.length === 0) return null;
    const primary = p.media.find((m) => m.isPrimary);
    return (primary ?? p.media[0]).cardThumbnailUrl ?? (primary ?? p.media[0]).url;
  });

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
    const str = (v: unknown): string => (v === null || v === undefined ? '' : String(v));
    this.form.reset({
      sku: p.sku,
      slug: p.slug,
      name: p.name,
      shortDescription: p.shortDescription ?? '',
      description: p.description ?? '',
      category: p.category,
      status: p.status,
      weightKg: str(p.weightKg),
      hsCode: p.hsCode ?? '',
      originCountry: p.originCountry ?? 'ET',
      baseCurrency: p.baseCurrency,
      basePrice: str(p.basePrice),
      costPrice: str(p.costPrice),
      minOrderQuantity: str(p.minOrderQuantity) || '1',
      packagingNotes: p.packagingNotes ?? '',
    });
    // SKU cannot be changed after creation.
    this.form.controls.sku.disable({ emitEvent: false });
  }

  protected submit(): void {
    if (this.submitting || this.localBusy()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.localBusy.set(true);
      setTimeout(() => this.localBusy.set(false), 100);
      return;
    }
    this.localBusy.set(true);
    setTimeout(() => this.localBusy.set(false), 250);

    const raw = this.form.getRawValue();
    const trim = (s: unknown): string | undefined => {
      if (s === null || s === undefined) return undefined;
      const str = String(s).trim();
      return str === '' ? undefined : str;
    };

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

  // ---------------------------------------------------------------------------
  // Image upload (create mode only)
  // ---------------------------------------------------------------------------

  protected openPicker(): void {
    if (this.uploading()) return;
    this.filePicker?.nativeElement.click();
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files.length > 0 ? input.files[0] : null;
    input.value = '';
    if (file) this.uploadFile(file);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.uploading()) this.dragActive.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragActive.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragActive.set(false);
    if (this.uploading()) return;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.uploadFile(file);
  }

  protected clearPendingImage(): void {
    this.pendingImage.set(null);
    this.uploadedFileName.set(null);
    this.uploadError.set(null);
    this.imageChange.emit(null);
  }

  private uploadFile(file: File): void {
    this.uploadError.set(null);
    this.uploading.set(true);
    this.uploadPercent.set(0);
    this.uploadedFileName.set(file.name);
    this.uploads.uploadProductMedia(file).subscribe({
      next: (evt) => {
        if (evt.phase === 'progress') {
          this.uploadPercent.set(evt.percent);
        } else {
          const res = evt.response;
          this.uploading.set(false);
          this.uploadPercent.set(100);
          this.pendingImage.set(res);
          this.uploadedFileName.set(file.name);
          this.imageChange.emit(res);
        }
      },
      error: (err: NormalizedApiError) => {
        this.uploading.set(false);
        this.uploadPercent.set(0);
        this.uploadedFileName.set(null);
        this.uploadError.set(err.message || 'Upload failed.');
      },
    });
  }
}
