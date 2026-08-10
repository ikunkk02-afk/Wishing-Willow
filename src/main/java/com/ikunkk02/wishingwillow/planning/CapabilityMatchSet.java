package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import java.util.List;

public record CapabilityMatchSet(WishCapability capability, MatchType quality, List<CapabilityCandidate> candidates) {
    public CapabilityMatchSet {
        candidates = List.copyOf(candidates);
    }

    public static CapabilityMatchSet unsatisfied(WishCapability capability) {
        return new CapabilityMatchSet(capability, MatchType.UNSATISFIED, List.of());
    }
}
