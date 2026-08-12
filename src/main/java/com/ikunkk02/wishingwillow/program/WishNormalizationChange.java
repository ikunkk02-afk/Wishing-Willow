package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonElement;

/** One auditable local change made to untrusted AI output. */
public record WishNormalizationChange(
        String action,
        String parameter,
        JsonElement original,
        JsonElement normalized,
        WishNormalizationReason reason
) {
    public WishNormalizationChange {
        action = action == null ? "" : action;
        parameter = parameter == null ? "" : parameter;
        original = original == null ? null : original.deepCopy();
        normalized = normalized == null ? null : normalized.deepCopy();
    }
}
