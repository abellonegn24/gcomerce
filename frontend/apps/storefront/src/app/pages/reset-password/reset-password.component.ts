import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: '../_shared/auth-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly token = signal<string>('');
  protected readonly submitting = signal<boolean>(false);
  protected readonly submitted = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: [matchPasswords] }
  );

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.errorMessage.set('Missing reset token in the URL. Request a new link.');
      return;
    }
    this.token.set(token);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting() || !this.token()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.resetPassword(this.token(), this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
        setTimeout(() => void this.router.navigateByUrl('/login?reset=1'), 2500);
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  private mapError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'ROUTE_NOT_FOUND':
      case 'NOT_FOUND':
        return 'That reset link is invalid or has already been used. Request a new one.';
      case 'VALIDATION_FAILED':
        return err.message || 'That reset link is no longer valid.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Could not reset your password. Please try again.';
    }
  }
}

function matchPasswords(group: AbstractControl): ValidationErrors | null {
  const a = group.get('newPassword')?.value;
  const b = group.get('confirmPassword')?.value;
  return a && b && a !== b ? { passwordsMismatch: true } : null;
}
