import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AdminAuthService } from './core/auth/admin-auth.service';
import { NotificationBellComponent } from './shared/notification-bell/notification-bell.component';

interface NavItem {
  label: string;
  path: string;
  icon: string;
  /** Optional — hide the link unless the current admin has one of these roles. */
  roles?: string[];
}

@Component({
  selector: 'gi-admin-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, NotificationBellComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);

  protected readonly admin = this.auth.currentAdmin;
  protected readonly isAuthenticated = this.auth.isAuthenticated;

  /** Primary sidebar nav. */
  protected readonly nav: NavItem[] = [
    { label: 'Dashboard',   path: '/dashboard',   icon: 'dashboard' },
    { label: 'Products',         path: '/products',         icon: 'inventory_2' },
    { label: 'Price lists',      path: '/price-lists',      icon: 'sell' },
    { label: 'Shipping methods', path: '/shipping-methods', icon: 'local_shipping' },
    { label: 'Orders',           path: '/orders',           icon: 'shopping_bag' },
    { label: 'Quotes',           path: '/quotes',           icon: 'request_quote' },
    { label: 'Customers',        path: '/customers',        icon: 'group' },
    { label: 'Customer groups',  path: '/customer-groups',  icon: 'group_work' },
    { label: 'Coupons',          path: '/coupons',          icon: 'confirmation_number' },
    { label: 'CMS pages',        path: '/cms/pages',        icon: 'article' },
    { label: 'CMS banners',      path: '/cms/banners',      icon: 'view_carousel' },
    { label: 'Admin users', path: '/admin-users', icon: 'admin_panel_settings' },
  ];

  protected canSee(item: NavItem): boolean {
    if (!item.roles || item.roles.length === 0) return true;
    return item.roles.some((r) => this.auth.hasRole(r));
  }

  protected signOut(): void {
    this.auth.logout().subscribe({
      complete: () => {
        void this.router.navigateByUrl('/login');
      },
    });
  }
}
