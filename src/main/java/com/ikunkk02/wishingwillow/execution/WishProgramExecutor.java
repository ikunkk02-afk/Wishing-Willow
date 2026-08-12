package com.ikunkk02.wishingwillow.execution;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.WishExecutionScheduler.StepKey;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.execution.action.WishExecutionContext;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.planning.WishTargetType;
import com.ikunkk02.wishingwillow.program.ProgramAction;
import com.ikunkk02.wishingwillow.program.ValidatedWishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramValidator;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishRejectionReason;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import com.ikunkk02.wishingwillow.wish.WishState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Native executor for Wish Programs on the NEW path.
 *
 * <p>It interprets the flattened {@link ProgramAction} leaves directly: bounded groups schedule
 * sequentially (parallel children share a group), delays resume after game ticks, core actions
 * determine the final program result, presentation failures never overturn a successful core.
 * Action execution goes through {@link WishActionRegistry} → {@link WishActionDefinition} →
 * executor, producing {@link WishActionResult} evidence. No {@code WishPlan}/{@code WishPlanStep}
 * is ever constructed.</p>
 *
 * <p>Timing budgets: per-action timeouts come from the action definition; the program watchdog
 * fails the whole program after 90s (60s for skill programs).</p>
 */
public final class WishProgramExecutor {
    /** Minimal delay between logic actions so entity state settles. */
    private static final int LOGIC_STEP_DELAY_TICKS = 2;
    /** Cinematic delay reserved for presentation (play_sound, particle, camera, etc). */
    private static final int PRESENTATION_START_DELAY_TICKS = 110;
    private static final long PROGRAM_TIMEOUT_TICKS = 1800L;   // 90s
    private static final long SKILL_TIMEOUT_TICKS = 1200L;     // 60s
    private static final Set<String> BOUNDED_WORLD_ACTIONS = Set.of(
            "place_block", "replace_blocks", "place_pattern", "spawn_falling_block",
            "spawn_item_rain", "create_structure");
    /** Actions that should run immediately (logic), not wait for cinematic pacing. */
    private static final Set<String> LOGIC_ACTIONS = Set.of(
            "spawn_entity", "follow_player", "avoid_player", "set_entity_target",
            "give_item", "apply_effect", "teleport_player", "modify_attribute",
            "change_ai", "set_world_time", "set_weather", "entity_attraction_aura");
    /** Actions that benefit from cinematic pacing (presentation). */
    private static final Set<String> PRESENTATION_ACTIONS = Set.of(
            "play_sound", "spawn_particle", "camera_effect", "screen_effect",
            "title", "animation", "lightning_visual", "spawn_falling_block",
            "spawn_item_rain");
    private static final WishActionRegistry ACTIONS = WishActionRegistry.defaults();

    private WishProgramExecutor() { }

    /** Schedules the first group of a freshly accepted program. */
    public static void schedule(MinecraftServer server, WishExecutionRecord record,
                                ValidatedWishProgram validated, long now) {
        record.transition(WishExecutionState.SCHEDULED, now);
        List<ProgramAction> leaves = validated.allLeaves();
        if (leaves.isEmpty()) {
            record.fail("EMPTY_PROGRAM", now);
            WishExecutionManager.changed(server, record);
            return;
        }
        int firstGroup = leaves.get(0).group();
        for (int index = 0; index < leaves.size(); index++) {
            ProgramAction leaf = leaves.get(index);
            if (leaf.group() != firstGroup) continue;
            WishStepExecution step = record.step(index);
            if (step == null || step.state() != WishStepExecutionState.PENDING) continue;
            StepKey key = new StepKey(record.executionId(), index);
            int delayTicks = stepDelayTicks(leaf);
            if (leaf.delayTicks() > 0) {
                long at = now + leaf.delayTicks();
                step.schedule(at);
                step.transition(WishStepExecutionState.WAITING_DELAY, now);
                WishExecutionManager.scheduler().delay(key, at);
            } else {
                step.transition(WishStepExecutionState.READY, now);
                step.schedule(now + delayTicks);
                WishExecutionManager.scheduler().delay(key, now + delayTicks);
            }
        }
        WishExecutionManager.changed(server, record);
    }

