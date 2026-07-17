import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { NormalizedApiError } from '../../core/http/error.interceptor';
import { AuthService } from '../../core/auth/auth.service';
import { OrderStatus } from '../../core/orders/order.model';
import { TrackingResponse } from '../../core/orders/tracking.model';
import { OrderTrackingService } from '../../core/orders/order-tracking.service';

/**
 * /track-order — public order tracking page. No sign-in required.
 * The visitor enters their order UUID (or invoice number) and the email
 * used at checkout to see the current status + timeline.
 */
@Component({
  selector: 'gi-track-order',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './track-order.component.html',
  styleUrl: './track-order.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrackOrderComponent {
  private readonly trackingService = inject(OrderTrackingService);
  private readonly auth = inject(AuthService);

  protected readonly isAuthenticated = this.auth.isAuthenticated;

  protected readonly uuid = signal<string>('');
  protected readonly email = signal<string>('');
  protected readonly loading = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly result = signal<TrackingResponse | null>(null);
  protected readonly notFound = signal<boolean>(false);

  protected readonly canSubmit = computed<boolean>(
    () => this.uuid().trim().length > 0 && this.email().trim().length > 0 && !this.loading(),
  );

  protected submit(): void {
    const u = this.uuid().trim();
    const e = this.email().trim();
    if (!u || !e) return;

    this.loading.set(true);
    this.errorMessage.set(null);
    this.notFound.set(false);
    this.result.set(null);

    this.trackingService.track(u, e).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.notFound.set(true);
        } else {
          this.errorMessage.set(err.message || 'Could not look up this order. Please try again.');
        }
      },
    });
  }

  protected statusLabel(status: OrderStatus): string {
    return status.replace(/_/g, ' ').toLowerCase();
  }

  protected statusClass(status: OrderStatus): string {
    switch (status) {
      case 'PAID':
      case 'FULFILLING':
      case 'SHIPPED':
        return 'is-ok';
      case 'DELIVERED':
        return 'is-done';
      case 'PENDING_PAYMENT':
      case 'DRAFT':
        return 'is-pending';
      case 'CANCELLED':
      case 'REFUNDED':
        return 'is-cancelled';
      default:
        return '';
    }
  }

  protected formatMoney(currency: string, amount: string): string {
    const value = Number(amount);
    if (!isFinite(value)) return `${currency} ${amount}`;
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(value);
    } catch {
      return `${currency} ${value.toFixed(2)}`;
    }
  }
}
