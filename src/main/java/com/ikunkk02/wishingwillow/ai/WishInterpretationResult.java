package com.ikunkk02.wishingwillow.ai;

import javax.annotation.Nullable;

public record WishInterpretationResult(
        InterpretationState state,
        AiErrorCategory errorCategory,
        int httpStatus,
        @Nullable WishInterpretation interpretation
) {
    public static WishInterpretationResult success(WishInterpretation interpretation) {
        return new WishInterpretationResult(InterpretationState.SUCCESS, AiErrorCategory.NONE, 200, interpretation);
    }

    public static WishInterpretationResult requestFailure(AiErrorCategory category, int status) {
        return new WishInterpretationResult(InterpretationState.AI_REQUEST_FAILED, category, status, null);
    }

    public static WishInterpretationResult invalidResponse() {
        return new WishInterpretationResult(
                InterpretationState.INVALID_RESPONSE,
                AiErrorCategory.MALFORMED_RESPONSE,
                200,
                null
        );
    }
}
