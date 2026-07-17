import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { CustomerType, RegisterPayload } from '../../core/auth/auth.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = this.fb.nonNullable.group({
    customerType: ['B2C' as CustomerType, Validators.required],
    firstName: ['', [Validators.required, Validators.maxLength(120)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    companyName: [''],
    companyTin: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    phone: [''],
    preferredCurrency: ['USD' as 'USD' | 'EUR' | 'GBP', Validators.required],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly isB2B = computed(
    () => this.form.controls.customerType.value === 'B2B'
  );

  constructor() {
    // Toggle company-field validation when switching between B2C and B2B.
    this.form.controls.customerType.valueChanges.subscribe((type) => {
      const companyName = this.form.controls.companyName;
      if (type === 'B2B') {
        companyName.setValidators([Validators.required, Validators.maxLength(255)]);
      } else {
        companyName.clearValidators();
      }
      companyName.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected submit(): void {
    this.errorMessage.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);

    const raw = this.form.getRawValue();
    const payload: RegisterPayload = {
      email: raw.email,
      password: raw.password,
      firstName: raw.firstName,
      lastName: raw.lastName,
      customerType: raw.customerType,
      companyName: raw.companyName || undefined,
      companyTin: raw.companyTin || undefined,
      phone: raw.phone || undefined,
      preferredCurrency: raw.preferredCurrency,
    };

    this.auth.register(payload).subscribe({
      next: () => {
        // B2C accounts must confirm their email before they can transact →
        // send them to the "check your inbox" screen instead of straight to
        // /account. B2B accounts still need admin approval anyway, so the
        // same screen doubles as a clear "next step" prompt.
        void this.router.navigate(['/verify-email/pending'], {
          queryParams: { email: payload.email },
        });
      },
      error: (err: NormalizedApiError) => {
        this.submitting.set(false);
        this.errorMessage.set(this.friendlyMessage(err));
      },
    });
  }

  private friendlyMessage(err: NormalizedApiError): string {
    switch (err.code) {
      case 'EMAIL_TAKEN':       return 'An account with this email already exists. Try signing in instead.';
      case 'VALIDATION_FAILED': return 'Please check the fields highlighted below.';
      case 'NETWORK_ERROR':     return 'Could not reach the server. Please check your connection.';
      default:                  return err.message || 'Something went wrong. Please try again.';
    }
  }
}
