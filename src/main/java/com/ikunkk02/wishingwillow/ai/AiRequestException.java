package com.ikunkk02.wishingwillow.ai;

public final class AiRequestException extends RuntimeException {
    private final AiErrorCategory category;
    private final int httpStatus;
    private final boolean outputModeUnsupported;

    public AiRequestException(AiErrorCategory category, int httpStatus, boolean outputModeUnsupported) {
        super(category.name());
        this.category = category;
        this.httpStatus = httpStatus;
        this.outputModeUnsupported = outputModeUnsupported;
    }

    public AiRequestException(AiErrorCategory category, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
        this.httpStatus = 0;
        this.outputModeUnsupported = false;
    }

    public AiErrorCategory category() {
        return category;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean outputModeUnsupported() {
        return outputModeUnsupported;
    }
}
