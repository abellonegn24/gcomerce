import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { mfaChallengeGuard } from './core/guards/mfa-challenge.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./pages/login/login.component').then((m) => m.LoginComponent),
    title: 'Sign in — Gadaa Istar Admin',
  },
  {
    path: 'verify-mfa',
    canActivate: [mfaChallengeGuard],
    loadComponent: () =>
      import('./pages/verify-mfa/verify-mfa.component').then((m) => m.VerifyMfaComponent),
    title: 'Two-factor sign-in — Gadaa Istar Admin',
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    title: 'Dashboard — Gadaa Istar Admin',
  },
  {
    path: 'admin-users',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/admin-users/admin-users.component').then((m) => m.AdminUsersComponent),
    title: 'Admin users — Gadaa Istar Admin',
  },
  {
    path: 'products',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/products/products-list.component').then((m) => m.ProductsListComponent),
    title: 'Products — Gadaa Istar Admin',
  },
  {
    path: 'products/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/products/product-new.component').then((m) => m.ProductNewComponent),
    title: 'New product — Gadaa Istar Admin',
  },
  {
    path: 'products/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/products/product-editor.component').then((m) => m.ProductEditorComponent),
    title: 'Edit product — Gadaa Istar Admin',
    children: [
      // Deep-link parity: /products/:id lands on the details tab so old
      // bookmarks + list-page row clicks keep working.
      { path: '', pathMatch: 'full', redirectTo: 'details' },
      {
        path: 'details',
        loadComponent: () =>
          import('./pages/products/tabs/product-details-tab.component').then((m) => m.ProductDetailsTabComponent),
      },
      {
        path: 'media',
        loadComponent: () =>
          import('./pages/products/tabs/product-media-tab.component').then((m) => m.ProductMediaTabComponent),
      },
      {
        path: 'inventory',
        loadComponent: () =>
          import('./pages/products/tabs/product-inventory-tab.component').then((m) => m.ProductInventoryTabComponent),
      },
      {
        path: 'pricing',
        loadComponent: () =>
          import('./pages/products/tabs/product-pricing-tab.component').then((m) => m.ProductPricingTabComponent),
      },
      {
        path: 'variants',
        loadComponent: () =>
          import('./pages/products/tabs/product-variants-tab.component').then((m) => m.ProductVariantsTabComponent),
      },
    ],
  },
  {
    path: 'price-lists',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/price-lists/price-lists.component').then((m) => m.PriceListsComponent),
    title: 'Price lists — Gadaa Istar Admin',
  },
  {
    path: 'shipping-methods',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/shipping-methods/shipping-methods.component').then((m) => m.ShippingMethodsComponent),
    title: 'Shipping methods — Gadaa Istar Admin',
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/orders/orders-list.component').then((m) => m.OrdersListComponent),
    title: 'Orders — Gadaa Istar Admin',
  },
  {
    path: 'orders/:uuid',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/orders/order-detail.component').then((m) => m.OrderDetailComponent),
    title: 'Order — Gadaa Istar Admin',
  },
  {
    path: 'quotes',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/quotes/quotes-list.component').then((m) => m.QuotesListComponent),
    title: 'Quotes — Gadaa Istar Admin',
  },
  {
    path: 'quotes/:uuid',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/quotes/quote-detail.component').then((m) => m.QuoteDetailComponent),
    title: 'Quote — Gadaa Istar Admin',
  },
  {
    path: 'customers',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/customers/customers-list.component').then((m) => m.CustomersListComponent),
    title: 'Customers — Gadaa Istar Admin',
  },
  {
    path: 'customers/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/customers/customer-detail.component').then((m) => m.CustomerDetailComponent),
    title: 'Customer — Gadaa Istar Admin',
  },
  {
    path: 'customer-groups',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/customer-groups/customer-groups.component').then((m) => m.CustomerGroupsComponent),
    title: 'Customer groups — Gadaa Istar Admin',
  },
  {
    path: 'coupons',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/coupons/coupons.component').then((m) => m.CouponsComponent),
    title: 'Coupons — Gadaa Istar Admin',
  },
  {
    path: 'cms/pages',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/cms/cms-pages.component').then((m) => m.CmsPagesComponent),
    title: 'CMS pages — Gadaa Istar Admin',
  },
  {
    path: 'cms/banners',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/cms/cms-banners.component').then((m) => m.CmsBannersComponent),
    title: 'CMS banners — Gadaa Istar Admin',
  },
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
