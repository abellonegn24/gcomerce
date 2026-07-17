import { Injectable, computed, signal } from '@angular/core';

/**
 * Wishlist / save-for-later — client-side only, persisted to localStorage.
 * Stores product slugs so the wishlist page can fetch full product details
 * via CatalogService.getBySlug().
 */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private static readonly STORAGE_KEY = 'gi-wishlist';

  private readonly _slugs = signal<readonly string[]>(this.loadFromStorage());

  /** Reactive list of saved product slugs. */
  readonly slugs = this._slugs.asReadonly();

  /** Count of saved items — useful for header badge. */
  readonly count = computed<number>(() => this._slugs().length);

  /** Check if a product slug is in the wishlist. */
  has(slug: string): boolean {
    return this._slugs().includes(slug);
  }

  /** Add a product slug to the wishlist (no-op if already present). */
  add(slug: string): void {
    if (!slug || this.has(slug)) return;
    this._slugs.update((list) => [...list, slug]);
    this.persist();
  }

  /** Remove a product slug from the wishlist. */
  remove(slug: string): void {
    this._slugs.update((list) => list.filter((s) => s !== slug));
    this.persist();
  }

  /** Toggle a product slug in/out of the wishlist. Returns true if added. */
  toggle(slug: string): boolean {
    if (this.has(slug)) {
      this.remove(slug);
      return false;
    }
    this.add(slug);
    return true;
  }

  /** Clear all saved items. */
  clear(): void {
    this._slugs.set([]);
    this.persist();
  }

  private loadFromStorage(): string[] {
    if (typeof localStorage === 'undefined') return [];
    try {
      const raw = localStorage.getItem(WishlistService.STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.filter((s) => typeof s === 'string') : [];
    } catch {
      return [];
    }
  }

  private persist(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(WishlistService.STORAGE_KEY, JSON.stringify(this._slugs()));
    } catch {
      // Ignore quota / privacy mode errors.
    }
  }
}
