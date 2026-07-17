/**
 * TypeScript mirrors of the backend {@code AuthDtos} record hierarchy.
 * Kept intentionally narrow to what the client actually reads.
 */

export type CustomerType = 'B2C' | 'B2B';

export type CustomerStatus =
  | 'PENDING_VERIFICATION'
  | 'PENDING_APPROVAL'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'DEACTIVATED';

export interface CustomerDto {
  id: number;
  email: string;
  customerType: CustomerType;
  status: CustomerStatus;
  firstName: string | null;
  lastName: string | null;
  companyName: string | null;
  companyTin: string | null;
  phone: string | null;
  preferredCurrency: string;
  preferredLocale: string;
  customerGroupCode: string | null;
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  customer: CustomerDto;
}

export interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  customerType?: CustomerType;
  companyName?: string;
  companyTin?: string;
  companyVatNumber?: string;
  phone?: string;
  preferredCurrency?: 'USD' | 'EUR' | 'GBP';
  preferredLocale?: string;
  customerGroupCode?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

/** Ack envelope returned by /forgot-password and /resend-verification. */
export interface AckResponse {
  status: string;
  message: string;
}

/** Body for PATCH /auth/me — every field is optional (partial update). */
export interface UpdateProfilePayload {
  firstName?: string;
  lastName?: string;
  companyName?: string;
  companyTin?: string;
  companyVatNumber?: string;
  phone?: string;
  preferredCurrency?: 'USD' | 'EUR' | 'GBP';
  preferredLocale?: string;
}

/** Body for POST /auth/me/change-password. */
export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

/**
 * Session snapshot persisted to localStorage. `accessToken` is a JWT and
 * `refreshToken` is the {id}.{secret} tuple issued by the backend.
 */
export interface StoredSession {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  customer: CustomerDto;
}
