/**
 * TypeScript mirrors of the backend {@code AdminAuthDtos} record hierarchy.
 * Admin sign-in is a two-step MFA flow — see AdminAuthService for how these
 * types thread together at runtime.
 */

export type LoginState = 'MFA_REQUIRED' | 'MFA_ENROLLMENT_REQUIRED';

export interface AdminUserDto {
  id: number;
  email: string;
  fullName: string;
  role: string | null;
  status: string;
  mfaEnrolled: boolean;
  lastLoginAt: string | null;
}

export interface MfaSetup {
  secretBase32: string;
  otpauthUri: string;
  issuer: string;
}

export interface LoginChallengeResponse {
  state: LoginState;
  challengeToken: string;
  challengeExpiresAt: string;
  setup: MfaSetup | null;
}

export interface AuthResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  admin: AdminUserDto;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface VerifyMfaPayload {
  challengeToken: string;
  code: string;
}

/** Session snapshot persisted to localStorage. */
export interface StoredAdminSession {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  admin: AdminUserDto;
}

/** Ephemeral state kept between /login (step 1) and /verify-mfa (step 2). */
export interface PendingMfaChallenge {
  state: LoginState;
  challengeToken: string;
  challengeExpiresAt: string;
  email: string;
  setup: MfaSetup | null;
}
