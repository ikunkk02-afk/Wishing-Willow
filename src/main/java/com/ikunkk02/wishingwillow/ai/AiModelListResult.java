package com.ikunkk02.wishingwillow.ai;

import java.util.List;

public record AiModelListResult(boolean supported, List<String> models, AiErrorCategory errorCategory, int httpStatus) {
    public AiModelListResult {
        models = List.copyOf(models);
    }

    public static AiModelListResult success(List<String> models) {
        return new AiModelListResult(true, models, AiErrorCategory.NONE, 200);
    }

    public static AiModelListResult unsupported(int status) {
        return new AiModelListResult(false, List.of(), AiErrorCategory.UNSUPPORTED_FEATURE, status);
    }

    public static AiModelListResult failure(AiErrorCategory category, int status) {
        return new AiModelListResult(true, List.of(), category, status);
    }
}
