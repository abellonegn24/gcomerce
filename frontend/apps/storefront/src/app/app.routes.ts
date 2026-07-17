import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/home/home.component').then((m) => m.HomeComponent),
    title: 'Gadaa Istar — Ethiopian Coffee & Specialty Exports',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./pages/login/login.component').then((m) => m.LoginComponent),
    title: 'Sign in — Gadaa Istar',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./pages/register/register.component').then((m) => m.RegisterComponent),
    title: 'Create account — Gadaa Istar',
  },
  // -------------------- Catalog --------------------
  {
    path: 'shop',
    loadComponent: () =>
      import('./pages/shop/shop-list.component').then((m) => m.ShopListComponent),
    title: 'Shop the collection — Gadaa Istar',
  },
  {
    path: 'shop/:category',
    loadComponent: () =>
      import('./pages/shop/shop-list.component').then((m) => m.ShopListComponent),
    title: 'Shop — Gadaa Istar',
  },
  {
    path: 'products/:slug',
    loadComponent: () =>
      import('./pages/product-detail/product-detail.component')
        .then((m) => m.ProductDetailComponent),
    title: 'Product — Gadaa Istar',
  },

  // -------------------- Customer portal --------------------
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/account/account.component').then((m) => m.AccountComponent),
    title: 'Your account — Gadaa Istar',
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./pages/account/profile.component')
            .then((m) => m.AccountProfileComponent),
        title: 'Your profile — Gadaa Istar',
      },
      {
        path: 'addresses',
        loadComponent: () =>
          import('./pages/account/addresses.component')
            .then((m) => m.AccountAddressesComponent),
        title: 'Address book — Gadaa Istar',
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./pages/account/orders.component')
            .then((m) => m.AccountOrdersComponent),
        title: 'Order history — Gadaa Istar',
      },
      {
        path: 'orders/:uuid',
        loadComponent: () =>
          import('./pages/account/order-detail.component')
            .then((m) => m.AccountOrderDetailComponent),
        title: 'Order detail — Gadaa Istar',
      },
      {
        path: 'quotes',
        loadComponent: () =>
          import('./pages/account/quotes.component')
            .then((m) => m.AccountQuotesComponent),
        title: 'Your quotes — Gadaa Istar',
      },
      {
        path: 'quotes/:uuid',
        loadComponent: () =>
          import('./pages/account/quote-detail.component')
            .then((m) => m.AccountQuoteDetailComponent),
        title: 'Quote detail — Gadaa Istar',
      },
    ],
  },

  // -------------------- Cart & checkout --------------------
  {
    path: 'cart',
    loadComponent: () =>
      import('./pages/cart/cart.component').then((m) => m.CartComponent),
    title: 'Your cart — Gadaa Istar',
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/checkout/checkout.component')
        .then((m) => m.CheckoutComponent),
    title: 'Checkout — Gadaa Istar',
  },
  {
    path: 'checkout/return',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/checkout/checkout-return.component')
        .then((m) => m.CheckoutReturnComponent),
    title: 'Payment confirmed — Gadaa Istar',
  },
  {
    path: 'checkout/cancel',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/checkout/checkout-cancel.component')
        .then((m) => m.CheckoutCancelComponent),
    title: 'Payment cancelled — Gadaa Istar',
  },

  // -------------------- Order tracking (public) --------------------
  {
    path: 'track-order',
    loadComponent: () =>
      import('./pages/track-order/track-order.component')
        .then((m) => m.TrackOrderComponent),
    title: 'Track your order — Gadaa Istar',
  },

  // -------------------- Wishlist (public, localStorage) --------------------
  {
    path: 'wishlist',
    loadComponent: () =>
      import('./pages/wishlist/wishlist.component')
        .then((m) => m.WishlistComponent),
    title: 'Your wishlist — Gadaa Istar',
  },

  // -------------------- Email verification --------------------
  {
    path: 'verify-email/pending',
    loadComponent: () =>
      import('./pages/verify-email-pending/verify-email-pending.component')
        .then((m) => m.VerifyEmailPendingComponent),
    title: 'Check your inbox — Gadaa Istar',
  },
  {
    path: 'verify-email/:token',
    loadComponent: () =>
      import('./pages/verify-email-confirm/verify-email-confirm.component')
        .then((m) => m.VerifyEmailConfirmComponent),
    title: 'Confirming your email — Gadaa Istar',
  },

  // -------------------- Password reset --------------------
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./pages/forgot-password/forgot-password.component')
        .then((m) => m.ForgotPasswordComponent),
    title: 'Forgot password — Gadaa Istar',
  },
  {
    path: 'reset-password/:token',
    loadComponent: () =>
      import('./pages/reset-password/reset-password.component')
        .then((m) => m.ResetPasswordComponent),
    title: 'Reset password — Gadaa Istar',
  },

  // -------------------- CMS static pages --------------------
  {
    path: 'pages/:slug',
    loadComponent: () =>
      import('./pages/cms-page/cms-page.component').then((m) => m.CmsPageComponent),
    title: 'Gadaa Istar',
  },

  {
    path: '**',
    redirectTo: '',
  },
];
