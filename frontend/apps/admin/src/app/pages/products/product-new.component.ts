import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { ProductsService } from '../../core/catalog/products.service';
import {
  CreateProductPayload,
  UploadResponse,
  UpdateProductPayload,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

import { ProductFormComponent } from './product-form.component';

/**
 * /products/new — thin wrapper around {@link ProductFormComponent} that
 * calls {@link ProductsService.create} on submit and routes to the new
 * product's edit page on success.
 */
@Component({
  selector: 'gi-admin-product-new',
  standalone: true,
  imports: [CommonModule, RouterLink, ProductFormComponent],
  template: `
    <section class="gi-pnew">
      <nav class="gi-pnew__crumbs" aria-label="Breadcrumb">
        <a routerLink="/products">
          <span class="material-icons-round">arrow_back</span>
          Back to products
        </a>
      </nav>

      <header class="gi-pnew__head">
        <div>
          <p class="gi-pnew__eyebrow">New product</p>
          <h1>Create a product</h1>
          <p class="gi-pnew__sub">
            Once created you'll be taken to the editor where you can add media,
            adjust inventory, and set wholesale price tiers.
          </p>
        </div>
      </header>

      <gi-admin-product-form
        [mode]="'create'"
        [submitting]="submitting()"
        [errorMessage]="errorMessage()"
        (save)="onSave($event)"
        (imageChange)="onImageChange($event)"
        (cancel)="onCancel()" />
    </section>
  `,
  styles: [`
    :host { display: block; }

    .gi-pnew {
      padding: 1.5rem 2rem 3rem;
      max-width: 1100px;
      margin: 0 auto;
      color: var(--gi-navy-900);

      @media (max-width: 720px) { padding: 1rem; }
    }

    .gi-pnew__crumbs {
      margin-bottom: 1rem;

      a {
        display: inline-flex; align-items: center; gap: 0.35rem;
        color: var(--gi-navy-700);
        text-decoration: none;
        font-size: 0.9rem;

        &:hover { color: var(--gi-navy-900); text-decoration: underline; }
        .material-icons-round { font-size: 1rem; }
      }
    }

    .gi-pnew__head {
      padding-bottom: 1rem;
      border-bottom: 1px solid var(--gi-border);
      margin-bottom: 1.5rem;

      h1 { margin: 0; font-family: var(--gi-font-display); font-size: 1.5rem; color: var(--gi-navy-900); }
    }

    .gi-pnew__eyebrow {
      color: var(--gi-gold-700);
      font-size: 0.72rem;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      margin: 0 0 0.25rem 0;
      font-weight: 700;
    }

    .gi-pnew__sub {
      color: var(--gi-text-secondary);
      margin: 0.5rem 0 0 0;
      font-size: 0.9rem;
      line-height: 1.5;
      max-width: 68ch;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductNewComponent {
  private readonly router = inject(Router);
  private readonly service = inject(ProductsService);

  protected readonly submitting = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  private pendingImage: UploadResponse | null = null;

  protected onImageChange(img: UploadResponse | null): void {
    this.pendingImage = img;
  }

  protected onSave(payload: CreateProductPayload | UpdateProductPayload): void {
    // In "create" mode the form always emits a CreateProductPayload.
    this.errorMessage.set(null);
    this.submitting.set(true);
    this.service.create(payload as CreateProductPayload).subscribe({
      next: (created) => {
        if (this.pendingImage) {
          this.attachImage(created.id, this.pendingImage);
        } else {
          this.submitting.set(false);
          void this.router.navigate(['/products', created.id], { queryParams: { created: '1' } });
        }
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(err.message || 'Could not create that product.');
      },
    });
  }

  private attachImage(productId: number, img: UploadResponse): void {
    const isVideo = img.contentType.startsWith('video/');
    this.service
      .addMedia(productId, {
        url: img.url,
        mediaType: isVideo ? 'VIDEO' : 'IMAGE',
        isPrimary: true,
        sortOrder: 0,
        cardThumbnailUrl: img.cardThumbnailUrl,
        detailThumbnailUrl: img.detailThumbnailUrl,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          void this.router.navigate(['/products', productId], { queryParams: { created: '1' } });
        },
        error: (err: NormalizedApiError) => {
          // Product was created but media attachment failed — still navigate
          // to the editor so the user can retry from the Media tab.
          this.submitting.set(false);
          void this.router.navigate(['/products', productId], {
            queryParams: { created: '1', mediaError: err.message || 'Image upload failed to attach.' },
          });
        },
      });
  }

  protected onCancel(): void {
    void this.router.navigateByUrl('/products');
  }
}
