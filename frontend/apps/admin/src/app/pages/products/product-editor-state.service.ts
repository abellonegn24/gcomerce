import { Injectable, computed, inject, signal } from '@angular/core';

import { ProductsService } from '../../core/catalog/products.service';
import {
  ProductDetail,
  ProductMedia,
  UpdateProductPayload,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * Scoped state store for the {@code /products/:id} shell.
 *
 * <p>Not declared with {@code providedIn: 'root'} — the shell component
 * lists it in its own {@code providers: []} array so we get one instance
 * per editor session. Navigating away and back tears the service down and
 * refetches the product, which is exactly what we want (the alternative,
 * a global singleton, would keep stale data around for the next product).
 *
 * <p>Children (details / media / inventory / pricing tabs) inject this
 * service to read the current product signal and drive mutations. That way
 * a deep-link to {@code /products/:id/media} still gets the product loaded
 * before the tab renders — the shell owns the fetch.
 */
@Injectable()
export class ProductEditorStateService {
  private readonly service = inject(ProductsService);

  private readonly _product = signal<ProductDetail | null>(null);
  private readonly _loading = signal<boolean>(true);
  private readonly _saving = signal<boolean>(false);
  private readonly _archiving = signal<boolean>(false);
  private readonly _errorMessage = signal<string | null>(null);
  private readonly _successMessage = signal<string | null>(null);

  readonly product = this._product.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly archiving = this._archiving.asReadonly();
  readonly errorMessage = this._errorMessage.asReadonly();
  readonly successMessage = this._successMessage.asReadonly();

  readonly productId = computed<number | null>(() => this._product()?.id ?? null);

  /** Fetch (or re-fetch) the product with the given id. */
  load(id: number): void {
    this._loading.set(true);
    this._errorMessage.set(null);
    this.service.get(id).subscribe({
      next: (p) => {
        this._product.set(p);
        this._loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this._errorMessage.set(err.message || 'Could not load that product.');
        this._loading.set(false);
      },
    });
  }

  /** Clear the current success/error banners — call on tab change. */
  clearBanners(): void {
    this._errorMessage.set(null);
    this._successMessage.set(null);
  }

  /**
   * Persist the "Details" tab payload. Success updates the product signal so
   * every other tab sees the new data on next render.
   */
  saveDetails(payload: UpdateProductPayload): void {
    const p = this._product();
    if (!p) return;
    this._saving.set(true);
    this._errorMessage.set(null);
    this._successMessage.set(null);
    this.service.update(p.id, payload).subscribe({
      next: (updated) => {
        this._saving.set(false);
        this._product.set(updated);
        this._successMessage.set('Product saved.');
      },
      error: (err: NormalizedApiError) => {
        this._saving.set(false);
        this._errorMessage.set(err.message || 'Could not save the product.');
      },
    });
  }

  /**
   * Patch the media array on the current product without re-fetching. The
   * media tab already emits the fresh list after every mutation.
   */
  patchMedia(list: ProductMedia[]): void {
    const p = this._product();
    if (!p) return;
    this._product.set({ ...p, media: list });
  }

  /**
   * Archive the product. Callers (usually the shell) navigate away on
   * success — this service does not know about the Router.
   */
  archive(onDone: (ok: boolean, err?: string) => void): void {
    const p = this._product();
    if (!p) return;
    this._errorMessage.set(null);
    this._successMessage.set(null);
    this._archiving.set(true);
    this.service.archive(p.id).subscribe({
      next: () => {
        this._archiving.set(false);
        onDone(true);
      },
      error: (err: NormalizedApiError) => {
        this._archiving.set(false);
        const msg = err.message || 'Could not archive the product.';
        this._errorMessage.set(msg);
        onDone(false, msg);
      },
    });
  }
}
