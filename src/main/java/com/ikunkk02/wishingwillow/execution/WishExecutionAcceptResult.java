package com.ikunkk02.wishingwillow.execution;

import javax.annotation.Nullable;
import java.util.UUID;

public record WishExecutionAcceptResult(
        boolean accepted,
        WishExecutionAcceptError error,
        @Nullable UUID executionId,
        String detail
) {
    public WishExecutionAcceptResult {
        error = error == null ? WishExecutionAcceptError.UNKNOWN : error;
        detail = safe(detail);
    }

    public static WishExecutionAcceptResult accepted(UUID executionId) {
        return new WishExecutionAcceptResult(true, WishExecutionAcceptError.NONE, executionId, "");
    }

    public static WishExecutionAcceptResult alreadyAccepted(UUID executionId) {
        return new WishExecutionAcceptResult(true, WishExecutionAcceptError.ALREADY_ACCEPTED,
                executionId, "Execution already exists for this wish plan");
    }

    public static WishExecutionAcceptResult rejected(WishExecutionAcceptError error, String detail) {
        return new WishExecutionAcceptResult(false, error, null, detail);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        return clean.length() <= 256 ? clean : clean.substring(0, 256);
    }
}
