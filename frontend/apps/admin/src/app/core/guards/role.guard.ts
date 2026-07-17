import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';

import { AdminAuthService } from '../auth/admin-auth.service';

/**
 * Route guard that reads `data: { roles: [...] }` from the route definition
 * and lets the request through only if the signed-in admin has at least one
 * of the listed roles. Falls through to /dashboard on mismatch (a "forbidden"
 * page is a Phase-3 UX polish item).
 *
 * Usage:
 *   { path: 'settings', data: { roles: ['SUPERADMIN'] }, canActivate: [roleGuard], ... }
 */
export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);

  const required = (route.data?.['roles'] as string[] | undefined) ?? [];
  if (required.length === 0) return true;

  const currentRole = auth.currentAdmin()?.role;
  if (currentRole && required.includes(currentRole)) return true;

  return router.createUrlTree(['/dashboard']);
};
