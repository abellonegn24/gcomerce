import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AdminAuthService, ForcedLogoutReason } from '../../core/auth/admin-auth.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly year = new Date().getFullYear();

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly submitting = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showPassword = signal<boolean>(false);
  /**
   * Populated when the user is redirected here by AppComponent after a
   * forced logout (idle timeout, expired refresh token, refresh request
   * failed). Rendered above the form so the user understands why they
   * were kicked out of the admin portal.
   */
  protected readonly noticeMessage = signal<string | null>(
    this.mapReason(this.route.snapshot.queryParamMap.get('reason'))
  );

  protected togglePasswordVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth.login({ email, password }).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl('/verify-mfa');
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  private mapError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'INVALID_CREDENTIALS':
        return 'That email or password is incorrect. Please try again.';
      case 'ACCOUNT_LOCKED':
        return this.lockedMessage(err);
      case 'ACCOUNT_SUSPENDED':
      case 'ACCOUNT_DEACTIVATED':
        return 'This admin account is disabled. Contact a super-admin.';
      case 'VALIDATION_FAILED':
        return err.message || 'Please check the highlighted fields and try again.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Sign-in failed. Please try again.';
    }
  }

  /**
   * Builds a user-friendly "account temporarily locked" message from the
   * {@code retryAfterSeconds} detail in the 429 response (INSA CWE-307).
   */
  private lockedMessage(err: NormalizedApiError): string {
    const seconds = err.details?.['retryAfterSeconds'] as number | undefined;
    if (seconds && seconds > 0) {
      const minutes = Math.ceil(seconds / 60);
      return `Too many failed attempts. This admin account is temporarily locked. Please try again in ${minutes} minute${minutes > 1 ? 's' : ''}.`;
    }
    return 'Too many failed attempts. This admin account is temporarily locked. Please try again later.';
  }

  private mapReason(raw: string | null): string | null {
    if (!raw) return null;
    const reason = raw as ForcedLogoutReason;
    switch (reason) {
      case 'idle':
        return 'You were signed out after 10 minutes of inactivity. Please sign in again.';
      case 'expired':
        return 'Your admin session has expired. Please sign in again.';
      case 'refresh-failed':
        return 'Your admin session is no longer valid. Please sign in again.';
      default:
        return null;
    }
  }
}