    /** Returns the appropriate step delay for the given action type. */
    private static int stepDelayTicks(ProgramAction leaf) {
        if (LOGIC_ACTIONS.contains(leaf.actionId())) return LOGIC_STEP_DELAY_TICKS;
        if (PRESENTATION_ACTIONS.contains(leaf.actionId())) return PRESENTATION_START_DELAY_TICKS;
        // Unknown actions: use minimal delay for safety but keep tight
        return LOGIC_STEP_DELAY_TICKS;
    }

    /** Recomputes validated leaves from the stored program (deterministic; re-resolves registries). */
    public static ValidatedWishProgram validated(MinecraftServer server, WishRecord wish) {
        return WishProgramValidator.validate(wish.program(),
                new ForgeWishProgramResourceResolver(server));
    }

    /** Runs one due program step: definition timeout, executor validate/execute, result handling. */
    public static void executeStep(MinecraftServer server, WishExecutionRecord record,
                                   int stepIndex, long now) {
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null || wish.program() == null) {
            staleStep(record, stepIndex, "PROGRAM_MISSING");
            finish(server, record, now);
            return;
        }
        ValidatedWishProgram validated;
        try {
            validated = validated(server, wish);
        } catch (IllegalArgumentException error) {
            markAllStale(record, "PROGRAM_REVALIDATION_FAILED " + error.getMessage());
            finish(server, record, now);
            return;
        }
        List<ProgramAction> leaves = validated.allLeaves();
        if (stepIndex < 0 || stepIndex >= leaves.size()) {
            markAllStale(record, "STEP_INDEX_OUT_OF_BOUNDS");
            finish(server, record, now);
            return;
        }
        ProgramAction leaf = leaves.get(stepIndex);
        WishStepExecution step = record.step(stepIndex);
        if (step == null || step.state().terminal()) return;
        WishActionDefinition definition = ACTIONS.find(leaf.actionId());
        if (definition == null || definition.executor() == null) {
            step.result(WishActionResult.failed("UNKNOWN_ACTION"));
            step.transition(WishStepExecutionState.FAILED, now);
            finish(server, record, now);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(record.ownerId());
        boolean needsPlayer = leaf.target() != WishTargetType.WORLD;
        if (player == null && needsPlayer) {
            if (step.targetDeadlineGameTime() > 0 && now >= step.targetDeadlineGameTime()) {
                step.transition(WishStepExecutionState.FAILED, now);
                step.result(WishActionResult.failed("TARGET_TIMEOUT"));
                finish(server, record, now);
                return;
            }
            step.transition(WishStepExecutionState.WAITING_TARGET, now);
            step.schedule(now + 20);
            WishExecutionManager.scheduler().delay(new StepKey(record.executionId(), stepIndex), now + 20);
            WishExecutionManager.changed(server, record);
            return;
        }
        ServerLevel level = player != null ? player.serverLevel()
                : WishExecutionManager.level(server, wish.dimension());
        if (level == null) {
            step.transition(WishStepExecutionState.STALE, now);
            step.result(WishActionResult.stale("LEVEL_NOT_FOUND"));
            finish(server, record, now);
            return;
        }
        long timeoutTicks = Math.max(1L, definition.timeout().toSeconds() * 20L);
        if (step.startedGameTime() >= 0 && now - step.startedGameTime() >= timeoutTicks) {
            step.result(WishActionResult.timeout("ACTION_TIMEOUT", step.affected()));
            step.transition(WishStepExecutionState.FAILED, now);
            WishExecutionAudit.transition(record, stepIndex, leaf.actionId(), "TIMEOUT",
                    "ACTION_TIMEOUT", step.affected());
            finish(server, record, now);
            return;
        }
        WishExecutionContext context = WishExecutionContext.program(level, player, record, leaf);
        WishActionResult validation = definition.executor().validate(context);
        if (validation.status() == WishActionResult.Status.RETRY) {
            step.transition(WishStepExecutionState.WAITING_TARGET, now);
            step.schedule(now + 20);
            WishExecutionManager.scheduler().delay(new StepKey(record.executionId(), stepIndex), now + 20);
            WishExecutionManager.changed(server, record);
            return;
        }
        boolean firstAttempt = step.startedGameTime() < 0;
        step.transition(WishStepExecutionState.RUNNING, now);
        record.transition(WishExecutionState.RUNNING, now);
        WishExecutionManager.changed(server, record);
        server.overworld().getDataStorage().save();
        if (firstAttempt) {
            WishingWillow.LOGGER.info("Action started session={} id={} parameters={}",
                    record.wishSessionId(), leaf.actionId(), leaf.parameters());
        }
        WishActionResult result = definition.executor().execute(context);
        WishPipelineProbe.actionExecution();
        step.result(result);
        if (result.successful()) {
            step.transition(WishStepExecutionState.SUCCEEDED, now);
        } else if (result.status() == WishActionResult.Status.RETRY) {
            boolean batchContinuation = result.shouldRetryNextTick();
            if (!batchContinuation && step.retryCount() >= 1) {
                step.retry("LOOP_DETECTED");
                step.result(WishActionResult.failed("LOOP_DETECTED"));
                step.transition(WishStepExecutionState.FAILED, now);
            } else {
                step.retry(result.code());
                step.transition(batchContinuation ? WishStepExecutionState.WAITING_DELAY
                        : WishStepExecutionState.WAITING_TARGET, now);
                long next = now + (batchContinuation ? 1 : 20);
                step.schedule(next);
                WishExecutionManager.scheduler().delay(new StepKey(record.executionId(), stepIndex), next);
            }
        } else if (result.status() == WishActionResult.Status.STALE) {
            step.transition(WishStepExecutionState.STALE, now);
        } else {
            step.transition(WishStepExecutionState.FAILED, now);
        }
        if (result.status() != WishActionResult.Status.RETRY
                || step.state() == WishStepExecutionState.FAILED) {
            int requested = requested(leaf, result);
            var evidence = step.state() == WishStepExecutionState.FAILED
                    && "LOOP_DETECTED".equals(step.lastError())
                    ? WishActionResult.failed("LOOP_DETECTED").toActionResult(requested)
                    : result.toActionResult(requested);
            WishingWillow.LOGGER.info(
                    "Action completed session={} id={} status={} requested={} completed={} failed={} message={}",
                    record.wishSessionId(), leaf.actionId(), evidence.status(), evidence.requested(),
                    evidence.completed(), evidence.failed(), evidence.message());
        }
        WishExecutionAudit.transition(record, stepIndex, leaf.actionId(), step.state().name(),
                step.lastError().isBlank() ? result.code() : step.lastError(), step.affected());
        finish(server, record, now);
    }

