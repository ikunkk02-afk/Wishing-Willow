package com.ikunkk02.wishingwillow.execution.action;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.planning.CandidateReference;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.program.ProgramAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Native execution context for action executors. It is intentionally independent of the legacy
 * {@link WishPlan}/{@link WishPlanStep} model: executors only see the action id, canonical
 * parameters, target, capability, candidate and the runtime execution record.
 *
 * <p>Legacy saved plans are adapted into this shape ({@link #legacy}) — OLD to NEW. New Wish
 * Programs construct contexts directly from {@link ProgramAction} leaves via {@link #program}.</p>
 */
public record WishExecutionContext(
        ServerLevel level,
        @Nullable ServerPlayer player,
        UUID wishSessionId,
        String actionId,
        JsonObject parameters,
        WishTargetType target,
        @Nullable WishCapability capability,
        int stepIndex,
        @Nullable CandidateReference candidate,
        WishExecutionRecord execution
) {
    public WishExecutionContext {
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        actionId = actionId == null ? "" : actionId;
    }

    @Override
    public JsonObject parameters() { return parameters.deepCopy(); }

    /** OLD-to-NEW adapter: builds a native context from a legacy saved plan step. */
    public static WishExecutionContext legacy(ServerLevel level, @Nullable ServerPlayer player,
                                              WishPlan plan, WishPlanStep step,
                                              WishExecutionRecord execution) {
        WishActionDefinition definition = WishActionRegistry.defaults().definition(step.action());
        return new WishExecutionContext(level, player, plan.wishSessionId(),
                definition == null ? step.action().name() : definition.id(),
                step.parameters(), step.target(), step.capability(), step.stepIndex(),
                step.candidateReference(), execution);
    }

    /** NEW path: builds a native context directly from a flattened program action leaf. */
    public static WishExecutionContext program(ServerLevel level, @Nullable ServerPlayer player,
                                               WishExecutionRecord execution,
                                               ProgramAction action) {
        return new WishExecutionContext(level, player, execution.wishSessionId(), action.actionId(),
                action.parameters(), action.target(), action.capability(), action.stepIndex(),
                action.candidate(), execution);
    }
}
