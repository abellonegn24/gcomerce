import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { CatalogService } from '../../core/catalog/catalog.service';
import { ProductDetail, VariantSummary, categoryByCode } from '../../core/catalog/catalog.model';
import { WishlistService } from '../../core/wishlist/wishlist.service';
import { SeoService } from '../../core/seo/seo.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly catalog = inject(CatalogService);
  private readonly cart = inject(CartService);
  private readonly auth = inject(AuthService);
  private readonly wishlist = inject(WishlistService);
  private readonly seo = inject(SeoService);
  private readonly document = inject(DOCUMENT);

  protected readonly product = signal<ProductDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly notFound = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** Currently-viewed image URL in the gallery (defaults to primary). */
  protected readonly activeImage = signal<string | null>(null);

  /** Selected quantity for the add-to-cart action. Respects MOQ. */
  protected readonly quantity = signal<number>(1);

  /** Selected variant id (null = no variant / product-level). */
  protected readonly selectedVariantId = signal<number | null>(null);

  /** True while a POST /cart/items is in flight. */
  protected readonly adding = signal<boolean>(false);

  /** Success flash after add-to-cart. Cleared automatically after a few seconds. */
  protected readonly addSuccess = signal<boolean>(false);

  protected readonly isAuthenticated = this.auth.isAuthenticated;

  protected readonly inWishlist = computed<boolean>(() => {
    const p = this.product();
    return p ? this.wishlist.has(p.slug) : false;
  });

  protected readonly categoryMeta = computed(() => {
    const p = this.product();
    return p ? categoryByCode(p.category) : null;
  });

  protected readonly hasVariants = computed<boolean>(() => {
    const p = this.product();
    return p != null && p.variants != null && p.variants.length > 0;
  });

  protected readonly selectedVariant = computed<VariantSummary | null>(() => {
    const p = this.product();
    if (!p || !p.variants) return null;
    const id = this.selectedVariantId();
    if (id == null) return null;
    return p.variants.find(v => v.id === id) ?? null;
  });

  /** Effective price: variant price override takes precedence, then display price. */
  protected readonly effectivePrice = computed<string>(() => {
    const p = this.product();
    if (!p) return '0';
    const v = this.selectedVariant();
    if (v && v.priceOverride) return v.priceOverride;
    return p.displayPrice;
  });

  /** Effective stock: variant stock when selected, product stock otherwise. */
  protected readonly effectiveInStock = computed<boolean>(() => {
    const p = this.product();
    if (!p) return false;
    const v = this.selectedVariant();
    if (v) return v.inStock;
    return p.inStock;
  });

  protected readonly effectiveAvailableQty = computed<string>(() => {
    const p = this.product();
    if (!p) return '0';
    const v = this.selectedVariant();
    if (v) return v.availableQuantity;
    return p.availableQuantity;
  });

  protected readonly hasSale = computed<boolean>(() => {
    const p = this.product();
    if (!p) return false;
    // When a variant with price override is selected, sale logic doesn't apply.
    const v = this.selectedVariant();
    if (v && v.priceOverride) return false;
    return Number(p.discountPercent) > 0;
  });

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slug = params.get('slug');
      if (!slug) {
        this.notFound.set(true);
        this.loading.set(false);
        return;
      }
      this.load(slug);
    });
  }

  private load(slug: string): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.errorMessage.set(null);
    this.product.set(null);
    this.activeImage.set(null);

    this.catalog.getBySlug(slug).subscribe({
      next: (p) => {
        this.product.set(p);
        this.activeImage.set(p.detailThumbnailUrl ?? p.primaryImageUrl ?? p.mediaUrls[0] ?? null);
        // MOQ may be fractional (e.g. 0.5 kg); UI selector uses integer steps
        // so we round up to the smallest integer >= MOQ.
        const moq = Math.max(1, Math.ceil(Number(p.minOrderQuantity) || 1));
        this.quantity.set(moq);
        // Auto-select the first active variant if the product has variants.
        if (p.variants && p.variants.length > 0) {
          this.selectedVariantId.set(p.variants[0].id);
        } else {
          this.selectedVariantId.set(null);
        }
        this.loading.set(false);
        this.seo.set({
          title: `${p.name} — Gadaa Istar`,
          description: p.shortDescription || p.description?.slice(0, 160) || undefined,
          canonicalUrl: `${this.document.location.origin}/product/${slug}`,
          ogType: 'product',
          ogImage: p.primaryImageUrl || undefined,
          jsonLd: {
            '@context': 'https://schema.org',
            '@type': 'Product',
            name: p.name,
            description: p.shortDescription || undefined,
            image: p.primaryImageUrl || undefined,
            sku: p.sku,
            offers: {
              '@type': 'Offer',
              price: p.displayPrice,
              priceCurrency: p.currency || 'USD',
              availability: p.inStock ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
            },
          },
        });
      },
      error: (err: NormalizedApiError) => {
        if (err.status === 404) {
          this.notFound.set(true);
        } else {
          this.errorMessage.set(err.message || 'Could not load this product.');
        }
        this.loading.set(false);
      },
    });
  }

  protected selectImage(url: string): void {
    this.activeImage.set(url);
  }

  protected incrementQty(): void {
    this.quantity.update((q) => q + 1);
  }

  protected decrementQty(): void {
    const p = this.product();
    if (!p) return;
    const moq = Math.max(1, Math.ceil(Number(p.minOrderQuantity) || 1));
    this.quantity.update((q) => Math.max(moq, q - 1));
  }

  protected selectVariant(variantId: number): void {
    this.selectedVariantId.set(variantId);
  }

  protected addToCart(): void {
    const p = this.product();
    if (!p) return;

    // If the product has variants, require a selection.
    if (this.hasVariants() && this.selectedVariantId() == null) {
      this.errorMessage.set('Please select a variant first.');
      return;
    }

    if (!this.effectiveInStock()) return;

    // Guests can add to cart — CartService routes to /guest-cart endpoints
    // automatically when not authenticated.

    this.errorMessage.set(null);
    this.addSuccess.set(false);
    this.adding.set(true);

    this.cart.addItem({
      productId: p.id,
      variantId: this.selectedVariantId() ?? undefined,
      quantity: this.quantity(),
    }).subscribe({
      next: () => {
        this.adding.set(false);
        this.addSuccess.set(true);
        // Clear the success flash after 3s so the CTA returns to its idle state.
        setTimeout(() => this.addSuccess.set(false), 3000);
      },
      error: (err: NormalizedApiError) => {
        this.adding.set(false);
        // Structured backend codes (INSUFFICIENT_STOCK, MIN_ORDER_QUANTITY, etc.)
        // travel in `message`; surface verbatim.
        this.errorMessage.set(err.message || 'Could not add this to your cart.');
      },
    });
  }

  protected toggleWishlist(): void {
    const p = this.product();
    if (!p) return;
    this.wishlist.toggle(p.slug);
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
