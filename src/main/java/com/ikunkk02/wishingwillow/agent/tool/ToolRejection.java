package com.ikunkk02.wishingwillow.agent.tool;

public record ToolRejection(String value, String code, String reason) {
    public ToolRejection {
        value = value == null ? "" : value;
        code = code == null ? "" : code;
        reason = reason == null ? "" : reason;
    }
}
