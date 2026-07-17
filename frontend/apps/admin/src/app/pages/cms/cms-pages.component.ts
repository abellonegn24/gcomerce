import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  CmsPageDto,
  CreateCmsPagePayload,
  UpdateCmsPagePayload,
} from '../../core/cms/cms.model';
import { CmsService } from '../../core/cms/cms.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-cms-pages',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cms-pages.component.html',
  styleUrl: './cms-pages.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CmsPagesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CmsService);

  protected readonly pages = signal<CmsPageDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly saving = signal<boolean>(false);
  protected readonly deletingId = signal<number | null>(null);
  protected readonly showCreate = signal<boolean>(false);
  protected readonly editingId = signal<number | null>(null);
  private editingOriginal: CmsPageDto | null = null;
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    slug:            ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/), Validators.maxLength(120)]],
    title:           ['', [Validators.required, Validators.maxLength(200)]],
    bodyHtml:        [''],
    seoTitle:        [''],
    seoDescription:  [''],
    status:          ['DRAFT'],
    sortOrder:       [0],
  });

  protected readonly editForm = this.fb.nonNullable.group({
    slug:            ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/), Validators.maxLength(120)]],
    title:           ['', [Validators.required, Validators.maxLength(200)]],
    bodyHtml:        [''],
    seoTitle:        [''],
    seoDescription:  [''],
    status:          ['DRAFT'],
    sortOrder:       [0],
  });

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.listPages().subscribe({
      next: (rows) => {
        this.pages.set(rows);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load CMS pages.');
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.clearBanners();
    this.editingId.set(null);
    this.editingOriginal = null;
    this.form.reset({
      slug: '', title: '', bodyHtml: '',
      seoTitle: '', seoDescription: '',
      status: 'DRAFT', sortOrder: 0,
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
    const payload: CreateCmsPagePayload = {
      slug: raw.slug.trim().toLowerCase(),
      title: raw.title.trim(),
      status: raw.status,
      sortOrder: raw.sortOrder ?? 0,
      ...(raw.bodyHtml?.trim() ? { bodyHtml: raw.bodyHtml } : {}),
      ...(raw.seoTitle?.trim() ? { seoTitle: raw.seoTitle.trim() } : {}),
      ...(raw.seoDescription?.trim() ? { seoDescription: raw.seoDescription.trim() } : {}),
    };
    this.clearBanners();
    this.saving.set(true);
    this.service.createPage(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Page created.');
        this.showCreate.set(false);
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not create that page.');
      },
    });
  }

  protected openEdit(p: CmsPageDto): void {
    this.clearBanners();
    this.showCreate.set(false);
    this.editingId.set(p.id);
    this.editingOriginal = p;
    this.editForm.reset({
      slug: p.slug,
      title: p.title,
      bodyHtml: p.bodyHtml ?? '',
      seoTitle: p.seoTitle ?? '',
      seoDescription: p.seoDescription ?? '',
      status: p.status,
      sortOrder: p.sortOrder,
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
    const payload: UpdateCmsPagePayload = {
      slug: raw.slug.trim().toLowerCase(),
      title: raw.title.trim(),
      status: raw.status,
      sortOrder: raw.sortOrder ?? 0,
    };
    const bodyTrimmed = (raw.bodyHtml ?? '').trim();
    if (bodyTrimmed) {
      payload.bodyHtml = bodyTrimmed;
    } else if (orig?.bodyHtml) {
      payload.clearBody = true;
    }
    const seoTitleTrimmed = (raw.seoTitle ?? '').trim();
    if (seoTitleTrimmed) {
      payload.seoTitle = seoTitleTrimmed;
    } else if (orig?.seoTitle) {
      payload.clearSeoTitle = true;
    }
    const seoDescTrimmed = (raw.seoDescription ?? '').trim();
    if (seoDescTrimmed) {
      payload.seoDescription = seoDescTrimmed;
    } else if (orig?.seoDescription) {
      payload.clearSeoDescription = true;
    }

    this.clearBanners();
    this.saving.set(true);
    this.service.updatePage(id, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.successMessage.set('Page updated.');
        this.editingId.set(null);
        this.editingOriginal = null;
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.saving.set(false);
        this.errorMessage.set(err.message || 'Could not update that page.');
      },
    });
  }

  protected remove(p: CmsPageDto): void {
    if (this.deletingId() !== null) return;
    if (!confirm(`Delete page "${p.title}" (${p.slug})?\n\nThis cannot be undone.`)) return;
    this.clearBanners();
    this.deletingId.set(p.id);
    this.service.deletePage(p.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.successMessage.set('Page deleted.');
        this.reload();
      },
      error: (err: NormalizedApiError) => {
        this.deletingId.set(null);
        this.errorMessage.set(err.message || 'Could not delete that page.');
      },
    });
  }

  protected statusBadge(status: string): string {
    return status === 'PUBLISHED' ? 'gi-badge--ok' : 'gi-badge--warn';
  }

  private clearBanners(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
}
