package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonObject;

import java.util.Objects;

/** One data-only invocation in a Wish Program. */
public record WishProgramAction(String action, JsonObject parameters) {
    public WishProgramAction {
        action = Objects.requireNonNull(action).strip();
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        if (!action.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("INVALID_PROGRAM_ACTION");
    }

    @Override
    public JsonObject parameters() { return parameters.deepCopy(); }

    public String normalizedSignature() {
        return action + "|" + WishProgramJson.canonical(parameters);
    }
}
