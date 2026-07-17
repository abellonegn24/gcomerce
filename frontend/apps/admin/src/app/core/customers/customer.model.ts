/**
 * TS mirrors of the admin customer DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/customers/CustomersDtos.java
 */

export type CustomerType = 'B2C' | 'B2B';

export type CustomerStatus =
  | 'PENDING_VERIFICATION'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'DEACTIVATED';

// -----------------------------------------------------------------------------
// Wire types
// -----------------------------------------------------------------------------

export interface CustomerRow {
  id: number;
  email: string;
  displayName: string;
  customerType: CustomerType;
  status: CustomerStatus;
  groupCode: string | null;
  preferredCurrency: string;
  emailVerified: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface CustomerAddressDto {
  id: number;
  addressType: 'BILLING' | 'SHIPPING' | 'BOTH';
  isDefault: boolean;
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

export interface CustomerDetail {
  id: number;
  email: string;
  customerType: CustomerType;
  status: CustomerStatus;
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  companyTin: string | null;
  companyVatNumber: string | null;
  businessLicenseUrl: string | null;
  phone: string | null;
  preferredCurrency: string;
  preferredLocale: string;
  groupId: number | null;
  groupCode: string | null;
  groupName: string | null;
  emailVerified: boolean;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  addresses: CustomerAddressDto[];
  orderCount: number;
  /** Legal status codes the admin can move to right now, per backend state machine. */
  allowedNextStatuses: CustomerStatus[];
}

export interface PageMeta {
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
  hasPrev: boolean;
}

export interface PagedCustomers {
  items: CustomerRow[];
  page: PageMeta;
}

// -----------------------------------------------------------------------------
// Write requests
// -----------------------------------------------------------------------------

export interface ChangeStatusPayload {
  status: CustomerStatus;
  reason?: string;
}

export interface SetGroupPayload {
  groupId: number | null;
}

export interface CustomerActionResponse {
  customerId: number;
  status: CustomerStatus;
  message: string;
}

// -----------------------------------------------------------------------------
// UI helpers
// -----------------------------------------------------------------------------

export const CUSTOMER_STATUS_LABELS: Record<CustomerStatus, string> = {
  PENDING_VERIFICATION: 'Pending email verification',
  PENDING_APPROVAL:     'Pending B2B approval',
  ACTIVE:               'Active',
  SUSPENDED:            'Suspended',
  DEACTIVATED:          'Deactivated',
};

export type CustomerStatusVariant = 'ok' | 'pending' | 'warn' | 'off';

export function customerStatusVariant(status: CustomerStatus): CustomerStatusVariant {
  switch (status) {
    case 'ACTIVE':                return 'ok';
    case 'PENDING_VERIFICATION':
    case 'PENDING_APPROVAL':      return 'pending';
    case 'SUSPENDED':             return 'warn';
    case 'DEACTIVATED':           return 'off';
  }
}

export const CUSTOMER_STATUS_OPTIONS: Array<{ code: CustomerStatus; label: string }> = [
  { code: 'PENDING_VERIFICATION', label: CUSTOMER_STATUS_LABELS.PENDING_VERIFICATION },
  { code: 'PENDING_APPROVAL',     label: CUSTOMER_STATUS_LABELS.PENDING_APPROVAL },
  { code: 'ACTIVE',               label: CUSTOMER_STATUS_LABELS.ACTIVE },
  { code: 'SUSPENDED',            label: CUSTOMER_STATUS_LABELS.SUSPENDED },
  { code: 'DEACTIVATED',          label: CUSTOMER_STATUS_LABELS.DEACTIVATED },
];

export const CUSTOMER_TYPE_OPTIONS: Array<{ code: CustomerType; label: string }> = [
  { code: 'B2C', label: 'B2C (retail)' },
  { code: 'B2B', label: 'B2B (wholesale)' },
];
