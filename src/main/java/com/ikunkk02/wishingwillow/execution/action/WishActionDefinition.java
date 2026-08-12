package com.ikunkk02.wishingwillow.execution.action;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Single source of truth for one executable Minecraft primitive.
 *
 * <p>The AI catalog, strict parameter boundary, runtime watchdog and debug commands all consume this
 * definition. Flow primitives have no legacy executor because the program compiler expands them before
 * Minecraft execution.</p>
 */
public record WishActionDefinition(
        String id,
        String description,
        JsonObject parameterSchema,
        Set<WishCapability> capabilities,
        @Nullable RegistryEntryType resourceKind,
        String resourceParameter,
        @Nullable WishActionType legacyType,
        @Nullable WishActionExecutor executor,
        Duration timeout,
        String resultType,
        boolean flowControl
) {
    public WishActionDefinition {
        id = Objects.requireNonNull(id).strip();
        description = Objects.requireNonNull(description).strip();
        parameterSchema = Objects.requireNonNull(parameterSchema).deepCopy();
        capabilities = Set.copyOf(capabilities);
        resourceParameter = Objects.requireNonNull(resourceParameter).strip();
        timeout = Objects.requireNonNull(timeout);
        resultType = Objects.requireNonNull(resultType).strip();
        if (!id.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("INVALID_ACTION_ID");
        if (description.isBlank() || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("INVALID_ACTION_DEFINITION");
        }
        if (!flowControl && (legacyType == null || executor == null)) {
            throw new IllegalArgumentException("ACTION_EXECUTOR_REQUIRED");
        }
        if ((resourceKind == null) != resourceParameter.isBlank()
                || !resourceParameter.isBlank() && !resourceParameter.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("INVALID_ACTION_RESOURCE_METADATA");
        }
    }

    @Override
    public JsonObject parameterSchema() {
        return parameterSchema.deepCopy();
    }
}
