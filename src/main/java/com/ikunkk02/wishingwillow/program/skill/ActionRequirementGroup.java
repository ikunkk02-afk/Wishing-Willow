package com.ikunkk02.wishingwillow.program.skill;

import java.util.Objects;
import java.util.Set;

/** One composable action constraint attached to a skill. */
public record ActionRequirementGroup(RequirementMode mode, Set<String> actions) {
    public ActionRequirementGroup {
        mode = Objects.requireNonNull(mode);
        actions = Set.copyOf(actions);
        if (actions.isEmpty() || actions.stream().anyMatch(ActionRequirementGroup::invalidActionId)) {
            throw new IllegalArgumentException("INVALID_SKILL_REQUIREMENT_GROUP");
        }
    }

    public boolean satisfiedBy(Set<String> used) {
        return mode == RequirementMode.ALL_OF
                ? used.containsAll(actions)
                : actions.stream().anyMatch(used::contains);
    }

    private static boolean invalidActionId(String action) {
        return action == null || !action.matches("[a-z][a-z0-9_]{0,63}");
    }
}