import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { OrdersService } from '../../core/orders/orders.service';
import { RefundsService } from '../../core/orders/refunds.service';
import { CreateRefundPayload, RefundEntry } from '../../core/orders/refund.model';
import {
  ALLOWED_TRANSITIONS,
  ORDER_STATUS_OPTIONS,
  OrderDetail,
  OrderStatus,
  orderStatusVariant,
} from '../../core/orders/order.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * /orders/:uuid — full detail for a single order plus a state-machine-aware
 * transition dropdown. Only the legal next statuses (per
 * {@link ALLOWED_TRANSITIONS}) are offered.
 */
@Component({
  selector: 'gi-admin-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(OrdersService);
  private readonly refundsService = inject(RefundsService);

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly transitioning = signal<boolean>(false);
  protected readonly successMessage = signal<string | null>(null);

  // Transition form
  protected readonly nextStatus = signal<OrderStatus | ''>('');
  protected readonly note = signal<string>('');

  // Refund modal
  protected readonly refundOpen     = signal<boolean>(false);
  protected readonly refundAmount   = signal<string>('');
  protected readonly refundReason   = signal<string>('');
  protected readonly refundManual   = signal<boolean>(false);
  protected readonly refundSaving   = signal<boolean>(false);
  protected readonly refundError    = signal<string | null>(null);

  /** Remaining refundable balance = grandTotal − refundedTotal. */
  protected readonly refundableRemaining = computed<number>(() => {
    const o = this.order();
    if (!o) return 0;
    const gt = Number(o.grandTotal);
    const rt = Number(o.refundedTotal);
    if (!isFinite(gt) || !isFinite(rt)) return 0;
    return Math.max(0, Math.round((gt - rt) * 100) / 100);
  });

  /** True when the current state legally accepts a refund attempt. */
  protected readonly canRefund = computed<boolean>(() => {
    const o = this.order();
    if (!o) return false;
    if (this.refundableRemaining() <= 0) return false;
    switch (o.status) {
      case 'PAID':
      case 'FULFILLING':
      case 'SHIPPED':
      case 'DELIVERED':
      case 'PARTIALLY_REFUNDED':
        return true;
      default:
        return false;
    }
  });

  protected readonly statusLabels: Record<OrderStatus, string> = Object.fromEntries(
    ORDER_STATUS_OPTIONS.map((o) => [o.code, o.label]),
  ) as Record<OrderStatus, string>;

  protected readonly allowedNext = computed<OrderStatus[]>(() => {
    const o = this.order();
    return o ? ALLOWED_TRANSITIONS[o.status] : [];
  });

  protected readonly isTerminal = computed<boolean>(() => this.allowedNext().length === 0);

  ngOnInit(): void {
    const uuid = this.route.snapshot.paramMap.get('uuid');
    if (!uuid) {
      this.errorMessage.set('Missing order id in the URL.');
      this.loading.set(false);
      return;
    }
    this.reload(uuid);
  }

  private reload(uuid: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.get(uuid).subscribe({
      next: (o) => {
        this.order.set(o);
        this.loading.set(false);
        // Reset the pending selection any time we (re)load the record.
        this.nextStatus.set('');
        this.note.set('');
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load this order.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Transitions
  // ---------------------------------------------------------------------------

  protected submitTransition(): void {
    const o = this.order();
    const next = this.nextStatus();
    if (!o || !next) {
      this.errorMessage.set('Please choose a target status.');
      return;
    }
    if (!ALLOWED_TRANSITIONS[o.status].includes(next)) {
      this.errorMessage.set(
        `Cannot transition ${o.status} → ${next}. Backend state machine will reject it.`,
      );
      return;
    }
    const trimmedNote = this.note().trim();
    // For explicit-note statuses, require a note.
    if ((next === 'CANCELLED' || next === 'REFUNDED') && trimmedNote.length === 0) {
      this.errorMessage.set(`A note is required when moving an order to ${next}.`);
      return;
    }

    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.transitioning.set(true);
    this.service
      .transition(o.uuid, {
        next,
        note: trimmedNote.length > 0 ? trimmedNote : undefined,
      })
      .subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.transitioning.set(false);
          this.successMessage.set(
            `Moved order to ${this.statusLabels[updated.status] ?? updated.status}.`,
          );
          this.nextStatus.set('');
          this.note.set('');
        },
        error: (err: NormalizedApiError) => {
          this.transitioning.set(false);
          this.errorMessage.set(err.message || 'Could not update the order status.');
        },
      });
  }

  // ---------------------------------------------------------------------------
  // Refunds
  // ---------------------------------------------------------------------------

  protected openRefundModal(): void {
    const remaining = this.refundableRemaining();
    // Prefill with the full remaining balance — the single most common case
    // ("customer wants their money back") gets zero typing; partials are a
    // one-field edit away.
    this.refundAmount.set(remaining > 0 ? remaining.toFixed(2) : '');
    this.refundReason.set('');
    this.refundManual.set(false);
    this.refundError.set(null);
    this.refundOpen.set(true);
  }

  protected closeRefundModal(): void {
    if (this.refundSaving()) return;      // guard against closing mid-request
    this.refundOpen.set(false);
    this.refundError.set(null);
  }

  protected submitRefund(): void {
    const o = this.order();
    if (!o || this.refundSaving()) return;

    const amountStr = this.refundAmount().trim();
    const reason    = this.refundReason().trim();
    const amount    = Number(amountStr);

    if (!amountStr || !isFinite(amount) || amount <= 0) {
      this.refundError.set('Enter a refund amount greater than 0.');
      return;
    }
    if (amount > this.refundableRemaining() + 0.001) {
      this.refundError.set(
        `Refund exceeds the remaining balance (${this.formatMoney(o.currency, this.refundableRemaining().toFixed(2))}).`,
      );
      return;
    }
    if (reason.length < 3) {
      this.refundError.set('Please provide a reason (3+ chars) — it goes on the audit log.');
      return;
    }

    const payload: CreateRefundPayload = {
      amount: amountStr,
      reason,
      manual: this.refundManual(),
    };
    this.refundError.set(null);
    this.refundSaving.set(true);
    this.refundsService.create(o.uuid, payload).subscribe({
      next: (row: RefundEntry) => {
        this.refundSaving.set(false);
        this.refundOpen.set(false);
        this.successMessage.set(
          row.status === 'SUCCEEDED'
            ? `Refunded ${this.formatMoney(o.currency, String(row.amount))} — ${row.gatewayProvider ?? 'gateway'}.`
            : `Refund attempt recorded, but the gateway rejected it: ${row.gatewayErrorMessage ?? row.gatewayErrorCode ?? 'unknown reason'}.`,
        );
        // Refetch so the refunded_total + status + refund history are all fresh.
        this.reload(o.uuid);
      },
      error: (err: NormalizedApiError) => {
        this.refundSaving.set(false);
        this.refundError.set(err.message || 'Could not issue the refund.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  protected statusVariant(s: OrderStatus): string {
    return orderStatusVariant(s);
  }

  protected formatMoney(currency: string, amount: string | number): string {
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

  protected asNumber(value: string | number | null | undefined): number {
    if (value === null || value === undefined) return 0;
    const n = typeof value === 'string' ? Number(value) : value;
    return isFinite(n) ? n : 0;
  }
}
