import { HttpClient, HttpEvent, HttpEventType, HttpRequest, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { UploadResponse } from './catalog.model';

/**
 * Progress + result envelope streamed by {@link UploadsService.uploadProductMedia}.
 * The observable emits one {@code {phase:'progress', percent}} event per
 * chunk while the file uploads and then a single terminal
 * {@code {phase:'done', response}} once the server returns. Other HTTP
 * lifecycle events (Sent / ResponseHeader / DownloadProgress) are dropped.
 */
export type UploadEvent =
  | { phase: 'progress'; percent: number }
  | { phase: 'done'; response: UploadResponse };

/**
 * Direct file upload to {@code /api/admin/v1/uploads/product-media}.
 * Uses {@link HttpClient} with {@code reportProgress:true} so the UI can
 * render a proper progress bar while large videos upload.
 */
@Injectable({ providedIn: 'root' })
export class UploadsService {
  private readonly http = inject(HttpClient);

  /**
   * Upload a single file to the product-media bucket.
   * Emits progress percentages then the final {@link UploadResponse}.
   */
  uploadProductMedia(file: File): Observable<UploadEvent> {
    const form = new FormData();
    form.append('file', file, file.name);

    const req = new HttpRequest(
      'POST',
      `${environment.apiBaseUrl}/uploads/product-media`,
      form,
      { reportProgress: true },
    );

    return this.http.request<UploadResponse>(req).pipe(
      map((event: HttpEvent<UploadResponse>): UploadEvent | null => {
        if (event.type === HttpEventType.UploadProgress) {
          const percent = event.total
            ? Math.round((event.loaded / event.total) * 100)
            : 0;
          return { phase: 'progress', percent };
        }
        if (event.type === HttpEventType.Response) {
          const body = (event as HttpResponse<UploadResponse>).body;
          if (!body) {
            throw new Error('Upload response was empty.');
          }
          return { phase: 'done', response: body };
        }
        return null;
      }),
      filter((e): e is UploadEvent => e !== null),
    );
  }
}
