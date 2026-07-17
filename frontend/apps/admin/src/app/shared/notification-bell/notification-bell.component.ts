import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
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

  protected readonly data = signal<NotificationsDto | null>(null);
  protected readonly open = signal<boolean>(false);
  private intervalId: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.fetch();
    this.intervalId = setInterval(() => this.fetch(), 60_000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) clearInterval(this.intervalId);
  }

  toggle(): void {
    this.open.update((v) => !v);
  }

  close(): void {
    this.open.set(false);
  }

  private fetch(): void {
    this.notificationsSvc.getNotifications().subscribe({
      next: (dto) => this.data.set(dto),
      error: () => {},
    });
  }
}
