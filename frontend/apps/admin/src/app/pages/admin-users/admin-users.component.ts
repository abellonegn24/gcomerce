import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AdminAuthService } from '../../core/auth/admin-auth.service';
import { AdminUsersService } from '../../core/admins/admin-users.service';
import { AdminUserSummary } from '../../core/admins/admin-users.model';
import { NormalizedApiError } from '../../core/http/error.interceptor';

@Component({
  selector: 'gi-admin-users',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersComponent implements OnInit {
  private readonly service = inject(AdminUsersService);
  private readonly auth = inject(AdminAuthService);

  protected readonly loading = signal<boolean>(true);
  protected readonly items = signal<AdminUserSummary[]>([]);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly banner = signal<string | null>(null);

  /** id of the row currently mid-reset — disables its button + shows spinner. */
  protected readonly resettingId = signal<number | null>(null);

  protected readonly currentAdminId = computed<number | null>(
    () => this.auth.currentAdmin()?.id ?? null
  );
  protected readonly isSuperadmin = computed<boolean>(
    () => this.auth.hasRole('SUPERADMIN')
  );

  ngOnInit(): void {
    this.refresh();
  }

  protected refresh(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service.list().subscribe({
      next: (res) => {
        this.items.set(res.items ?? []);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.errorMessage.set(err.message || 'Could not load admin users.');
        this.loading.set(false);
      },
    });
  }

  protected canResetPeer(row: AdminUserSummary): boolean {
    return this.isSuperadmin() && row.id !== this.currentAdminId();
  }

  protected resetMfa(row: AdminUserSummary): void {
    if (!this.canResetPeer(row)) return;
    const ok = confirm(
      `Reset MFA for ${row.email}?\n\n` +
        `This wipes their TOTP secret and revokes every active session for that admin. ` +
        `They will be forced to re-enroll on their next sign-in.`
    );
    if (!ok) return;

    this.resettingId.set(row.id);
    this.banner.set(null);
    this.errorMessage.set(null);

    this.service.resetMfa(row.id).subscribe({
      next: (res) => {
        this.banner.set(res.message);
        this.resettingId.set(null);
        this.refresh();
      },
      error: (err: NormalizedApiError) => {
        this.resettingId.set(null);
        this.errorMessage.set(this.mapError(err));
      },
    });
  }

  private mapError(err: NormalizedApiError): string {
    switch (err.code) {
      case 'SELF_RESET_FORBIDDEN':
        return "You can't reset your own MFA. Sign out and use the enrollment flow instead.";
      case 'FORBIDDEN':
      case 'MISSING_ROLE':
        return 'Only SUPERADMIN accounts can reset MFA for others.';
      case 'NETWORK_ERROR':
        return err.message;
      default:
        return err.message || 'Could not reset MFA. Please try again.';
    }
  }
}
