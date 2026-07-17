import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { correlationInterceptor } from './core/http/correlation.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      // Interceptor order matters:
      //   1. correlation — attaches X-Correlation-Id
      //   2. auth        — attaches Bearer, retries once on 401 via refresh
      //   3. error       — normalizes 4xx/5xx into NormalizedApiError shape
      withInterceptors([correlationInterceptor, authInterceptor, errorInterceptor])
    ),
    provideAnimationsAsync(),
  ],
};
