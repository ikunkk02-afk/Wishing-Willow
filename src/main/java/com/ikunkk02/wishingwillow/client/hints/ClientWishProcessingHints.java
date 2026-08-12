package com.ikunkk02.wishingwillow.client.hints;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Manages the action-bar processing hints shown while a wish is being
 * fulfilled. Hints cycle every ~3 s (configurable), change per pipeline
 * phase, and warn when the player strays too far from the willow position.
 *
 * <p>All player-facing text comes from translatable components so zh_cn /
 * en_us can supply the final strings.</p>
 */
@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishProcessingHints {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static UUID activeSessionId;
    private static WishProcessingPhase phase = WishProcessingPhase.INTERPRETING;
    @Nullable
    private static BlockPos willowPos;
    private static long startedAtMs;
    private static long lastHintTick;
    private static int hintIndex;
    private static boolean active;

    private ClientWishProcessingHints() {}

    /** Activate the hint loop. Called when the wish is snapped. */
    public static void start(UUID sessionId, BlockPos pos) {
        activeSessionId = sessionId;
        willowPos = pos;
        phase = WishProcessingPhase.INTERPRETING;
        startedAtMs = System.currentTimeMillis();
        lastHintTick = 0;
        hintIndex = 0;
        active = true;
        LOGGER.info("Wish processing hints started session={} willowPos={}", sessionId, pos);
        showNow();
    }

    /** Transition to a new phase. */
    public static void transition(WishProcessingPhase newPhase) {
        transition(activeSessionId, newPhase);
    }

    /** Transition only when the update belongs to the active wish pipeline. */
    public static void transition(@Nullable UUID sessionId, WishProcessingPhase newPhase) {
        if (!active || sessionId == null || !sessionId.equals(activeSessionId)) return;
        WishProcessingPhase previous = phase;
        phase = newPhase;
        hintIndex = 0;
        LOGGER.info("Wish pipeline UI state session={} phase {} -> {}",
                activeSessionId, previous, newPhase);
        showNow();
    }

    /** Update the phase based on pipeline progress (for external callers). */
    public static void setPhase(WishProcessingPhase newPhase) {
        if (!active) return;
        transition(newPhase);
    }

    public static void setPhase(UUID sessionId, WishProcessingPhase newPhase) {
        transition(sessionId, newPhase);
    }

    /** Stop the hint loop entirely. */
    public static void stop() {
        if (!active) return;
        LOGGER.info("Wish processing hints stopped session={} phase={}", activeSessionId, phase);
        active = false;
        activeSessionId = null;
        willowPos = null;
        phase = WishProcessingPhase.INTERPRETING;
    }

    /** Stop only the named pipeline; stale UI events cannot stop a newer wish. */
    public static void stop(UUID sessionId) {
        if (!isActive(sessionId)) return;
        stop();
    }

    /** Check if hints are active for the given session. */
    public static boolean isActive(UUID sessionId) {
        return active && activeSessionId != null && activeSessionId.equals(sessionId);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        if (!WishingWillowClientConfig.SHOW_WISH_PROCESSING_HINT.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        long now = minecraft.player.level().getGameTime();
        long interval = WishingWillowClientConfig.WISH_PROCESSING_HINT_INTERVAL.get();
        if (now - lastHintTick < interval) return;
        lastHintTick = now;

        // Distance warning takes priority
        if (willowPos != null && isTooFar(minecraft.player)) {
            showDistanceWarning(minecraft.player);
            return;
        }

        showPhaseHint(minecraft.player);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    private static void showNow() {
        if (!WishingWillowClientConfig.SHOW_WISH_PROCESSING_HINT.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        LocalPlayer player = minecraft.player;
        if (willowPos != null && isTooFar(player)) {
            showDistanceWarning(player);
        } else {
            showPhaseHint(player);
        }
        lastHintTick = player.level().getGameTime();
    }

    private static boolean isTooFar(LocalPlayer player) {
        if (willowPos == null || !WishingWillowClientConfig.WISH_STAY_NEARBY_WARNING.get()) return false;
        double distance = Math.sqrt(player.distanceToSqr(willowPos.getX() + 0.5,
                willowPos.getY(), willowPos.getZ() + 0.5));
        int maxDistance = WishingWillowClientConfig.WISH_STAY_NEARBY_DISTANCE.get();
        return distance > maxDistance * 0.7;
    }

    private static void showDistanceWarning(LocalPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.wishing_willow.wish_distance_warning"), true);
    }

    private static void showPhaseHint(LocalPlayer player) {
        Component message = currentMessage();
        player.displayClientMessage(message, true);
    }

    private static Component currentMessage() {
        List<String> keys = phaseMessageKeys();
        if (keys.isEmpty()) return Component.empty();
        String key = keys.get(hintIndex % keys.size());
        hintIndex = (hintIndex + 1) % keys.size();

        // For long waits, use extended messages
        long elapsed = System.currentTimeMillis() - startedAtMs;
        if (elapsed > 10_000 && phase != WishProcessingPhase.FAILED) {
            return Component.translatable("message.wishing_willow.wish_long_wait");
        }
        if (elapsed > 5_000 && phase != WishProcessingPhase.FAILED
                && hintIndex == 0) {
            return Component.translatable("message.wishing_willow.wish_please_wait");
        }
        return Component.translatable(key);
    }

    private static List<String> phaseMessageKeys() {
        return switch (phase) {
            case INTERPRETING -> List.of(
                    "message.wishing_willow.wish_interpreting_1",
                    "message.wishing_willow.wish_interpreting_2",
                    "message.wishing_willow.wish_interpreting_3"
            );
            case PLANNING -> List.of(
                    "message.wishing_willow.wish_planning_1",
                    "message.wishing_willow.wish_planning_2",
                    "message.wishing_willow.wish_planning_3"
            );
            case RESEARCHING -> List.of(
                    "message.wishing_willow.wish_researching_1",
                    "message.wishing_willow.wish_researching_2"
            );
            case PREPARING -> List.of(
                    "message.wishing_willow.wish_preparing"
            );
            case EXECUTING -> List.of(
                    "message.wishing_willow.wish_executing"
            );
            case FAILED -> List.of(
                    "message.wishing_willow.wish_failed"
            );
        };
    }
}
