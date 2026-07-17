import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AdminUsersListResponse, ResetMfaResponse } from './admin-users.model';

/**
 * Thin data-access wrapper around /api/admin/v1/admin-users.
 */
@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  private readonly http = inject(HttpClient);

  list(): Observable<AdminUsersListResponse> {
    return this.http.get<AdminUsersListResponse>(
      `${environment.apiBaseUrl}/admin-users`
    );
  }

  resetMfa(adminUserId: number): Observable<ResetMfaResponse> {
    return this.http.post<ResetMfaResponse>(
      `${environment.apiBaseUrl}/admin-users/${adminUserId}/mfa/reset`,
      {}
    );
  }
}
