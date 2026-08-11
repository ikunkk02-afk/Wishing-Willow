package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the lifecycle boundary for Wish Programs: supersede, loop validation, start and cancel.
 * Low-level persisted scheduling remains in {@link WishExecutionManager} for save compatibility.
 */
public final class WishActionManager {
    private static final Map<UUID, UUID> ACTIVE_BY_PLAYER = new HashMap<>();

    private WishActionManager() { }

    public static WishExecutionAcceptResult start(ServerPlayer player, WishPlan plan) {
        if (player == null || plan == null) {
            return WishExecutionAcceptResult.rejected(WishExecutionAcceptError.INVALID_PLAN, "missing player or plan");
        }
        WishActionLoopDetector detector = new WishActionLoopDetector();
        for (WishPlanStep step : plan.steps()) {
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
