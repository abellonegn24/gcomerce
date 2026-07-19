import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideClientHydration } from '@angular/platform-browser';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { timeoutInterceptor } from './core/http/timeout.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })
    ),
    provideHttpClient(
      withFetch(),
      // Order matters: correlation → auth → timeout → error (so error can see the normalized envelope)
      withInterceptors([correlationInterceptor, authInterceptor, timeoutInterceptor, errorInterceptor])
    ),
    provideAnimationsAsync(),
    provideClientHydration(),
  ],
};
