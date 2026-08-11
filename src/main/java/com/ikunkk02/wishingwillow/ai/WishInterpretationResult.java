package com.ikunkk02.wishingwillow.ai;

import com.ikunkk02.wishingwillow.program.WishProgram;
import javax.annotation.Nullable;

public record WishInterpretationResult(
        InterpretationState state,
        AiErrorCategory errorCategory,
        int httpStatus,
        @Nullable WishInterpretation interpretation,
        @Nullable WishProgram program
) {
    public WishInterpretationResult(InterpretationState state, AiErrorCategory errorCategory,
                                    int httpStatus, @Nullable WishInterpretation interpretation) {
        this(state, errorCategory, httpStatus, interpretation, null);
    }

    public static WishInterpretationResult success(WishInterpretation interpretation) {
        return new WishInterpretationResult(InterpretationState.SUCCESS, AiErrorCategory.NONE, 200, interpretation, null);
    }

    public static WishInterpretationResult success(WishInterpretation interpretation, WishProgram program) {
        return new WishInterpretationResult(InterpretationState.SUCCESS, AiErrorCategory.NONE, 200, interpretation, program);
    }

    public static WishInterpretationResult requestFailure(AiErrorCategory category, int status) {
        return new WishInterpretationResult(InterpretationState.AI_REQUEST_FAILED, category, status, null, null);
    }

    public static WishInterpretationResult invalidResponse() {
        return new WishInterpretationResult(
                InterpretationState.INVALID_RESPONSE,
                AiErrorCategory.MALFORMED_RESPONSE,
                200,
                null,
                null
        );
    }
}
