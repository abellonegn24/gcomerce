import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ProductsService } from '../../core/catalog/products.service';
import { UploadsService } from '../../core/catalog/uploads.service';
import {
  CreateMediaPayload,
  MediaType,
  ProductMedia,
  UPLOAD_ACCEPTED_TYPES,
  UPLOAD_ACCEPT_ATTR,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * Media manager tab for {@code /products/:id}.
 *
 * Two ways to add media:
 *   1. Direct file upload — drag-drop or file picker sends the file to
 *      {@code POST /api/admin/v1/uploads/product-media}. On a successful
 *      upload, the returned public URL auto-populates the URL field. The
 *      admin can then adjust altText / primary flag and click "Attach media"
 *      to write the DB row. This keeps the DB write independent of the
 *      storage backend (retryable, undo-able).
 *   2. Paste an existing {@code https://} URL — the classic flow, useful
 *      when images already live on a CDN.
 *
 * The parent owns the product record and re-fetches after each mutation so
 * the media list stays in sync.
 */
@Component({
  selector: 'gi-admin-product-media',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './product-media.component.html',
  styleUrl: './product-media.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductMediaComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProductsService);
  private readonly uploads = inject(UploadsService);

  @Input({ required: true }) productId!: number;
  @Input() media: ProductMedia[] = [];

  @Output() readonly changed = new EventEmitter<ProductMedia[]>();

  @ViewChild('filePicker') protected filePicker?: ElementRef<HTMLInputElement>;

  /** Value passed to the {@code accept} attribute of the file input. */
  protected readonly acceptTypes = UPLOAD_ACCEPT_ATTR;

  protected readonly form = this.fb.nonNullable.group({
    url:       ['', [Validators.required, Validators.maxLength(500),
                     Validators.pattern(/^https?:\/\/\S+$/)]],
    altText:   ['', [Validators.maxLength(255)]],
    mediaType: this.fb.nonNullable.control<MediaType>('IMAGE'),
    isPrimary: this.fb.nonNullable.control<boolean>(false),
    sortOrder: this.fb.nonNullable.control<number>(0),
  });

  protected readonly saving = signal<boolean>(false);
  protected readonly removingId = signal<number | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  // Upload state
  protected readonly uploading = signal<boolean>(false);
  protected readonly uploadPercent = signal<number>(0);
  protected readonly uploadedFileName = signal<string | null>(null);
  protected readonly dragActive = signal<boolean>(false);

  // Thumbnail URLs from the most recent upload (cleared on attach).
  protected readonly pendingCardThumb = signal<string | null>(null);
  protected readonly pendingDetailThumb = signal<string | null>(null);

  // ---------------------------------------------------------------------------

  protected addMedia(): void {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: CreateMediaPayload = {
      url: raw.url.trim(),
      altText: raw.altText.trim() || undefined,
      mediaType: raw.mediaType,
      isPrimary: raw.isPrimary,
      sortOrder: Number(raw.sortOrder) || 0,
      cardThumbnailUrl: this.pendingCardThumb(),
      detailThumbnailUrl: this.pendingDetailThumb(),
    };
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.saving.set(true);
    this.service.addMedia(this.productId, payload).subscribe({
      next: (list) => {
        this.saving.set(false);
        this.successMessage.set('Media attached.');
        this.form.reset({
          url: '',
          altText: '',
          mediaType: 'IMAGE',
          isPrimary: false,
          sortOrder: 0,
        });
        this.pendingCardThumb.set(null);
        this.pendingDetailThumb.set(null);
        this.changed.emit(list);
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not attach that media.');
      },
    });
  }

  protected removeMedia(m: ProductMedia): void {
    if (this.removingId() !== null) return;
    if (!confirm(`Remove this media entry?\n\n${m.url}`)) return;
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.removingId.set(m.id);
    this.service.removeMedia(this.productId, m.id).subscribe({
      next: () => {
        this.removingId.set(null);
        // Optimistically drop it locally; parent will re-fetch.
        const updated = this.media.filter((x) => x.id !== m.id);
        this.successMessage.set('Media removed.');
        this.changed.emit(updated);
      },
      error: (err: NormalizedApiError) => {
        this.removingId.set(null);
        this.errorMessage.set(err.message || 'Could not remove that media.');
      },
    });
  }

  /** Promote by adding the same URL again with isPrimary=true — backend demotes others. */
  protected promoteToPrimary(m: ProductMedia): void {
    if (this.saving() || m.isPrimary) return;
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.saving.set(true);
    this.service
      .addMedia(this.productId, {
        url: m.url,
        altText: m.altText ?? undefined,
        mediaType: m.mediaType,
        isPrimary: true,
        sortOrder: m.sortOrder,
        cardThumbnailUrl: m.cardThumbnailUrl,
        detailThumbnailUrl: m.detailThumbnailUrl,
      })
      .subscribe({
        next: (list) => {
          this.saving.set(false);
          this.successMessage.set('Primary image updated.');
          this.changed.emit(list);
        },
        error: (err: NormalizedApiError) => {
          this.saving.set(false);
          this.errorMessage.set(err.message || 'Could not promote that media.');
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Drag-drop + file picker upload
  // ---------------------------------------------------------------------------

  /** Native <input type="file"> change handler. */
  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files.length > 0 ? input.files[0] : null;
    // Reset the input so re-selecting the same file re-fires the event.
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

  protected openPicker(): void {
    if (this.uploading()) return;
    this.filePicker?.nativeElement.click();
  }

  /**
   * Push a single file through the upload endpoint. On success, populate
   * the URL field + media type so the admin only has to click "Attach media"
   * to finish. We do NOT auto-attach because they might want to set alt text
   * or the primary flag first.
   */
  private uploadFile(file: File): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);

    // Client-side type check — the server enforces the real allowlist.
    if (!UPLOAD_ACCEPTED_TYPES.includes(file.type)) {
      this.errorMessage.set(
        `Unsupported file type "${file.type || 'unknown'}". Allowed: JPG, PNG, WEBP, GIF, MP4, MOV, WEBM.`,
      );
      return;
    }

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
          const mediaType: MediaType = res.contentType.startsWith('video/') ? 'VIDEO' : 'IMAGE';
          this.form.patchValue({
            url: res.url,
            mediaType,
          });
          this.pendingCardThumb.set(res.cardThumbnailUrl);
          this.pendingDetailThumb.set(res.detailThumbnailUrl);
          this.successMessage.set(
            `Uploaded "${file.name}" (${(res.sizeBytes / 1024).toFixed(1)} KB). ` +
            `Adjust alt text or primary flag, then click Attach media.`,
          );
        }
      },
      error: (err: NormalizedApiError) => {
        this.uploading.set(false);
        this.uploadPercent.set(0);
        this.uploadedFileName.set(null);
        this.errorMessage.set(err.message || 'Upload failed.');
      },
    });
  }
}
