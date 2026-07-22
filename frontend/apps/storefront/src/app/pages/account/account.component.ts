import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Signed-in customer portal shell. Provides a vertical tab rail and delegates
 * to child routes (/account, /account/addresses, /account/orders).
 */
@Component({
  selector: 'gi-account',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './account.component.html',
  styleUrl: './account.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountComponent {
  private readonly auth = inject(AuthService);

  protected readonly customer = this.auth.currentCustomer;

  private readonly isTransactionViewer = computed<boolean>(() => {
    const c = this.customer();
    if (!c) return false;
    return c.email === 'admin@gplus.com';
  });

  protected readonly tabs = computed(() => {
    const base = [
      { path: '/account',           exact: true,  icon: 'person',        label: 'Profile' },
      { path: '/account/addresses', exact: false, icon: 'place',         label: 'Addresses' },
      { path: '/account/orders',    exact: false, icon: 'shopping_bag',  label: 'Orders' },
    ];
    if (this.isTransactionViewer()) {
      base.push({ path: '/account/transactions', exact: false, icon: 'receipt_long', label: 'Transactions' });
    }
    base.push({ path: '/account/quotes', exact: false, icon: 'request_quote', label: 'Quotes' });
    return base;
  });
}
