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
        WishSkillType type,
        Set<String> requiredActions,
        Set<String> recommendedActions,
        List<ActionRequirementGroup> requirementGroups,
        String parameterTemplate,
        List<String> examples,
        Duration timeout
) {
    /** Source-compatible constructor for existing recipe-style skill definitions. */
    public WishSkillDefinition(String id, String description, Set<String> triggers,
                               Set<String> requiredActions, String parameterTemplate,
                               List<String> examples, Duration timeout) {
        this(id, description, triggers, WishSkillType.RECIPE, requiredActions,
                Set.of(), List.of(), parameterTemplate, examples, timeout);
    }

    public WishSkillDefinition {
        id = Objects.requireNonNull(id).strip();
        description = Objects.requireNonNull(description).strip();
        triggers = Set.copyOf(triggers);
        type = Objects.requireNonNull(type);
        requiredActions = Set.copyOf(requiredActions);
        recommendedActions = Set.copyOf(recommendedActions);
        requirementGroups = List.copyOf(requirementGroups);
        parameterTemplate = Objects.requireNonNullElse(parameterTemplate, "").strip();
        examples = List.copyOf(examples);
        timeout = Objects.requireNonNull(timeout);
        if (!id.matches("[a-z][a-z0-9_]{0,63}") || timeout.isZero() || timeout.isNegative()
                || requiredActions.stream().anyMatch(WishSkillDefinition::invalidActionId)
                || recommendedActions.stream().anyMatch(WishSkillDefinition::invalidActionId)) {
            throw new IllegalArgumentException("INVALID_SKILL_DEFINITION");
        }
    }

    private static boolean invalidActionId(String action) {
        return action == null || !action.matches("[a-z][a-z0-9_]{0,63}");
    }
}
