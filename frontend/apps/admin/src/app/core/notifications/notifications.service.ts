import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { NotificationsDto } from './notifications.model';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  getNotifications(): Observable<NotificationsDto> {
    return this.http.get<NotificationsDto>(
      `${environment.apiBaseUrl}/notifications`
    );
  }
}
