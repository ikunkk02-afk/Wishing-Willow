package com.ikunkk02.wishingwillow.wish;

public enum WishRejectionReason {
    NONE,
    EMPTY,
    TOO_LONG,
    INVALID_CHARACTERS,
    AI_NOT_CONFIGURED,
    NOT_HOLDING,
    BUSY,
    DUPLICATE,
    COOLDOWN,
    INTERRUPTED,
    TIMEOUT
}
