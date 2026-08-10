package com.ikunkk02.wishingwillow.research.web;

import java.util.List;

public record ModIdentityResolution(
        IdentityConfidenceLevel level,
        double confidence,
        String selectedUrl,
        String selectedTitle,
        List<ModIdentityCandidate> candidates,
        String reason,
        long resolvedAt
) {
    public ModIdentityResolution {
        level = level == null ? IdentityConfidenceLevel.UNRESOLVED : level;
        selectedUrl = selectedUrl == null ? "" : selectedUrl.strip();
        selectedTitle = selectedTitle == null ? "" : selectedTitle.strip();
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        reason = reason == null ? "" : reason.strip();
    }

    public static ModIdentityResolution unresolved(String reason) {
        return new ModIdentityResolution(IdentityConfidenceLevel.UNRESOLVED, 0.0,
                "", "", List.of(), reason, System.currentTimeMillis());
    }
}
