package com.ikunkk02.wishingwillow.ai;

public record AiConnectionResult(boolean success, AiErrorCategory errorCategory, int httpStatus) {
    public static AiConnectionResult successful() {
        return new AiConnectionResult(true, AiErrorCategory.NONE, 200);
    }

    public static AiConnectionResult failure(AiErrorCategory category, int status) {
        return new AiConnectionResult(false, category, status);
    }
}
