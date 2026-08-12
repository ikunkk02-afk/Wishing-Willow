package com.ikunkk02.wishingwillow.program;

/** Stable reasons emitted by schema-driven Wish Program normalization. */
public enum WishNormalizationReason {
    JSON_RECOVERY,
    ROOT_DEFAULT,
    ROOT_FIELD_MERGED,
    METADATA_COERCION,
    ACTION_ARRAY_WRAPPED,
    STRING_NORMALIZATION,
    ACTION_NAME_NORMALIZATION,
    TYPE_COERCION,
    MIN_CLAMP,
    MAX_CLAMP,
    ENUM_NORMALIZATION,
    DEFAULT_APPLIED,
    UNKNOWN_FIELD_IGNORED,
    ACTION_DROPPED,
    ACTION_BUDGET_TRUNCATED
}
