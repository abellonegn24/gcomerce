import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TrackingResponse } from './tracking.model';

/**
 * Public order tracking — no auth token required. Looks up an order by
 * UUID + email. Rate-limited server-side to 20 requests / minute.
 */
@Injectable({ providedIn: 'root' })
export class OrderTrackingService {
  private readonly http = inject(HttpClient);

  track(uuid: string, email: string): Observable<TrackingResponse> {
    const params = new HttpParams()
      .set('uuid', uuid)
      .set('email', email);
    return this.http.get<TrackingResponse>(
      `${environment.apiBaseUrl}/track-order`,
      { params },
    );
  }
}
