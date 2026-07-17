/**
 * TS mirrors of the admin refund DTOs.
 * See @/backend/api-admin/src/main/java/com/gadaaistar/admin/refunds/RefundDtos.java
 *
 * A refund attempt is append-only. On success, money has been sent back
 * to the customer (via CyberSource) or the admin recorded a MANUAL row
 * because they refunded out-of-band. On failure the row stays for the
 * audit trail and support can retry from the same UI.
 */
export type RefundStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';

export interface RefundEntry {
  uuid: string;
  amount: string | number;
  reason: string;
  status: RefundStatus;
  gatewayProvider: string | null;
  gatewayReference: string | null;
  gatewayErrorCode: string | null;
  gatewayErrorMessage: string | null;
  initiatedByAdminId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRefundPayload {
  amount: string | number;
  reason: string;
  /** Skip the CyberSource call and record the row as MANUAL. Default false. */
  manual?: boolean;
}
