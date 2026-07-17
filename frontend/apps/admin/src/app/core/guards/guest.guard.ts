import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AdminAuthService } from '../auth/admin-auth.service';

/**
 * Prevents already-signed-in admins from re-hitting /login.
 * Sends them to /dashboard (or the `next` query param if present).
 */
export const guestGuard: CanActivateFn = (route) => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return true;
  const next = route.queryParamMap.get('next');
  return router.createUrlTree([next ?? '/dashboard']);
};
