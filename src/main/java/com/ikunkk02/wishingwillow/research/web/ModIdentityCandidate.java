package com.ikunkk02.wishingwillow.research.web;

import java.util.List;

public record ModIdentityCandidate(
        WebSearchResult result,
        double confidence,
        IdentityConfidenceLevel level,
        List<IdentityMatchFactor> factors,
        boolean rejected,
        String rejectionReason
) {
    public ModIdentityCandidate {
        factors = List.copyOf(factors == null ? List.of() : factors);
        rejectionReason = rejectionReason == null ? "" : rejectionReason.strip();
    }
}
