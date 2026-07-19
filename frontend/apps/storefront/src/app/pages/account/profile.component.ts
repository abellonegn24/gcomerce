import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { CustomerDto, UpdateProfilePayload } from '../../core/auth/auth.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

type ViewMode = 'view' | 'edit' | 'password';

/**
 * /account default child. Three modes:
 *   - view      → read-only summary card
 *   - edit      → inline form bound to PATCH /auth/me
 *   - password  → current+new+confirm form bound to POST /auth/me/change-password
 */
@Component({
  selector: 'gi-account-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountProfileComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  protected readonly customer = this.auth.currentCustomer;

  protected readonly mode = signal<ViewMode>('view');
  protected readonly submitting = signal<boolean>(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  // ---------------------------------------------------------------------------
  // Edit-profile form — repopulated whenever the underlying customer changes.
  // ---------------------------------------------------------------------------
  protected readonly editForm = this.fb.nonNullable.group({
    firstName: ['', [Validators.maxLength(120)]],
    lastName: ['', [Validators.maxLength(120)]],
    phone: ['', [Validators.maxLength(64)]],
    companyName: ['', [Validators.maxLength(255)]],
    companyTin: ['', [Validators.maxLength(64), Validators.pattern(/^[A-Za-z0-9\- ]*$/)]],
    companyVatNumber: ['', [Validators.maxLength(64)]],
    preferredCurrency: ['USD' as 'USD' | 'ETB'],
    preferredLocale: ['', [Validators.maxLength(10)]],
  });

  // ---------------------------------------------------------------------------
  // Change-password form
  // ---------------------------------------------------------------------------
  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    confirmPassword: ['', [Validators.required]],
  }, {
    validators: [(group) => {
      const g = group as unknown as { value: { newPassword: string; confirmPassword: string } };
      return g.value.newPassword === g.value.confirmPassword ? null : { mismatch: true };
    }],
  });

  protected readonly isB2b = computed<boolean>(
    () => this.customer()?.customerType === 'B2B'
  );

  constructor() {
    // Refill the edit form whenever the stored customer changes.
    effect(() => {
      const c = this.customer();
      if (!c) return;
      this.editForm.patchValue({
        firstName: c.firstName ?? '',
        lastName: c.lastName ?? '',
        phone: c.phone ?? '',
        companyName: c.companyName ?? '',
        companyTin: c.companyTin ?? '',
        companyVatNumber: '',
        preferredCurrency: (c.preferredCurrency as 'USD' | 'ETB') || 'USD',
        preferredLocale: c.preferredLocale ?? '',
      });
    });
  }

  // ---------------------------------------------------------------------------
  // Mode transitions
  // ---------------------------------------------------------------------------

  protected startEdit(): void {
    this.mode.set('edit');
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  protected startPasswordChange(): void {
    this.mode.set('password');
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.passwordForm.reset();
  }

  protected cancel(): void {
    this.mode.set('view');
    this.errorMessage.set(null);
    // Success message persists briefly so users see the confirmation.
  }

  // ---------------------------------------------------------------------------
  // Submit handlers
  // ---------------------------------------------------------------------------

  protected saveProfile(): void {
    if (this.editForm.invalid || this.submitting()) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.editForm.getRawValue();
    const payload: Partial<UpdateProfilePayload> = {};
    // Only send non-blank fields — matches server-side nonBlank guard.
    for (const [k, v] of Object.entries(raw)) {
      if (v != null && String(v).trim().length > 0) {
        (payload as Record<string, unknown>)[k] = String(v).trim();
      }
    }

    this.auth.updateProfile(payload).subscribe({
      next: (_updated: CustomerDto) => {
        this.submitting.set(false);
        this.mode.set('view');
        this.successMessage.set('Profile updated.');
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(err.message || 'Could not save your profile.');
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.submitting()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    if (this.passwordForm.hasError('mismatch')) {
      this.errorMessage.set('New password and confirmation do not match.');
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.submitting.set(false);
        // Backend revoked every session; AuthService already cleared local state.
        void this.router.navigateByUrl('/login?passwordChanged=1');
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.mapPasswordError(err));
      },
    });
  }

  private mapPasswordError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'INVALID_CREDENTIALS':
        return 'Current password is incorrect.';
      case 'VALIDATION_FAILED':
        return err.message || 'Please choose a different password.';
      case 'RATE_LIMIT_EXCEEDED':
        return 'Too many attempts. Please wait a few minutes before trying again.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Could not change your password. Please try again.';
    }
  }
}
