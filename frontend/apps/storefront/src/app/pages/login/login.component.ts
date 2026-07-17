import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** True when the user just completed a password reset and landed here. */
  protected readonly resetSuccess = signal<boolean>(
    this.route.snapshot.queryParamMap.get('reset') === '1'
  );

  /** True when the user just changed their password in /account and got signed out. */
  protected readonly passwordChanged = signal<boolean>(
    this.route.snapshot.queryParamMap.get('passwordChanged') === '1'
  );

  protected submit(): void {
    this.errorMessage.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { email, password } = this.form.getRawValue();
    this.auth.login({ email, password }).subscribe({
      next: () => {
        const next = this.route.snapshot.queryParamMap.get('next') ?? '/account';
        void this.router.navigateByUrl(next);
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.friendlyMessage(err));
      },
    });
  }

  private friendlyMessage(err: NormalizedApiError): string {
    switch (err.code) {
      case 'INVALID_CREDENTIALS':       return 'The email or password you entered is incorrect.';
      case 'ACCOUNT_SUSPENDED':         return 'Your account has been suspended. Please contact support.';
      case 'ACCOUNT_DEACTIVATED':       return 'This account is no longer active.';
      case 'ACCOUNT_PENDING_APPROVAL':  return 'Your B2B account is still awaiting approval.';
      case 'VALIDATION_FAILED':         return 'Please check the fields highlighted below.';
      case 'NETWORK_ERROR':             return 'Could not reach the server. Please check your connection.';
      default:                          return err.message || 'Something went wrong. Please try again.';
    }
  }
}
