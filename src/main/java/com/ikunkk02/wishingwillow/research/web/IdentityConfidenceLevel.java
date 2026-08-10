package com.ikunkk02.wishingwillow.research.web;

public enum IdentityConfidenceLevel {
    CONFIRMED,
    PROBABLE,
    POSSIBLE,
    UNRESOLVED;

    public static IdentityConfidenceLevel from(double confidence) {
        if (confidence >= 0.90) return CONFIRMED;
        if (confidence >= 0.75) return PROBABLE;
        if (confidence >= 0.55) return POSSIBLE;
        return UNRESOLVED;
    }
}
