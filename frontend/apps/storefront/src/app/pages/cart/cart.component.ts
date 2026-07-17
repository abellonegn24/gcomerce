import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { CartService } from '../../core/cart/cart.service';
import { AuthService } from '../../core/auth/auth.service';
import { QuoteService } from '../../core/quotes/quote.service';
import { QuoteDetail } from '../../core/quotes/quote.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';
import { CartLine } from '../../core/cart/cart.model';

/**
 * /cart — full shopping-cart page. Renders whatever CartService has cached
 * (populated by the reload effect on sign-in) and drives mutations through
 * the same service. Every mutation snapshots the fresh response into the
 * store so re-renders happen without an extra GET.
 */
@Component({
  selector: 'gi-cart',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartComponent implements OnInit {
  private readonly cartService = inject(CartService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly quoteService = inject(QuoteService);

  protected readonly cart = this.cartService.cart;
  protected readonly loading = this.cartService.loading;
  protected readonly subtotal = this.cartService.subtotal;

  /** Rows currently being mutated — keyed by productId:variantId to distinguish variant lines. */
  protected readonly busyKeys = signal<ReadonlySet<string>>(new Set());

  protected readonly errorMessage = signal<string | null>(null);
  protected readonly quoteLoading = signal<boolean>(false);
  protected readonly quoteSuccess = signal<string | null>(null);

  /** Whether the Request Quote button should be shown — B2B only, authed, non-empty cart. */
  protected readonly canRequestQuote = computed<boolean>(() => {
    const c = this.cart();
    return this.auth.isAuthenticated() && c !== null && c.items.length > 0;
  });

  protected readonly isEmpty = computed<boolean>(() => {
    const c = this.cart();
    return !this.loading() && (c === null || c.items.length === 0);
  });

  ngOnInit(): void {
    this.cartService.reload().subscribe({
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load your cart.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Mutations
  // ---------------------------------------------------------------------------

  protected increment(line: CartLine): void {
    this.setQuantity(line, Number(line.quantity) + 1);
  }

  protected decrement(line: CartLine): void {
    const next = Number(line.quantity) - 1;
    if (next <= 0) {
      this.remove(line);
      return;
    }
    this.setQuantity(line, next);
  }

  protected remove(line: CartLine): void {
    const key = this.lineKey(line);
    this.markBusy(key, true);
    this.errorMessage.set(null);
    this.cartService.removeItem(line.productId, line.variantId ?? undefined).subscribe({
      next: () => this.markBusy(key, false),
      error: (err: NormalizedApiError) => {
        this.markBusy(key, false);
        this.errorMessage.set(err.message || 'Could not update that line.');
      },
    });
  }

  protected clearAll(): void {
    if (!confirm('Empty your cart? This cannot be undone.')) return;
    this.errorMessage.set(null);
    this.cartService.clearCart().subscribe({
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not clear your cart.');
      },
    });
  }

  protected goToCheckout(): void {
    if (!this.auth.isAuthenticated()) {
      void this.router.navigate(['/login'], {
        queryParams: { returnUrl: '/checkout' },
      });
      return;
    }
    void this.router.navigateByUrl('/checkout');
  }

  protected requestQuote(): void {
    this.quoteLoading.set(true);
    this.quoteSuccess.set(null);
    this.errorMessage.set(null);
    this.quoteService.create({}).subscribe({
      next: (quote: QuoteDetail) => {
        this.quoteLoading.set(false);
        this.quoteSuccess.set(quote.uuid);
        void this.router.navigate(['/account/quotes', quote.uuid]);
      },
      error: (err: NormalizedApiError) => {
        this.quoteLoading.set(false);
        this.errorMessage.set(err.message || 'Could not create a quote request.');
      },
    });
  }

  // ---------------------------------------------------------------------------

  private setQuantity(line: CartLine, quantity: number): void {
    const key = this.lineKey(line);
    this.markBusy(key, true);
    this.errorMessage.set(null);
    this.cartService
      .updateItem(line.productId, { quantity }, line.variantId ?? undefined)
      .subscribe({
        next: () => this.markBusy(key, false),
        error: (err: NormalizedApiError) => {
          this.markBusy(key, false);
          // Server returns structured codes: INSUFFICIENT_STOCK, MIN_ORDER_QUANTITY,
          // PRODUCT_NOT_PURCHASABLE — surface the message verbatim.
          this.errorMessage.set(err.message || 'Could not update that line.');
        },
      });
  }

  private lineKey(line: CartLine): string {
    return `${line.productId}:${line.variantId ?? 'null'}`;
  }

  private markBusy(key: string, busy: boolean): void {
    const next = new Set(this.busyKeys());
    if (busy) next.add(key);
    else next.delete(key);
    this.busyKeys.set(next);
  }

  protected isBusy(line: CartLine): boolean {
    return this.busyKeys().has(this.lineKey(line));
  }

  // ---------------------------------------------------------------------------
  // Formatting helpers
  // ---------------------------------------------------------------------------

  protected formatMoney(currency: string, amount: string | number): string {
    const value = typeof amount === 'string' ? Number(amount) : amount;
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
