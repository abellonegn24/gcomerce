import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-verify-email-pending',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verify-email-pending.component.html',
  styleUrl: '../_shared/auth-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailPendingComponent {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  /** Email is passed in as a query param when we route here from /register. */
  protected readonly email = signal<string>(
    this.route.snapshot.queryParamMap.get('email') ?? this.auth.currentCustomer()?.email ?? ''
  );

  protected readonly resending = signal<boolean>(false);
  protected readonly resentAt = signal<Date | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected resend(): void {
    const email = this.email().trim();
    if (!email || this.resending()) return;
    this.resending.set(true);
    this.errorMessage.set(null);

    this.auth.resendVerification(email).subscribe({
      next: () => {
        this.resending.set(false);
        this.resentAt.set(new Date());
      },
      error: (err: NormalizedApiError) => {
        this.resending.set(false);
        this.errorMessage.set(err.message || 'Could not resend the message. Please try again.');
      },
    });
  }
}
