package com.ikunkk02.wishingwillow.ai;

public record AiConnectionResult(boolean success, AiErrorCategory errorCategory, int httpStatus,
                                 ToolCallingSupport toolCallingSupport) {
    public static AiConnectionResult successful() {
        return successful(ToolCallingSupport.UNKNOWN);
    }

    public static AiConnectionResult successful(ToolCallingSupport support) {
        return new AiConnectionResult(true, AiErrorCategory.NONE, 200, support);
    }

    public static AiConnectionResult failure(AiErrorCategory category, int status) {
        return new AiConnectionResult(false, category, status, ToolCallingSupport.UNKNOWN);
    }
}
