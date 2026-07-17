import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AdminAuthService } from '../../core/auth/admin-auth.service';
import {
  StatsService,
  StatsDto,
  RevenueTrend,
  RecentOrder,
  LowStockItem,
  DayRevenuePoint,
} from '../../core/stats/stats.service';
import { NormalizedApiError } from '../../core/http/error.interceptor';

interface KpiTile {
  label: string;
  value: string;
  icon: string;
  hint: string;
  variant: 'default' | 'warning' | 'success';
}

interface StatusBreakdownEntry {
  status: string;
  count: number;
  pct: number;
}

@Component({
  selector: 'gi-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AdminAuthService);
  private readonly statsService = inject(StatsService);

  protected readonly admin = this.auth.currentAdmin;

  protected readonly stats = signal<StatsDto | null>(null);
  protected readonly loading = signal<boolean>(true);
  protected readonly error = signal<string | null>(null);

  protected readonly kpis = computed<KpiTile[]>(() => {
    const s = this.stats();
    if (!s) return [];

    const fmt = (n: number) => new Intl.NumberFormat('en-US').format(n);
    const fmtMoney = (v: string) => {
      const num = Number(v);
      if (!isFinite(num)) return v;
      return new Intl.NumberFormat('en-US', {
        style: 'currency', currency: 'USD', minimumFractionDigits: 0, maximumFractionDigits: 0,
      }).format(num);
    };

    const tiles: KpiTile[] = [
      {
        label: 'Open orders',
        value: fmt(s.openOrders),
        icon: 'shopping_cart',
        hint: 'Awaiting fulfilment',
        variant: s.openOrders > 0 ? 'default' : 'success',
      },
      {
        label: 'Pending B2B approvals',
        value: fmt(s.pendingB2bApprovals),
        icon: 'verified_user',
        hint: 'New wholesale accounts',
        variant: s.pendingB2bApprovals > 0 ? 'warning' : 'success',
      },
      {
        label: 'Products live',
        value: fmt(s.productsLive),
        icon: 'inventory_2',
        hint: 'In the storefront catalog',
        variant: 'default',
      },
      {
        label: 'Revenue this month',
        value: fmtMoney(s.monthRevenue),
        icon: 'payments',
        hint: 'Paid orders, current calendar month',
        variant: 'success',
      },
      {
        label: 'Low-stock items',
        value: fmt(s.lowStockItems),
        icon: 'warning_amber',
        hint: 'At or below restock threshold',
        variant: s.lowStockItems > 0 ? 'warning' : 'success',
      },
      {
        label: 'Active coupons',
        value: fmt(s.activeCoupons),
        icon: 'confirmation_number',
        hint: 'Currently redeemable',
        variant: 'default',
      },
      {
        label: 'Abandoned orders',
        value: fmt(s.abandonedOrders),
        icon: 'schedule',
        hint: 'Pending payment > 1 hour',
        variant: s.abandonedOrders > 0 ? 'warning' : 'success',
      },
    ];

    if (s.revenueTrend) {
      tiles.push({
        label: 'Revenue today',
        value: fmtMoney(s.revenueTrend.todayRevenue),
        icon: 'today',
        hint: `${s.revenueTrend.todayOrderCount} orders today`,
        variant: 'default',
      });
    }

    return tiles;
  });

  protected readonly trend = computed<RevenueTrend | null>(() => {
    const s = this.stats();
    return s?.revenueTrend ?? null;
  });

  protected readonly trendDeltaPct = computed<number | null>(() => {
    const t = this.trend();
    if (!t) return null;
    const today = Number(t.todayRevenue);
    const yesterday = Number(t.yesterdayRevenue);
    if (!isFinite(today) || !isFinite(yesterday) || yesterday === 0) return null;
    return ((today - yesterday) / yesterday) * 100;
  });

  protected readonly monthDeltaPct = computed<number | null>(() => {
    const t = this.trend();
    if (!t) return null;
    const month = Number(t.monthRevenue);
    const last = Number(t.lastMonthRevenue);
    if (!isFinite(month) || !isFinite(last) || last === 0) return null;
    return ((month - last) / last) * 100;
  });

  protected readonly recentOrders = computed<RecentOrder[]>(() => {
    return this.stats()?.recentOrders ?? [];
  });

  protected readonly lowStockList = computed<LowStockItem[]>(() => {
    return this.stats()?.lowStockItemsList ?? [];
  });

  protected readonly statusEntries = computed<StatusBreakdownEntry[]>(() => {
    const s = this.stats();
    if (!s?.statusBreakdown) return [];
    const entries = Object.entries(s.statusBreakdown) as [string, number][];
    const total = entries.reduce((sum, [, c]) => sum + c, 0);
    return entries
      .map(([status, count]: [string, number]) => ({
        status,
        count,
        pct: total > 0 ? Math.round((count / total) * 100) : 0,
      }))
      .sort((a, b) => b.count - a.count);
  });

  protected readonly maxDailyRevenue = computed<number>(() => {
    const t = this.trend();
    if (!t?.dailySeries) return 0;
    return Math.max(...t.dailySeries.map((d) => Number(d.revenue)), 1);
  });

  protected barHeight(point: DayRevenuePoint): number {
    const max = this.maxDailyRevenue();
    const v = Number(point.revenue);
    if (!isFinite(v) || max === 0) return 0;
    return Math.max(2, Math.round((v / max) * 100));
  }

  protected formatDayLabel(date: string): string {
    const d = new Date(date + 'T00:00:00Z');
    return d.toLocaleDateString('en-US', { weekday: 'short', timeZone: 'UTC' });
  }

  protected formatMoney(v: string): string {
    const num = Number(v);
    if (!isFinite(num)) return v;
    return new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(num);
  }

  protected statusClass(status: string): string {
    return status.toLowerCase().replace(/_/g, '-');
  }

  protected abs(value: number | null | undefined): number {
    return Math.abs(value ?? 0);
  }

  protected humanizeStatus(status: string): string {
    return status.replace(/_/g, ' ').toLowerCase();
  }

  ngOnInit(): void {
    this.statsService.getStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
      },
      error: (err: NormalizedApiError) => {
        this.error.set(err.message || 'Could not load dashboard stats.');
        this.loading.set(false);
      },
    });
  }
}
