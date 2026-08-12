package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured reason why local normalization could not safely recover a candidate. */
public record WishProgramValidationIssue(
        String validationError,
        String action,
        String parameter,
        JsonElement provided,
        JsonElement allowedMin,
        JsonElement allowedMax,
        boolean safetyCritical,
        String detail
) {
    public WishProgramValidationIssue {
        validationError = validationError == null || validationError.isBlank()
                ? "INVALID_WISH_PROGRAM:UNKNOWN" : validationError;
        action = action == null ? "" : action;
        parameter = parameter == null ? "" : parameter;
        provided = provided == null ? null : provided.deepCopy();
        allowedMin = allowedMin == null ? null : allowedMin.deepCopy();
        allowedMax = allowedMax == null ? null : allowedMax.deepCopy();
        detail = detail == null ? "" : detail;
    }

    public Map<String, Object> repairContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("validation_error", validationError);
        if (!action.isBlank()) context.put("action", action);
        if (!parameter.isBlank()) context.put("parameter", parameter);
        if (provided != null) context.put("provided", jsonValue(provided));
        if (allowedMin != null) context.put("allowed_min", jsonValue(allowedMin));
        if (allowedMax != null) context.put("allowed_max", jsonValue(allowedMax));
        if (!detail.isBlank()) context.put("detail", detail);
        return context;
    }

    private static Object jsonValue(JsonElement value) {
        if (!value.isJsonPrimitive()) return value.toString();
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsNumber();
        return primitive.getAsString();
    }
}
