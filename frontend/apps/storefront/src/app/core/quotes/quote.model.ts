export type QuoteStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'SENT'
  | 'ACCEPTED'
  | 'CONVERTED'
  | 'REJECTED'
  | 'EXPIRED';

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
  items: QuoteLine[];
}

export interface PagedQuotes {
  items: QuoteSummary[];
  page: number;
  size: number;
  total: number;
}

export interface RequestQuotePayload {
  customerNotes?: string;
}
