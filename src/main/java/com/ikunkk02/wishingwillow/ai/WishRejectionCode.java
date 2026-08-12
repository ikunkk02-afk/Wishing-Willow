package com.ikunkk02.wishingwillow.ai;

/** Stable, player-safe reasons for declining a wish before world side effects begin. */
public enum WishRejectionCode {
    NONE,
    OUT_OF_GAME_SCOPE,
    EXTERNAL_SYSTEM_ACCESS,
    ARBITRARY_CODE_EXECUTION,
    PERMISSION_ESCALATION,
    UNSAFE_SERVER_OPERATION,
    RESOURCE_ABUSE,
    UNSUPPORTED_CAPABILITY,
    CONTRADICTORY_REQUEST,
    SERVER_POLICY
}
