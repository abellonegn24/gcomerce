import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AdminAuthService } from '../../core/auth/admin-auth.service';
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

  protected readonly year = new Date().getFullYear();

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly submitting = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

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
      case 'ACCOUNT_SUSPENDED':
      case 'ACCOUNT_DEACTIVATED':
        return 'This admin account is disabled. Contact a super-admin.';
      case 'VALIDATION_FAILED':
        return 'Please check the highlighted fields and try again.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Sign-in failed. Please try again.';
    }
  }
}
