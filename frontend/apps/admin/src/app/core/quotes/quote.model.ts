export type QuoteStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'SENT'
  | 'ACCEPTED'
  | 'CONVERTED'
  | 'REJECTED'
  | 'EXPIRED';

export const QUOTE_STATUS_OPTIONS: Array<{ code: QuoteStatus; label: string }> = [
  { code: 'DRAFT',        label: 'Draft' },
  { code: 'SUBMITTED',    label: 'Submitted' },
  { code: 'UNDER_REVIEW', label: 'Under review' },
  { code: 'SENT',         label: 'Sent' },
  { code: 'ACCEPTED',     label: 'Accepted' },
  { code: 'CONVERTED',    label: 'Converted' },
  { code: 'REJECTED',     label: 'Rejected' },
  { code: 'EXPIRED',      label: 'Expired' },
];

export function quoteStatusVariant(s: QuoteStatus): string {
  switch (s) {
    case 'SUBMITTED':
    case 'UNDER_REVIEW':
      return 'pending';
    case 'SENT':
      return 'info';
    case 'ACCEPTED':
      return 'ok';
    case 'CONVERTED':
      return 'done';
    case 'REJECTED':
    case 'EXPIRED':
      return 'cancelled';
    default:
      return '';
  }
}

export interface QuoteLine {
  id: number;
  productId: number | null;
  sku: string;
  name: string;
  variantSku: string | null;
  variantName: string | null;
  quantity: string;
  proposedUnitPrice: string;
  lineTotal: string;
}

export interface QuoteSummary {
  id: number;
  uuid: string;
  status: QuoteStatus;
  currency: string;
  grandTotal: string;
  itemCount: number;
  createdAt: string;
  validUntil: string | null;
  customerEmail: string;
  customerName: string | null;
}

export interface QuoteDetail {
  id: number;
  uuid: string;
  status: QuoteStatus;
  currency: string;
  subtotal: string;
  taxTotal: string;
  shippingTotal: string;
  grandTotal: string;
  customerNotes: string | null;
  adminNotes: string | null;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
  customerId: number;
  customerEmail: string;
  customerName: string | null;
  items: QuoteLine[];
}

export interface PagedQuotes {
  items: QuoteSummary[];
  page: number;
  size: number;
  total: number;
}

export interface UpdateQuoteItemPayload {
  proposedUnitPrice: string;
}

export interface SendQuotePayload {
  adminNotes?: string;
  validForDays?: number;
  shippingTotal?: string;
  taxTotal?: string;
}

export interface RejectQuotePayload {
  adminNotes?: string;
}

export interface ConvertQuotePayload {
  shipToAddressId: number;
  billToAddressId?: number | null;
  shippingMethodId?: number | null;
  internalNotes?: string;
}

export interface ConvertedOrder {
  orderUuid: string;
  invoiceNumber: string;
  grandTotal: string;
  currency: string;
}
