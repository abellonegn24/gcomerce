import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  OnDestroy,
  PLATFORM_ID,
  inject,
  signal,
} from '@angular/core';
import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { NotificationsService } from '../../core/notifications/notifications.service';
import { NotificationsDto } from '../../core/notifications/notifications.model';

@Component({
  selector: 'gi-notification-bell',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private readonly notificationsSvc = inject(NotificationsService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly doc = inject(DOCUMENT);

  protected readonly data = signal<NotificationsDto | null>(null);
  protected readonly open = signal<boolean>(false);
  private intervalId: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.fetch();
    this.startPolling();
    this.doc.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  ngOnDestroy(): void {
    this.stopPolling();
    if (isPlatformBrowser(this.platformId)) {
      this.doc.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
  }

  toggle(): void {
    this.open.update((v) => !v);
  }

  close(): void {
    this.open.set(false);
  }

  // ---------------------------------------------------------------------------

  /**
   * Only poll while the tab is visible. When the tab is hidden the browser
   * throttles timers anyway, and — more importantly — continued polling
   * would trigger authenticated API calls that refresh the admin session
   * token, defeating the idle-timeout policy.
   */
  private readonly onVisibilityChange = (): void => {
    if (this.doc.visibilityState === 'visible') {
      this.fetch();
      this.startPolling();
    } else {
      this.stopPolling();
    }
  };

  private startPolling(): void {
    if (this.intervalId !== null) return;
    this.intervalId = setInterval(() => this.fetch(), 60_000);
  }

  private stopPolling(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  private fetch(): void {
    this.notificationsSvc.getNotifications().subscribe({
      next: (dto) => this.data.set(dto),
      error: () => {},
    });
  }
}
