/**
 * TS mirrors of the admin order DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/orders/AdminOrderDtos.java
 */

import { RefundEntry } from './refund.model';

export type OrderStatus =
  | 'DRAFT'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'FULFILLING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

export interface OrderRow {
  id: number;
  uuid: string;
  invoiceNumber: string;
  status: OrderStatus;
  currency: string;
  grandTotal: string;
  customerEmail: string;
  customerName: string | null;
  itemCount: number;
  placedAt: string;
  paidAt: string | null;
}

export interface AddressSnapshot {
  fullName: string | null;
  company: string | null;
  streetLine1: string;
  streetLine2: string | null;
  city: string;
  region: string | null;
  postalCode: string | null;
  countryCode: string;
  phone: string | null;
}

export interface CustomerSnapshot {
  id: number;
  email: string;
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  phone: string | null;
}

export interface LineItem {
  id: number;
  productId: number | null;
  sku: string;
  name: string;
  variantSku: string | null;
  variantName: string | null;
  unitPrice: string;
  quantity: string;
  taxRate: string;
  taxAmount: string;
  lineTotal: string;
}

export interface OrderStatusEvent {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  source: string;
  actorId: number | null;
  note: string | null;
  occurredAt: string;
}

export interface OrderDetail {
  id: number;
  uuid: string;
  invoiceNumber: string;
  status: OrderStatus;
  currency: string;
  subtotal: string;
  taxTotal: string;
  shippingTotal: string;
  discountTotal: string;
  /** Cumulative amount refunded across SUCCEEDED refund attempts. */
  refundedTotal: string;
  grandTotal: string;
  customer: CustomerSnapshot;
  shipTo: AddressSnapshot;
  billTo: AddressSnapshot;
  /** Snapshot of the chosen shipping method (both null if pickup / no method). */
  shippingMethodCode: string | null;
  shippingMethodName: string | null;
  customerNotes: string | null;
  couponCode: string | null;
  couponDescription: string | null;
  couponDiscount: string;
  internalNotes: string | null;
  paymentReference: string | null;
  paymentProvider: string | null;
  paymentMethod: string | null;
  placedAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  items: LineItem[];
  statusHistory: OrderStatusEvent[];
  /** Refund history — includes both SUCCEEDED and FAILED attempts. */
  refunds: RefundEntry[];
}

export interface PagedOrders {
  items: OrderRow[];
  page: number;
  size: number;
  total: number;
}

export interface TransitionOrderPayload {
  next: OrderStatus;
  note?: string;
}

// -----------------------------------------------------------------------------
// Client-side helpers
// -----------------------------------------------------------------------------

/**
 * Legal next-state transitions, mirrored from
 * {@code OrderStatus.canTransitionTo} in the backend common module.
 *
 * Keep this in sync — the server will reject illegal transitions, but the
 * dropdown should not offer them either.
 */
export const ALLOWED_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  DRAFT:              ['PENDING_PAYMENT', 'CANCELLED'],
  PENDING_PAYMENT:    ['PAID', 'CANCELLED'],
  PAID:               ['FULFILLING', 'REFUNDED', 'CANCELLED'],
  FULFILLING:         ['SHIPPED', 'CANCELLED', 'REFUNDED'],
  SHIPPED:            ['DELIVERED', 'REFUNDED'],
  DELIVERED:          [],
  CANCELLED:          [],
  // Partial refunds keep the order live — admin can still finish
  // fulfilment or issue further refunds until the whole balance is
  // returned. Both routes go through the refund modal, not this dropdown.
  PARTIALLY_REFUNDED: [],
  REFUNDED:           [],
};

export const ORDER_STATUS_OPTIONS: Array<{ code: OrderStatus; label: string }> = [
  { code: 'DRAFT',              label: 'Draft' },
  { code: 'PENDING_PAYMENT',    label: 'Pending payment' },
  { code: 'PAID',               label: 'Paid' },
  { code: 'FULFILLING',         label: 'Fulfilling' },
  { code: 'SHIPPED',            label: 'Shipped' },
  { code: 'DELIVERED',          label: 'Delivered' },
  { code: 'CANCELLED',          label: 'Cancelled' },
  { code: 'PARTIALLY_REFUNDED', label: 'Partially refunded' },
  { code: 'REFUNDED',           label: 'Refunded' },
];

/** Colour hint used by the status pill component. */
export function orderStatusVariant(s: OrderStatus): 'ok' | 'done' | 'pending' | 'cancelled' | 'draft' {
  switch (s) {
    case 'PAID':
    case 'FULFILLING':
    case 'SHIPPED':          return 'ok';
    case 'DELIVERED':        return 'done';
    case 'PENDING_PAYMENT':  return 'pending';
    case 'CANCELLED':
    case 'PARTIALLY_REFUNDED':
    case 'REFUNDED':         return 'cancelled';
    case 'DRAFT':
    default:                 return 'draft';
  }
}
