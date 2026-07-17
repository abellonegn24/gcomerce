import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, map, skip } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { FormsModule } from '@angular/forms';

import { CatalogService } from '../../core/catalog/catalog.service';
import {
  CATEGORIES,
  CategoryMeta,
  ProductCard,
  categoryBySlug,
} from '../../core/catalog/catalog.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

const PAGE_SIZE = 12;

@Component({
  selector: 'gi-shop-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './shop-list.component.html',
  styleUrl: './shop-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShopListComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalog = inject(CatalogService);

  protected readonly categories = CATEGORIES;

  /** Category selected via `/shop/:category` URL param — null on `/shop`. */
  private readonly activeCategory = toSignal<CategoryMeta | null>(
    this.route.paramMap.pipe(map((p) => categoryBySlug(p.get('category'))))
  );

  protected readonly activeSlug = computed<string | null>(
    () => this.activeCategory()?.slug ?? null
  );
  protected readonly heading = computed<string>(() => {
    if (this.isSearching()) return 'Search results';
    const c = this.activeCategory();
    return c ? c.label : 'Shop the collection';
  });
  protected readonly blurb = computed<string>(() => {
    const c = this.activeCategory();
    return c
      ? c.blurb
      : 'Direct-from-origin Ethiopian coffee, honey, and spices — hand-selected for the world.';
  });

  protected readonly items = signal<ProductCard[]>([]);
  protected readonly page = signal<number>(0);
  protected readonly total = signal<number>(0);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);

  // --- Search ---
  protected readonly searchTerm = signal<string>('');
  protected readonly isSearching = computed<boolean>(() => this.searchTerm().trim().length > 0);
  private readonly searchInput$ = new Subject<string>();

  protected readonly pageCount = computed<number>(() =>
    Math.max(1, Math.ceil(this.total() / PAGE_SIZE))
  );
  protected readonly hasNext = computed<boolean>(() => this.page() + 1 < this.pageCount());
  protected readonly hasPrev = computed<boolean>(() => this.page() > 0);

  ngOnInit(): void {
    // Reload the list whenever the category URL param changes.
    this.route.paramMap.subscribe(() => {
      this.page.set(0);
      // Don't reset search if we have a query param (e.g. from header search).
      const queryQ = this.route.snapshot.queryParamMap.get('q');
      if (queryQ && queryQ.trim()) {
        this.searchTerm.set(queryQ.trim());
      } else {
        this.searchTerm.set('');
      }
      this.load();
    });

    // Also react to query param changes (e.g. header search -> /shop?q=...).
    this.route.queryParamMap.subscribe((qp) => {
      const q = qp.get('q');
      if (q !== null) {
        const trimmed = q.trim();
        if (trimmed !== this.searchTerm().trim()) {
          this.searchTerm.set(trimmed);
          this.page.set(0);
          this.load();
        }
      }
    });

    // Debounced search — 300ms after the user stops typing.
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), skip(1))
      .subscribe((q) => {
        this.searchTerm.set(q);
        this.page.set(0);
        this.load();
      });
  }

  protected load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const q = this.searchTerm().trim();
    const opts = {
      category: this.activeCategory()?.slug ?? null,
      page: this.page(),
      size: PAGE_SIZE,
    };

    const source$ = q
      ? this.catalog.search({ q, ...opts })
      : this.catalog.list(opts);

    source$.subscribe({
      next: (res) => {
        this.items.set(res.page.data);
        this.total.set(res.page.meta.total);
        this.loading.set(false);
        if (typeof window !== 'undefined') {
          window.scrollTo({ top: 0, behavior: 'smooth' });
        }
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load the catalog. Please try again.');
        this.loading.set(false);
      },
    });
  }

  protected onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  protected clearSearch(): void {
    this.searchTerm.set('');
    this.page.set(0);
    this.load();
  }

  protected nextPage(): void {
    if (!this.hasNext()) return;
    this.page.update((p) => p + 1);
    this.load();
  }

  protected prevPage(): void {
    if (!this.hasPrev()) return;
    this.page.update((p) => Math.max(0, p - 1));
    this.load();
  }

  protected trackBySlug(_: number, item: ProductCard): string {
    return item.slug;
  }

  protected navigateToCategory(slug: string | null): void {
    if (slug === null) {
      void this.router.navigate(['/shop']);
    } else {
      void this.router.navigate(['/shop', slug]);
    }
  }

  protected formatPrice(currency: string, amount: string): string {
    const value = Number(amount);
    if (!isFinite(value)) return `${currency} ${amount}`;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }
}
