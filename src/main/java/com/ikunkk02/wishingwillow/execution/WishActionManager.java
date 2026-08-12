package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.program.ProgramAction;
import com.ikunkk02.wishingwillow.program.ValidatedWishProgram;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramValidator;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import com.ikunkk02.wishingwillow.wish.WishPipelineAudit;
import com.ikunkk02.wishingwillow.wish.WishLifecycleLog;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.WishPipelineStatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the lifecycle boundary for wish execution.
 *
 * <p><b>NEW path:</b> {@link #startProgram(ServerPlayer, WishRecord, ValidatedWishProgram)}
 * starts a validated Wish Program on the native program executor. This is the ONLY entry used
 * for new wishes.</p>
 *
 * <p><b>LEGACY path:</b> {@link #start(ServerPlayer, WishPlan)} remains only for old saved
 * WishPlan data (save compatibility). New programs must never be lowered into a WishPlan.</p>
 */
public final class WishActionManager {
    private static final Map<UUID, UUID> ACTIVE_BY_PLAYER = new HashMap<>();

    private WishActionManager() { }

    /**
     * NEW path entry: starts a server-validated Wish Program natively. No legacy plan artifacts
     * are created; the program leaves are scheduled by {@link WishProgramExecutor}.
     */
    public static WishExecutionAcceptResult startProgram(ServerPlayer player, WishRecord wish,
                                                         ValidatedWishProgram validated) {
        if (player == null || wish == null || validated == null) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_PLAN,
                    "missing player, wish or validated program");
        }
        if (!wish.playerId().equals(player.getUUID())) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_OWNER,
                    "player does not own the wish session");
        }
        if (wish.executionId() != null && !wish.executionState().terminal()) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.PLAN_ALREADY_EXECUTED,
                    "session already has an active execution");
        }
        if (!WishExecutionConfig.ENABLED.get()) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.EXECUTION_DISABLED,
                    "server execution is disabled");
        }
        WishActionLoopDetector detector = new WishActionLoopDetector();
        for (ProgramAction leaf : validated.allLeaves()) {
            String signature = leaf.actionId() + ":" + leaf.parameters().toString();
            if (!detector.allow(signature)) {
                return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.VALIDATION_FAILED,
                        "LOOP_DETECTED action=" + leaf.actionId());
            }
        }

        WishExecutionSavedData data = WishExecutionSavedData.get(player.server);
        for (WishExecutionRecord active : data.all()) {
            if (active.ownerId().equals(player.getUUID()) && !active.state().terminal()) {
                WishExecutionManager.supersede(player.server, active.executionId());
                WishingWillow.LOGGER.info("Wish program superseded owner={} oldExecution={}",
                        player.getUUID(), active.executionId());
            }
        }

        long now = player.serverLevel().getGameTime();
        UUID programId = UUID.randomUUID();
        WishExecutionRecord record = new WishExecutionRecord(programId, programId,
                wish.sessionId(), player.getUUID(), validated.leafCount(), now,
                ExecutionSource.WISH_PROGRAM, validated.coreActions().size());
        if (!data.add(record)) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.DUPLICATE_EXECUTION,
                    "execution index rejected duplicate");
        }
        WishSavedData.get(player.server).update(wish.withExecution(record.executionId(),
                record.state(), WishExecutionAcceptError.NONE, ""));
        WishProgramExecutor.schedule(player.server, record, validated, now);
        ACTIVE_BY_PLAYER.put(player.getUUID(), record.executionId());
        WishPipelineAudit.success(wish.sessionId(), "PROGRAM_ACCEPT",
                "program=" + programId + " execution=" + record.executionId());
        WishLifecycleLog.event(wish.sessionId(), "PROGRAM_ACCEPT",
                "program=" + programId + " execution=" + record.executionId());
        ModNetworking.sendToPlayer(player,
                WishPipelineStatePacket.progress(wish.sessionId(), WishPipelineState.EXECUTING));
        WishLifecycleLog.event(wish.sessionId(), "EXECUTION_STARTED",
                "execution=" + record.executionId());
        WishingWillow.LOGGER.info(
                "Program execution started session={} program={} execution={} coreActions={} presentationActions={}",
                wish.sessionId(), programId, record.executionId(),
                validated.coreActions().stream().map(ProgramAction::actionId).toList(),
                validated.presentationActions().stream().map(ProgramAction::actionId).toList());
        WishPipelineProbe.programExecution();
        return WishExecutionAcceptResult.accepted(record.executionId());
    }

    /**
     * Convenience NEW path entry: validates the program server-side and starts it against the
     * player's latest stored wish whose program matches. Intended for server-side callers that
     * already hold the final program (e.g. the packet handler uses the wish-aware variant).
     */
    public static WishExecutionAcceptResult startProgram(ServerPlayer player, WishProgram program) {
        if (player == null || program == null) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_PLAN,
                    "missing player or program");
        }
        WishRecord latest = WishSavedData.get(player.server).getLatest(player.getUUID());
        if (latest == null || latest.program() == null
                || !WishProgramJsonEquals(latest.program(), program)) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_SESSION,
                    "no stored wish matches the submitted program");
        }
        try {
            ValidatedWishProgram validated = WishProgramValidator.validate(program,
                    new ForgeWishProgramResourceResolver(player.server));
            return startProgram(player, latest, validated);
        } catch (IllegalArgumentException error) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_PARAMETER,
                    "program validation failed: " + error.getMessage());
        }
    }

    private static boolean WishProgramJsonEquals(WishProgram left, WishProgram right) {
        return com.ikunkk02.wishingwillow.program.WishProgramJson.toJson(left)
                .equals(com.ikunkk02.wishingwillow.program.WishProgramJson.toJson(right));
    }

    /**
     * @deprecated Legacy WishPlan compatibility only. Do not use for WishProgram execution.
     * New wishes must call {@link #startProgram(ServerPlayer, WishRecord, ValidatedWishProgram)}.
     */
    @Deprecated
    public static WishExecutionAcceptResult start(ServerPlayer player, WishPlan plan) {
        if (player == null || plan == null) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_PLAN, "missing player or plan");
        }
        WishActionLoopDetector detector = new WishActionLoopDetector();
        for (com.ikunkk02.wishingwillow.planning.WishPlanStep step : plan.steps()) {
            String signature = step.action().name() + ":" + step.target().name() + ":"
                    + step.parameters().toString();
            if (!detector.allow(signature)) {
                return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.VALIDATION_FAILED,
                        "LOOP_DETECTED action=" + step.action());
            }
        }

        WishExecutionSavedData data = WishExecutionSavedData.get(player.server);
        for (WishExecutionRecord active : data.all()) {
            if (active.ownerId().equals(player.getUUID()) && !active.state().terminal()) {
                WishExecutionManager.supersede(player.server, active.executionId());
                WishingWillow.LOGGER.info("Wish program superseded owner={} oldExecution={}",
                        player.getUUID(), active.executionId());
            }
        }
        WishExecutionAcceptResult accepted = WishExecutionManager.accept(player, plan);
        if (accepted.accepted() && accepted.executionId() != null) {
            ACTIVE_BY_PLAYER.put(player.getUUID(), accepted.executionId());
        }
        return accepted;
    }

    public static boolean cancel(ServerPlayer player) {
        UUID execution = player == null ? null : ACTIVE_BY_PLAYER.remove(player.getUUID());
        return execution != null && WishExecutionManager.cancel(player.server, execution);
    }
}
