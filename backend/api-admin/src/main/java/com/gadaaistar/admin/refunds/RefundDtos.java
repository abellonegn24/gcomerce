package com.gadaaistar.admin.refunds;

import java.math.BigDecimal;
import java.time.Instant;

import com.gadaaistar.persistence.order.OrderRefund;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request + response records for the admin refund API.
 *
 * <p>Only the {@link CreateRefundRequest} is externally callable — the
 * response shape is embedded in both the create-response and the admin
 * order-detail response.
 */
public final class RefundDtos {

    private RefundDtos() {}

    /**
     * Body for {@code POST /orders/{uuid}/refunds}. Amount is validated
     * against {@code order.grandTotal - order.refundedTotal} in the
     * service; validating strictly here would duplicate that check and
     * mask the more actionable REFUND_EXCEEDS_REMAINING error message.
     */
    public record CreateRefundRequest(
            @NotNull @DecimalMin(value = "0.01",
                    message = "Refund amount must be at least 0.01.") BigDecimal amount,
            @NotNull @Size(min = 3, max = 1000,
                    message = "Refund reason must be 3–1000 characters.") String reason,
            /**
             * When true, skip the gateway call entirely and record the row
             * as {@code MANUAL}. Used when the admin already refunded the
             * customer out-of-band (e.g. cash at pickup, wire transfer)
             * and only needs the audit + status transition side effects.
             * Default false.
             */
            Boolean manual
    ) {}

    /**
     * One entry in the audit list. Same shape whether the refund succeeded
     * or failed — the front-end colours the row by {@link #status}.
     */
    public record RefundEntry(
            String uuid,
            BigDecimal amount,
            String reason,
            OrderRefund.Status status,
            String gatewayProvider,
            String gatewayReference,
            String gatewayErrorCode,
            String gatewayErrorMessage,
            Long initiatedByAdminId,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
