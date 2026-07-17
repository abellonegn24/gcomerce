import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { CustomersService } from '../../core/customers/customers.service';
import { CustomerGroupsService } from '../../core/catalog/customer-groups.service';
import { CustomerGroupDto } from '../../core/catalog/catalog.model';
import {
  CUSTOMER_STATUS_LABELS,
  CustomerActionResponse,
  CustomerDetail,
  CustomerStatus,
  customerStatusVariant,
} from '../../core/customers/customer.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

/**
 * /customers/:id — full customer detail + admin action panel.
 *
 * All destructive actions are confirm()-gated. Successful actions re-fetch
 * the customer so the signal keeps a fresh snapshot (including the updated
 * allowedNextStatuses list from the state machine).
 */
@Component({
  selector: 'gi-admin-customer-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './customer-detail.component.html',
  styleUrl: './customer-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CustomerDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CustomersService);
  private readonly groupsService = inject(CustomerGroupsService);

  protected readonly customer = signal<CustomerDetail | null>(null);
  protected readonly groups = signal<CustomerGroupDto[]>([]);
  protected readonly loading = signal<boolean>(true);
  protected readonly busy = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  protected readonly targetStatus = signal<CustomerStatus | ''>('');
  protected readonly statusReason = signal<string>('');
  protected readonly targetGroupId = signal<number | null>(null);

  protected readonly statusLabels = CUSTOMER_STATUS_LABELS;
  protected readonly statusVariant = customerStatusVariant;

  protected readonly isTerminal = computed<boolean>(() => {
    const c = this.customer();
    return !!c && c.allowedNextStatuses.length === 0;
  });

  protected readonly canApproveB2B = computed<boolean>(() => {
    const c = this.customer();
    return !!c && c.customerType === 'B2B' && c.status === 'PENDING_APPROVAL';
  });

  // ---------------------------------------------------------------------------

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get('id');
    const id = raw ? Number(raw) : NaN;
    if (!isFinite(id) || id <= 0) {
      this.errorMessage.set('Invalid customer id in the URL.');
      this.loading.set(false);
      return;
    }
    this.load(id);
    this.groupsService.list().subscribe({
      next: (g) => this.groups.set(g),
      // Fine to swallow — group dropdown just stays empty.
      error: () => this.groups.set([]),
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.get(id).subscribe({
      next: (c) => {
        this.customer.set(c);
        this.targetGroupId.set(c.groupId);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load that customer.');
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  private handle(op: (id: number) => import('rxjs').Observable<CustomerActionResponse>,
                 confirmMsg: string): void {
    const c = this.customer();
    if (!c || this.busy()) return;
    if (confirmMsg && !confirm(confirmMsg)) return;

    this.busy.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    op(c.id).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.successMessage.set(res.message);
        this.load(c.id);
      },
      error: (err: NormalizedApiError) => {
        this.busy.set(false);
        this.errorMessage.set(err.message || 'Action failed.');
      },
    });
  }

  protected submitStatusChange(): void {
    const target = this.targetStatus();
    if (!target) return;
    const c = this.customer();
    if (!c) return;
    const label = this.statusLabels[target];
    const msg = `Move ${c.email} to "${label}"?\n\nThis logs them out of all devices (for SUSPENDED / DEACTIVATED).`;
    this.handle(
      (id) => this.service.changeStatus(id, {
        status: target,
        reason: this.statusReason().trim() || undefined,
      }),
      msg,
    );
    this.targetStatus.set('');
    this.statusReason.set('');
  }

  protected approveB2B(): void {
    const c = this.customer();
    if (!c) return;
    this.handle(
      (id) => this.service.approveB2B(id),
      `Approve B2B application for ${c.companyName || c.email}?\n\nAccount will be flipped to ACTIVE and email verification force-completed.`,
    );
  }

  protected verifyEmail(): void {
    const c = this.customer();
    if (!c) return;
    this.handle(
      (id) => this.service.forceVerifyEmail(id),
      `Force-mark ${c.email} as verified?\n\nUse this only when you've verified the address out-of-band.`,
    );
  }

  protected revokeSessions(): void {
    const c = this.customer();
    if (!c) return;
    this.handle(
      (id) => this.service.revokeSessions(id),
      `Revoke every live session for ${c.email}?\n\nThey'll be logged out on every device and must sign in again.`,
    );
  }

  protected sendPasswordReset(): void {
    const c = this.customer();
    if (!c) return;
    this.handle(
      (id) => this.service.sendPasswordReset(id),
      `Email a password-reset link to ${c.email}?\n\nAny previous reset links are invalidated, all live sessions are revoked, and the customer completes the reset themselves on the storefront.`,
    );
  }

  protected saveGroup(): void {
    const c = this.customer();
    if (!c) return;
    const target = this.targetGroupId();
    if (target === c.groupId) {
      this.successMessage.set('Customer group unchanged.');
      return;
    }
    const targetName = target === null
      ? 'no group (base pricing)'
      : (this.groups().find((g) => g.id === target)?.code ?? String(target));
    this.handle(
      (id) => this.service.setGroup(id, { groupId: target }),
      `Move ${c.email} to ${targetName}?`,
    );
  }
}
