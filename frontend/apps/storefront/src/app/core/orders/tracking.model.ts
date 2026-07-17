import { OrderStatus } from '../orders/order.model';

export interface TrackingLine {
  id: number;
  productId: number | null;
  sku: string;
  name: string;
  variantSku: string | null;
  variantName: string | null;
  primaryImageUrl: string | null;
  unitPrice: string;
  quantity: string;
  taxRate: string;
  taxAmount: string;
  lineTotal: string;
}

export interface TrackingStatusEvent {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  source: string | null;
  note: string | null;
  occurredAt: string;
}

export interface TrackingResponse {
  uuid: string;
  invoiceNumber: string;
  status: OrderStatus;
  currency: string;
  grandTotal: string;
  itemCount: number;
  placedAt: string;
  paidAt: string | null;
  shippingMethodName: string | null;
  items: TrackingLine[];
  statusHistory: TrackingStatusEvent[];
}
