package com.ikunkk02.wishingwillow.contract;

import java.util.Objects;

/**
 * Strict, provider-facing constraint shape. Every value slot is present so JSON-schema providers do not
 * need discriminator unions; {@link WishContractValidator} enforces which slot is meaningful for each kind.
 */
public record WishHardConstraint(
        WishConstraintKind kind,
        WishConstraintOperator operator,
        String semantic,
        int quantity,
        double amount,
        boolean required
) {
    public WishHardConstraint {
        kind = Objects.requireNonNull(kind);
        operator = Objects.requireNonNull(operator);
        semantic = Objects.requireNonNullElse(semantic, "").strip();
        if (semantic.length() > 128 || quantity < 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("INVALID_CONTRACT_CONSTRAINT");
        }
    }
}
