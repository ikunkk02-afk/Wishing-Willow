package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.planning.WishActionType;

import java.util.Objects;

/** One parsed Action DSL instruction. It is data only and can never carry a command string. */
public record DirectWishAction(
        WishActionType type,
        DirectWishTarget target,
        String resource,
        JsonObject parameters
) {
    public DirectWishAction {
        type = Objects.requireNonNull(type);
        target = Objects.requireNonNull(target);
        resource = Objects.requireNonNullElse(resource, "").strip();
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        if (resource.length() > 128) throw new IllegalArgumentException("INVALID_RESOURCE");
    }
}
