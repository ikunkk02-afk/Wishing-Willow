package com.ikunkk02.wishingwillow.client.ai;

/** Visual-only lifecycle. FINISHED never implies that the wish pipeline is terminal. */
enum WishCinematicState {
    NOT_STARTED,
    PLAYING,
    FINISHED,
    CANCELLED
}
