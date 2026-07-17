import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CmsBannerDto,
  CreateCmsBannerPayload,
  UpdateCmsBannerPayload,
} from '../../core/cms/cms.model';
import { CmsService } from '../../core/cms/cms.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-cms-banners',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cms-banners.component.html',
  styleUrl: './cms-banners.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CmsBannersComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CmsService);

  protected readonly banners = signal<CmsBannerDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  protected readonly editingId = signal<number | null>(null);
  private editingOriginal: CmsBannerDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    placement:   ['HOMEPAGE_HERO', [Validators.required, Validators.maxLength(40)]],
    title:       [''],
    imageUrl:    ['', [Validators.required, Validators.maxLength(500)]],
    altText:     [''],
    linkUrl:     [''],
    activeFrom:  [''],
    activeTo:    [''],
    sortOrder:   [0],
    active:      [true],
  });

  protected readonly editForm = this.fb.nonNullable.group({
    placement:   ['HOMEPAGE_HERO', [Validators.required, Validators.maxLength(40)]],
    title:       [''],
    imageUrl:    ['', [Validators.required, Validators.maxLength(500)]],
    altText:     [''],
    linkUrl:     [''],
    activeFrom:  [''],
    activeTo:    [''],
    sortOrder:   [0],
    active:      [true],
  });

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.listBanners().subscribe({
      next: (rows) => {
        this.banners.set(rows);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load banners.');
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.clearBanners();
    this.editingId.set(null);
    this.editingOriginal = null;
    this.form.reset({
      placement: 'HOMEPAGE_HERO', title: '', imageUrl: '',
      altText: '', linkUrl: '', activeFrom: '', activeTo: '',
      sortOrder: 0, active: true,
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
    const payload: CreateCmsBannerPayload = {
      placement: raw.placement.trim().toUpperCase(),
      imageUrl: raw.imageUrl.trim(),
      sortOrder: raw.sortOrder ?? 0,
      active: raw.active,
      ...(raw.title?.trim() ? { title: raw.title.trim() } : {}),
      ...(raw.altText?.trim() ? { altText: raw.altText.trim() } : {}),
      ...(raw.linkUrl?.trim() ? { linkUrl: raw.linkUrl.trim() } : {}),
      ...(raw.activeFrom ? { activeFrom: raw.activeFrom } : {}),
      ...(raw.activeTo ? { activeTo: raw.activeTo } : {}),
    };
    this.clearBanners();
    this.saving.set(true);
    this.service.createBanner(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Banner created.');
        this.showCreate.set(false);
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create that banner.');
      },
    });
  }

  protected openEdit(b: CmsBannerDto): void {
    this.clearBanners();
    this.showCreate.set(false);
    this.editingId.set(b.id);
    this.editingOriginal = b;
    this.editForm.reset({
      placement: b.placement,
      title: b.title ?? '',
      imageUrl: b.imageUrl,
      altText: b.altText ?? '',
      linkUrl: b.linkUrl ?? '',
      activeFrom: b.activeFrom ?? '',
      activeTo: b.activeTo ?? '',
      sortOrder: b.sortOrder,
      active: b.active,
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
    const payload: UpdateCmsBannerPayload = {
      placement: raw.placement.trim().toUpperCase(),
      imageUrl: raw.imageUrl.trim(),
      sortOrder: raw.sortOrder ?? 0,
      active: raw.active,
    };
    const titleTrimmed = (raw.title ?? '').trim();
    if (titleTrimmed) {
      payload.title = titleTrimmed;
    } else if (orig?.title) {
      payload.clearTitle = true;
    }
    if (raw.altText?.trim()) payload.altText = raw.altText.trim();
    const linkTrimmed = (raw.linkUrl ?? '').trim();
    if (linkTrimmed) {
      payload.linkUrl = linkTrimmed;
    } else if (orig?.linkUrl) {
      payload.clearLinkUrl = true;
    }
    if (!raw.activeFrom && !raw.activeTo && (orig?.activeFrom || orig?.activeTo)) {
      payload.clearSchedule = true;
    } else {
      if (raw.activeFrom) payload.activeFrom = raw.activeFrom;
      if (raw.activeTo) payload.activeTo = raw.activeTo;
    }

    this.clearBanners();
    this.saving.set(true);
    this.service.updateBanner(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Banner updated.');
        this.editingId.set(null);
        this.editingOriginal = null;
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that banner.');
      },
    });
  }

  protected remove(b: CmsBannerDto): void {
    if (this.deletingId() !== null) return;
    if (!confirm(`Delete banner "${b.title || b.placement}"?\n\nThis cannot be undone.`)) return;
    this.clearBanners();
    this.deletingId.set(b.id);
    this.service.deleteBanner(b.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Banner deleted.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete that banner.');
      },
    });
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
