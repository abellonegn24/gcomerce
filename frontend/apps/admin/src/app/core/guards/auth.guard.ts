import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AdminAuthService } from '../auth/admin-auth.service';

/**
 * Blocks admin routes for anonymous users, redirecting to /login with a
 * `?next=...` query parameter so we can send them back after signing in.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) return true;
  return router.createUrlTree(['/login'], { queryParams: { next: state.url } });
};
