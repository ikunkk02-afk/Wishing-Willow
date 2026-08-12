package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramAction;

import java.util.ArrayList;
import java.util.List;

/**
 * OLD-to-NEW adapter: converts a legacy agent-produced {@link WishPlanDraft} into a
 * {@link WishProgram} so the Complex Agent result runs on the same native program executor as
 * every other wish.
 *
 * <p>This is the only permitted legacy-adjacent conversion and its direction is strictly
 * OLD → NEW. The reverse direction (NEW WishProgram → legacy WishPlan) is forbidden and never
 * implemented.</p>
 */
public final class LegacyPlanToProgramAdapter {
    private static final WishActionRegistry ACTIONS = WishActionRegistry.defaults();

    private LegacyPlanToProgramAdapter() { }

    public static WishProgram toProgram(WishPlanDraft draft, String goal) {
        List<WishProgramAction> core = new ArrayList<>();
        List<WishProgramAction> presentation = new ArrayList<>();
        for (WishPlanStep step : draft.steps()) {
            WishActionDefinition definition = ACTIONS.definition(step.action());
            if (definition == null || definition.flowControl()) continue;
            JsonObject parameters = step.parameters().deepCopy();
            if (step.candidateReference() != null
                    && step.candidateReference().registryResource() != null) {
                String key = resourceKey(step);
                String id = step.candidateReference().registryResource().id();
                if (!key.isEmpty() && !parameters.has(key)) parameters.addProperty(key, id);
            } else if (step.action() == com.ikunkk02.wishingwillow.planning.WishActionType.START_PREDEFINED_EVENT
                    && step.candidateReference() != null
                    && !parameters.has("event")) {
                parameters.addProperty("event", step.candidateReference().featureName());
            }
            boolean presentationStep = step.selectionReason() != null
                    && step.selectionReason().contains("optional absurd presentation");
            (presentationStep ? presentation : core).add(
                    new WishProgramAction(definition.id(), parameters));
        }
        if (core.isEmpty()) {
            throw new IllegalArgumentException("AGENT_PROGRAM_EMPTY");
        }
        return new WishProgram(WishProgram.CURRENT_SCHEMA_VERSION,
                goal == null || goal.isBlank() ? "Agent-researched wish" : goal,
                List.copyOf(core), List.copyOf(presentation), "", "");
    }

    private static String resourceKey(WishPlanStep step) {
        return switch (step.action()) {
            case GIVE_ITEM, REMOVE_ITEM, ITEM_RAIN -> "item";
            case APPLY_EFFECT, REMOVE_EFFECT -> "effect";
            case SPAWN_ENTITY, DESPAWN_ENTITY -> "entity";
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER -> "block";
            case PLAY_SOUND -> "sound";
            case SPAWN_PARTICLE -> "particle";
            case TELEPORT -> "dimension";
            default -> "";
        };
    }
}
