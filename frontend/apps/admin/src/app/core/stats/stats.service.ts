import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface RevenueTrend {
  todayRevenue: string;
  yesterdayRevenue: string;
  monthRevenue: string;
  lastMonthRevenue: string;
  todayOrderCount: number;
  yesterdayOrderCount: number;
  dailySeries: DayRevenuePoint[];
}

export interface DayRevenuePoint {
  date: string;
  revenue: string;
  orderCount: number;
}

export interface RecentOrder {
  uuid: string;
  invoiceNumber: string;
  status: string;
  grandTotal: string;
  currency: string;
  customerName: string;
  itemCount: number;
  placedAt: string;
}

export interface LowStockItem {
  productSku: string;
  productName: string;
  variantName: string | null;
  available: string;
  restockThreshold: string;
}

export interface StatsDto {
  openOrders: number;
  pendingB2bApprovals: number;
  productsLive: number;
  monthRevenue: string;
  lowStockItems: number;
  activeCoupons: number;
  abandonedOrders: number;
  asOf: string;
  revenueTrend: RevenueTrend;
  statusBreakdown: Record<string, number>;
  recentOrders: RecentOrder[];
  lowStockItemsList: LowStockItem[];
}

@Injectable({ providedIn: 'root' })
export class StatsService {
  private readonly http = inject(HttpClient);

  getStats(): Observable<StatsDto> {
    return this.http.get<StatsDto>(`${environment.apiBaseUrl}/stats`);
  }
}
