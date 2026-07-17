/**
 * TypeScript mirrors of the backend {@code AdminUsersDtos} shape.
 * See @/api-admin/src/main/java/com/gadaaistar/admin/admins/AdminUsersDtos.java
 */
export interface AdminUserSummary {
  id: number;
  email: string;
  fullName: string;
  role: string | null;
  status: string;
  mfaEnrolled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AdminUsersListResponse {
  items: AdminUserSummary[];
}

export interface ResetMfaResponse {
  adminUserId: number;
  email: string;
  status: string;
  message: string;
}
