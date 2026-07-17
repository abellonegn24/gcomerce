import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AdminAuthService } from '../auth/admin-auth.service';

/**
 * Guards the /verify-mfa route: only reachable when the user has just
 * completed step 1 (password) and has a pending MFA challenge in flight.
 */
export const mfaChallengeGuard: CanActivateFn = () => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);
  return auth.pendingChallenge() !== null || router.createUrlTree(['/login']);
};
