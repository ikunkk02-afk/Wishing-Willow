package com.ikunkk02.wishingwillow.program;

import java.util.List;
import java.util.Objects;

/**
 * A bounded, serializable program selected by the single AI Understanding request.
 * It contains no command strings or executable code.
 */
public record WishProgram(
        int schemaVersion,
        String goal,
        List<WishProgramAction> coreActions,
        List<WishProgramAction> presentationActions,
        String skill,
        String unknownCapability
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_ACTIONS = 32;

    public WishProgram {
        goal = Objects.requireNonNullElse(goal, "").strip();
        coreActions = List.copyOf(coreActions == null ? List.of() : coreActions);
        presentationActions = List.copyOf(presentationActions == null ? List.of() : presentationActions);
        skill = Objects.requireNonNullElse(skill, "").strip();
        unknownCapability = Objects.requireNonNullElse(unknownCapability, "").strip();
        if (schemaVersion != CURRENT_SCHEMA_VERSION || goal.isBlank() || goal.length() > 512
                || coreActions.size() + presentationActions.size() > MAX_ACTIONS
                || skill.length() > 64 || unknownCapability.length() > 256) {
            throw new IllegalArgumentException("INVALID_WISH_PROGRAM");
        }
        if (coreActions.isEmpty() && skill.isBlank() && unknownCapability.isBlank()) {
            throw new IllegalArgumentException("EMPTY_WISH_PROGRAM");
        }
    }

    public boolean requiresAgent() { return !unknownCapability.isBlank(); }
    public boolean usesSkill() { return !skill.isBlank(); }
}
