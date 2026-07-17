import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/**
 * Prevents already-signed-in customers from hitting /login or /register.
 * Sends them to /account (or the `next` query param if present).
 */
export const guestGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return true;
  const next = route.queryParamMap.get('next');
  return router.createUrlTree([next ?? '/account']);
};
