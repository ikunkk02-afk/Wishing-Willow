package com.ikunkk02.wishingwillow.program;

/**
 * Stable error taxonomy for the native WishProgram runtime. Every failure on the new path
 * maps to one of these codes instead of an opaque RuntimeException.
 */
public enum WishProgramError {
    INVALID_PROGRAM,
    INVALID_ACTION,
    INVALID_PARAMETER,
    INVALID_REGISTRY,
    RESOURCE_KIND_MISMATCH,
    BUDGET_EXCEEDED,
    EXECUTION_DISABLED,
    UNSUPPORTED_ACTION,
    UNKNOWN_CAPABILITY,
    TIMEOUT,
    UNKNOWN;

    public static WishProgramError fromMessage(String message) {
        if (message == null) return UNKNOWN;
        for (WishProgramError value : values()) {
            if (message.equals(value.name()) || message.startsWith(value.name() + ":")) return value;
        }
        if (message.contains("INVALID_WISH_PROGRAM")) return INVALID_PROGRAM;
        if (message.contains("UNKNOWN_ACTION")) return INVALID_ACTION;
        return UNKNOWN;
    }
}
