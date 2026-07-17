import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { CustomerDto } from '../../core/auth/auth.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

type State = 'verifying' | 'success' | 'error';

@Component({
  selector: 'gi-verify-email-confirm',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './verify-email-confirm.component.html',
  styleUrl: '../_shared/auth-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailConfirmComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly state = signal<State>('verifying');
  protected readonly customer = signal<CustomerDto | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (!token) {
      this.state.set('error');
      this.errorMessage.set('Missing verification token in the URL.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: (customer) => {
        this.customer.set(customer);
        this.state.set('success');
      },
      error: (err: NormalizedApiError) => {
        this.state.set('error');
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  private mapError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'ROUTE_NOT_FOUND':
      case 'NotFound':
      case 'NOT_FOUND':
        return 'That verification link is invalid or has already been used.';
      case 'VALIDATION_FAILED':
        return err.message || 'That link is no longer valid — request a new one.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'We couldn\'t verify your email. Try requesting a new link.';
    }
  }
}
