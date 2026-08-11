package com.ikunkk02.wishingwillow.execution;

import java.util.HashMap;
import java.util.Map;

/** Blocks identical action invocations after two attempts in one Wish Program. */
public final class WishActionLoopDetector {
    public static final int MAX_IDENTICAL_ATTEMPTS = 2;
    private final Map<String, Integer> attempts = new HashMap<>();

    public boolean allow(String normalizedSignature) {
        int next = attempts.merge(normalizedSignature, 1, Integer::sum);
        return next <= MAX_IDENTICAL_ATTEMPTS;
    }

    public int attempts(String normalizedSignature) {
        return attempts.getOrDefault(normalizedSignature, 0);
    }
}
