import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from './core/auth/auth.service';
import { CartService } from './core/cart/cart.service';
import { WishlistService } from './core/wishlist/wishlist.service';
import { I18nService } from './core/i18n/i18n.service';
import { LanguageSwitcherComponent } from './shared/language-switcher/language-switcher.component';

@Component({
  selector: 'gi-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, RouterLink, LanguageSwitcherComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);
  private readonly wishlist = inject(WishlistService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);

  protected readonly currency = this.i18n.currency;
  /** Backed by CartService — refreshes automatically on auth + cart mutations. */
  protected readonly cartCount = this.cart.itemCount;
  protected readonly wishlistCount = this.wishlist.count;

  protected readonly year = new Date().getFullYear();

  protected readonly categories = [
    { slug: 'raw-coffee', label: 'Raw Coffee' },
    { slug: 'roasted-coffee', label: 'Roasted Coffee' },
    { slug: 'oil-seed', label: 'Oil Seed' },
    { slug: 'livestock', label: 'Livestock' },
    { slug: 'mining', label: 'Mining' },
    { slug: 'white-products', label: 'White Products' },
    { slug: 'spicy-products', label: 'Spicy Products' },
  ];

  // Auth-driven UI state
  protected readonly isAuthenticated = this.auth.isAuthenticated;
  protected readonly customer = this.auth.currentCustomer;
  protected readonly greetingName = computed<string>(() => {
    const c = this.customer();
    if (!c) return '';
    if (c.companyName && c.companyName.trim().length > 0) return c.companyName;
    return (c.firstName ?? '').trim() || c.email;
  });

  protected readonly searchQuery = signal<string>('');

  protected submitSearch(): void {
    const q = this.searchQuery().trim();
    if (q) {
      void this.router.navigate(['/shop'], { queryParams: { q } });
    } else {
      void this.router.navigate(['/shop']);
    }
  }

  setCurrency(code: 'USD' | 'ETB'): void {
    this.i18n.setCurrency(code);
  }

  signOut(): void {
    this.auth.logout().subscribe({
      complete: () => {
        void this.router.navigateByUrl('/');
      },
    });
  }
}
