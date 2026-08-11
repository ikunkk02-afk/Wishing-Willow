package com.ikunkk02.wishingwillow.contract;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record WishContract(
        WishContractType type,
        String requiredOutcome,
        List<WishHardConstraint> hardConstraints
) {
    public WishContract {
        type = Objects.requireNonNull(type);
        requiredOutcome = Objects.requireNonNullElse(requiredOutcome, "").strip();
        hardConstraints = List.copyOf(hardConstraints == null ? List.of() : hardConstraints);
        if (requiredOutcome.isEmpty() || requiredOutcome.length() > 1024 || hardConstraints.size() > 16) {
            throw new IllegalArgumentException("INVALID_WISH_CONTRACT");
        }
    }

    public Optional<WishHardConstraint> first(WishConstraintKind kind) {
        return hardConstraints.stream().filter(value -> value.kind() == kind).findFirst();
    }

    public Optional<String> semantic(WishConstraintKind kind) {
        return first(kind).map(WishHardConstraint::semantic).filter(value -> !value.isBlank());
    }

    public OptionalInt quantity(WishConstraintKind kind) {
        return first(kind).map(value -> OptionalInt.of(value.quantity())).orElseGet(OptionalInt::empty);
    }

    public boolean requires(WishConstraintKind kind) {
        return first(kind).map(WishHardConstraint::required).orElse(false);
    }

    public boolean requiresAiReview() {
        return hardConstraints.stream().anyMatch(value -> value.kind() == WishConstraintKind.CUSTOM_SEMANTIC);
    }

    public static WishContract legacy(String literalGoal) {
        return new WishContract(WishContractType.OTHER, literalGoal, List.of(
                new WishHardConstraint(WishConstraintKind.CUSTOM_SEMANTIC,
                        WishConstraintOperator.REQUIRED, "legacy_schema_v1", 0, 0, true)
        ));
    }
}