    /**
     * Program completion logic: core failure cancels everything; otherwise the next pending group
     * is scheduled; when all leaves are terminal the program result is reduced from core and
     * presentation states.
     */
    public static void finish(MinecraftServer server, WishExecutionRecord record, long now) {
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null || wish.program() == null) {
            record.fail("PROGRAM_MISSING", now);
            WishExecutionManager.changed(server, record);
            return;
        }
        ValidatedWishProgram validated;
        try {
            validated = validated(server, wish);
        } catch (IllegalArgumentException error) {
            markAllStale(record, "PROGRAM_REVALIDATION_FAILED " + error.getMessage());
            WishExecutionManager.changed(server, record);
            return;
        }
        List<ProgramAction> leaves = validated.allLeaves();
        boolean coreFailed = false;
        int coreCount = Math.min(record.coreActionCount(), leaves.size());
        for (int index = 0; index < coreCount; index++) {
            WishStepExecution step = record.step(index);
            if (step != null && (step.state() == WishStepExecutionState.FAILED
                    || step.state() == WishStepExecutionState.STALE
                    || step.state() == WishStepExecutionState.CANCELLED
                    || "PARTIAL_SUCCESS".equals(step.lastResult()))) {
                coreFailed = true;
                break;
            }
        }
        if (coreFailed) {
            for (WishStepExecution step : record.steps()) {
                if (!step.state().terminal()) {
                    WishExecutionManager.scheduler().remove(
                            new StepKey(record.executionId(), step.stepIndex()));
                    step.transition(WishStepExecutionState.CANCELLED, now);
                }
            }
            record.fail("CORE_ACTION_FAILED", now);
            WishExecutionManager.changed(server, record);
            return;
        }
        boolean active = record.steps().stream().anyMatch(step ->
                step.state() != WishStepExecutionState.PENDING && !step.state().terminal());
        if (!active) {
            WishStepExecution pending = record.steps().stream()
                    .filter(step -> step.state() == WishStepExecutionState.PENDING)
                    .findFirst().orElse(null);
            if (pending != null) {
                int group = leaves.get(pending.stepIndex()).group();
                for (int index = 0; index < leaves.size(); index++) {
                    if (leaves.get(index).group() != group) continue;
                    WishStepExecution step = record.step(index);
                    if (step == null || step.state() != WishStepExecutionState.PENDING) continue;
                    StepKey key = new StepKey(record.executionId(), index);
                    ProgramAction leaf = leaves.get(index);
                    int delayTicks = stepDelayTicks(leaf);
                    if (leaf.delayTicks() > 0) {
                        long at = now + leaf.delayTicks();
                        step.schedule(at);
                        step.transition(WishStepExecutionState.WAITING_DELAY, now);
                        WishExecutionManager.scheduler().delay(key, at);
                    } else {
                        step.transition(WishStepExecutionState.READY, now);
                        step.schedule(now + delayTicks);
                        WishExecutionManager.scheduler().delay(key, now + delayTicks);
                    }
                }
                record.transition(WishExecutionState.SCHEDULED, now);
                WishExecutionManager.changed(server, record);
                return;
            }
        }
        if (record.steps().stream().allMatch(step -> step.state().terminal())) {
            List<WishStepExecutionState> core = new ArrayList<>();
            List<WishStepExecutionState> presentation = new ArrayList<>();
            for (int index = 0; index < record.steps().size(); index++) {
                (index < coreCount ? core : presentation).add(record.step(index).state());
            }
            record.transition(WishProgramResultPolicy.reduce(core, presentation), now);
            if (record.state() == WishExecutionState.COMPLETED && !presentation.isEmpty()
                    && presentation.stream().anyMatch(state -> state == WishStepExecutionState.FAILED
                    || state == WishStepExecutionState.STALE)) {
                WishingWillow.LOGGER.info(
                        "Wish program completed session={} status=COMPLETED presentation=PRESENTATION_PARTIAL",
                        record.wishSessionId());
            } else {
                WishingWillow.LOGGER.info("Wish program completed session={} status={}",
                        record.wishSessionId(), record.state());
            }
            // Send completion notification to the client so processing hints stop.
            notifyCompletion(server, record);
            WishExecutionManager.changed(server, record);
            return;
        }
        record.transition(WishExecutionState.SCHEDULED, now);
        WishExecutionManager.changed(server, record);
    }

    private static void notifyCompletion(MinecraftServer server, WishExecutionRecord record) {
        ServerPlayer player = server.getPlayerList().getPlayer(record.ownerId());
        if (player == null) return;
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null) return;
        ModNetworking.sendToPlayer(player,
                new WishStatePacket(wish.sessionId(), WishState.FINISHED,
                        record.state() == WishExecutionState.COMPLETED
                                ? WishRejectionReason.NONE : WishRejectionReason.INTERRUPTED));
        WishingWillow.LOGGER.info("Wish completion notified session={} state={}",
                record.wishSessionId(), record.state());
    }

    /** Server-start recovery for stored program executions. */
    public static void rebuild(MinecraftServer server, WishExecutionRecord record) {
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null || wish.program() == null) {
            record.transition(WishExecutionState.STALE, server.overworld().getGameTime());
            WishExecutionSavedData.get(server).changed();
            return;
        }
        ValidatedWishProgram validated;
        try {
            validated = validated(server, wish);
        } catch (IllegalArgumentException error) {
            markAllStale(record, "PROGRAM_REVALIDATION_FAILED " + error.getMessage());
            WishSavedData.get(server).update(wish.withExecution(record.executionId(),
                    WishExecutionState.STALE, WishExecutionAcceptError.STALE_RESOURCE,
                    "startup program revalidation: " + error.getMessage()));
            WishExecutionSavedData.get(server).changed();
            WishingWillow.LOGGER.warn(
                    "WishingWillow program recovery rejected session={} execution={} reason={}",
                    record.wishSessionId(), record.executionId(), error.getMessage());
            return;
        }
        List<ProgramAction> leaves = validated.allLeaves();
        boolean anyActive = record.steps().stream().anyMatch(step ->
                step.state() != WishStepExecutionState.PENDING && !step.state().terminal());
        if (!anyActive) {
            schedule(server, record, validated, server.overworld().getGameTime());
            return;
        }
        for (WishStepExecution step : record.steps()) {
            if (step.state().terminal() || step.state() == WishStepExecutionState.PENDING) continue;
            StepKey key = new StepKey(record.executionId(), step.stepIndex());
            WishExecutionManager.scheduler().delay(key,
                    Math.max(server.overworld().getGameTime(), step.executeAtGameTime()));
        }
    }

    /** Program-level watchdog: fails the whole program after the bounded deadline. */
    public static void watchdog(MinecraftServer server, WishExecutionRecord record, long now) {
        if (record.state().terminal()) return;
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null || wish.program() == null) return;
        long timeout = wish.program().usesSkill() ? SKILL_TIMEOUT_TICKS : PROGRAM_TIMEOUT_TICKS;
        if (now - record.createdGameTime() < timeout) return;
        for (WishStepExecution step : record.steps()) {
            if (!step.state().terminal()) {
                WishExecutionManager.scheduler().remove(
                        new StepKey(record.executionId(), step.stepIndex()));
                step.result(WishActionResult.timeout("PROGRAM_TIMEOUT", step.affected()));
                step.transition(WishStepExecutionState.FAILED, now);
            }
        }
        record.fail(wish.program().usesSkill() ? "SKILL_TIMEOUT" : "PROGRAM_TIMEOUT", now);
        WishExecutionManager.changed(server, record);
    }

    public static boolean isBoundedWorldStep(MinecraftServer server, WishExecutionRecord record,
                                             int stepIndex) {
        WishRecord wish = WishSavedData.get(server).getBySession(record.wishSessionId());
        if (wish == null || wish.program() == null) return false;
        try {
            List<ProgramAction> leaves = validated(server, wish).allLeaves();
            if (stepIndex < 0 || stepIndex >= leaves.size()) return false;
            return BOUNDED_WORLD_ACTIONS.contains(leaves.get(stepIndex).actionId());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static void staleStep(WishExecutionRecord record, int stepIndex, String reason) {
        WishStepExecution step = record.step(stepIndex);
        if (step != null && !step.state().terminal()) {
            step.transition(WishStepExecutionState.STALE, record.updatedGameTime());
            step.result(WishActionResult.stale(reason));
        }
    }

    private static void markAllStale(WishExecutionRecord record, String reason) {
        long now = record.updatedGameTime();
        for (WishStepExecution step : record.steps()) {
            if (!step.state().terminal()) {
                WishExecutionManager.scheduler().remove(
                        new StepKey(record.executionId(), step.stepIndex()));
                step.transition(WishStepExecutionState.STALE, now);
                step.result(WishActionResult.stale(reason));
            }
        }
        record.transition(WishExecutionState.STALE, now);
    }

    private static int requested(ProgramAction leaf, WishActionResult result) {
        JsonObject parameters = leaf.parameters();
        return parameters.has("count") ? Math.max(0, parameters.get("count").getAsInt())
                : Math.max(1, result.affected());
    }
}
