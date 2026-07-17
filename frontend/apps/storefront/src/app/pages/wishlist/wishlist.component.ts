import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ProductDetail } from '../../core/catalog/catalog.model';
import { CatalogService } from '../../core/catalog/catalog.service';
import { CartService } from '../../core/cart/cart.service';
import { WishlistService } from '../../core/wishlist/wishlist.service';

/**
 * /wishlist — shows all saved products with add-to-cart and remove actions.
 * Data is fetched from the catalog API by slug on init.
 */
@Component({
  selector: 'gi-wishlist',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './wishlist.component.html',
  styleUrl: './wishlist.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WishlistComponent {
  private readonly wishlist = inject(WishlistService);
  private readonly catalog = inject(CatalogService);
  private readonly cart = inject(CartService);

  protected readonly slugs = this.wishlist.slugs;
  protected readonly products = signal<ProductDetail[]>([]);
  protected readonly loading = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly addingSlug = signal<string | null>(null);

  protected readonly hasItems = computed<boolean>(() => this.slugs().length > 0);

  constructor() {
    this.loadProducts();
  }

  protected loadProducts(): void {
    const slugs = this.slugs();
    if (slugs.length === 0) {
      this.products.set([]);
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    Promise.all(
      slugs.map((slug) =>
        this.catalog.getBySlug(slug).toPromise().catch(() => null),
      ),
    )
      .then((results) => {
        this.products.set(results.filter((r): r is ProductDetail => r !== null));
        this.loading.set(false);
      })
      .catch(() => {
        this.error.set('Could not load some wishlist items. Please try again.');
        this.loading.set(false);
      });
  }

  protected remove(slug: string): void {
    this.wishlist.remove(slug);
    this.products.update((list) => list.filter((_, i) => this.slugs()[i] !== slug));
  }

  protected addToCart(product: ProductDetail): void {
    if (!product.inStock) return;
    this.addingSlug.set(product.slug);
    const qty = Number(product.minOrderQuantity) || 1;
    this.cart
      .addItem({
        productId: product.id,
        quantity: qty,
      })
      .subscribe({
      next: () => {
        this.addingSlug.set(null);
      },
      error: () => {
        this.addingSlug.set(null);
      },
    });
  }

  protected formatMoney(currency: string, amount: string): string {
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
