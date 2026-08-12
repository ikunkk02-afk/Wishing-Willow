package com.ikunkk02.wishingwillow.wish;

/**
 * Physical willow/cinematic lifecycle only. {@link #FINISHED} means the visual wish sequence
 * ended; it is never evidence that AI, planning, research, or execution completed.
 */
public enum WishState {
    REQUESTED,
    ANIMATING,
    SNAPPED,
    FINISHED,
    CANCELLED
}
