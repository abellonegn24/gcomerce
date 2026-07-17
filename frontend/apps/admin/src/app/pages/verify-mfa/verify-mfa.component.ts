import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { QRCodeComponent } from 'angularx-qrcode';

import { AdminAuthService } from '../../core/auth/admin-auth.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-verify-mfa',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, QRCodeComponent],
  templateUrl: './verify-mfa.component.html',
  styleUrl: './verify-mfa.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyMfaComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AdminAuthService);
  private readonly router = inject(Router);

  protected readonly year = new Date().getFullYear();
  protected readonly pending = this.auth.pendingChallenge;

  protected readonly form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected readonly submitting = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly isEnrollment = computed<boolean>(
    () => this.pending()?.state === 'MFA_ENROLLMENT_REQUIRED'
  );
  protected readonly setup = computed(() => this.pending()?.setup ?? null);
  protected readonly email = computed<string>(() => this.pending()?.email ?? '');

  /**
   * The otpauth:// URI to embed in the QR code. Rendered client-side by
   * <qrcode> (angularx-qrcode) so the TOTP secret never leaves the browser.
   */
  protected readonly qrData = computed<string | null>(() => this.setup()?.otpauthUri ?? null);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.verifyMfa(this.form.getRawValue().code).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl('/dashboard');
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  protected cancel(): void {
    this.auth.cancelPendingChallenge();
    void this.router.navigateByUrl('/login');
  }

  private mapError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'MFA_INVALID_CODE':
        return 'That code was not accepted. Try the current 6-digit code from your authenticator.';
      case 'INVALID_TOKEN':
      case 'TOKEN_EXPIRED':
        return 'Your sign-in window expired. Please sign in again.';
      case 'MFA_NOT_INITIALIZED':
        return 'No MFA secret on file. Please sign in again to start enrollment.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Verification failed. Please try again.';
    }
  }
}
