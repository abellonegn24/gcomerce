import { Address } from '../addresses/address.model';

export type OrderStatus =
  | 'DRAFT'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'FULFILLING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface OrderLine {
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

export interface OrderStatusEvent {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  source: string | null;
  note: string | null;
  occurredAt: string;
}

/** Compact list-view row. */
export interface OrderSummary {
  id: number;
  uuid: string;
  invoiceNumber: string;
  status: OrderStatus;
  currency: string;
  grandTotal: string;
  itemCount: number;
  placedAt: string;
  paidAt: string | null;
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
  grandTotal: string;
  shipTo: Address | null;
  billTo: Address | null;
  /** Snapshot of the chosen shipping method (both null when pickup / no method). */
  shippingMethodCode: string | null;
  shippingMethodName: string | null;
  customerNotes: string | null;
  couponCode: string | null;
  couponDescription: string | null;
  couponDiscount: string;
  paymentReference: string | null;
  paymentProvider: string | null;
  paymentMethod: string | null;
  placedAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  items: OrderLine[];
  statusHistory: OrderStatusEvent[];
}

export interface PagedOrders {
  items: OrderSummary[];
  page: number;
  size: number;
  total: number;
}

export interface TransactionSummary {
  id: number;
  uuid: string;
  invoiceNumber: string;
  status: OrderStatus;
  currency: string;
  grandTotal: string;
  itemCount: number;
  customerName: string | null;
  customerEmail: string | null;
  customerType: string | null;
  placedAt: string;
  paidAt: string | null;
}

export interface PagedTransactions {
  items: TransactionSummary[];
  page: number;
  size: number;
  total: number;
}

export interface PlaceOrderPayload {
  shipToAddressId: number;
  billToAddressId?: number | null;
  /**
   * Optional. Null / undefined means "no shipping method" — used for
   * pickup / FCA orders. When set, must be an active shipping method whose
   * currency matches the cart's currency; otherwise the server responds
   * with a 409 (SHIPPING_METHOD_INACTIVE / SHIPPING_METHOD_CURRENCY_MISMATCH).
   */
  shippingMethodId?: number | null;
  /** Optional coupon code. */
  couponCode?: string;
  customerNotes?: string;
  /** Payment method chosen at checkout: 'CYBERSOURCE' | 'MPGS'. */
  paymentMethod?: string;
}

export interface CancelOrderPayload {
  reason?: string;
}

/**
 * Payment payload returned by the backend. For CyberSource, this is a set
 * of signed form fields the browser POSTs to {@link gatewayUrl}. For MPGS,
 * {@link gatewayUrl} is the Checkout.js script URL and {@link fields}
 * contains the session config (merchantId, sessionId, successIndicator).
 */
export interface PaymentPayload {
  provider: 'CYBERSOURCE' | 'MPGS';
  gatewayUrl: string;
  fields: Record<string, string>;
}

/** Available payment method option returned by GET /me/payment-methods. */
export interface PaymentMethodOption {
  id: string;
  name: string;
  description: string;
  logoUrl: string | null;
}
