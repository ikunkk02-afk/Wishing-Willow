package com.ikunkk02.wishingwillow.program.skill;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Metadata-first reusable composition of primitive actions. */
public record WishSkillDefinition(
        String id,
        String description,
        Set<String> triggers,
        Set<String> requiredActions,
        String parameterTemplate,
        List<String> examples,
        Duration timeout
) {
    public WishSkillDefinition {
        id = Objects.requireNonNull(id).strip();
        description = Objects.requireNonNull(description).strip();
        triggers = Set.copyOf(triggers);
        requiredActions = Set.copyOf(requiredActions);
        parameterTemplate = Objects.requireNonNullElse(parameterTemplate, "").strip();
        examples = List.copyOf(examples);
        timeout = Objects.requireNonNull(timeout);
        if (!id.matches("[a-z][a-z0-9_]{0,63}") || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("INVALID_SKILL_DEFINITION");
        }
    }
}
