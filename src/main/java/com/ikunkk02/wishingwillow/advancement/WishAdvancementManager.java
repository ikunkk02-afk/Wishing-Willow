package com.ikunkk02.wishingwillow.advancement;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class WishAdvancementManager {
    public static final ResourceLocation ROOT = id("root");
    public static final ResourceLocation FIRST_WISH = id("first_wish");
    public static final ResourceLocation WISH_COME_TRUE = id("wish_come_true");
    public static final ResourceLocation DREAMER = id("dreamer");
    public static final ResourceLocation GREEDY = id("greedy");
    public static final ResourceLocation VETERAN = id("wish_veteran");
    public static final ResourceLocation ABSURD = id("absurd_success");
    public static final ResourceLocation WORLD_HEARD = id("world_heard");
    public static final ResourceLocation PERSISTENT = id("out_of_control");
    public static final ResourceLocation BACKFIRE = id("backfire");
    public static final ResourceLocation CAREFUL = id("careful_what_you_wish_for");

    private WishAdvancementManager() { }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WishAdvancementManager::onPlayerLoggedIn);
    }

    public static void onWishSubmitted(ServerPlayer player, UUID sessionId) {
        WishAdvancementSavedData data = WishAdvancementSavedData.get(player.server);
        WishAdvancementProgress progress = data.progress(player.getUUID());
        if (!progress.recordSubmitted(sessionId)) return;
        data.changed();
        award(player, ROOT);
        award(player, FIRST_WISH);
        WishingWillow.LOGGER.info("Advancement progress player={} event=WISH_SUBMITTED total={}",
                player.getGameProfile().getName(), progress.totalWishesSubmitted());
    }

    public static void onExecutionCompleted(MinecraftServer server, WishExecutionRecord execution,
                                            WishRecord wish) {
        if (execution == null || wish == null || wish.program() == null || wish.interpretation() == null
                || execution.state() != WishExecutionState.COMPLETED) return;
        WishOutcomeSummary outcome;
        try {
            outcome = WishOutcomeClassifier.classify(execution, wish.program(), wish.interpretation(),
                    com.ikunkk02.wishingwillow.execution.WishProgramExecutor.validated(server, wish).allLeaves());
        } catch (RuntimeException error) {
            WishingWillow.LOGGER.warn("Advancement outcome classification skipped session={} reason={}",
                    execution.wishSessionId(), error.getMessage());
            return;
        }
        if (outcome.successfulActionCount() < 1) return;
        WishAdvancementSavedData data = WishAdvancementSavedData.get(server);
        WishAdvancementProgress progress = data.progress(execution.ownerId());
        if (!progress.recordSuccess(execution.wishSessionId(), outcome)) return;
        data.changed();
        ServerPlayer player = server.getPlayerList().getPlayer(execution.ownerId());
        if (player == null) return;
        sync(player, progress);
        if (outcome.absurd()) award(player, ABSURD);
        if (outcome.successfulActionCount() >= 10) award(player, WORLD_HEARD);
        if (outcome.persistent()) award(player, PERSISTENT);
        if (outcome.negative()) award(player, BACKFIRE);
        if (outcome.severity() == WishSeverity.CATASTROPHIC) award(player, CAREFUL);
        WishingWillow.LOGGER.info("Advancement progress player={} event=WISH_SUCCESS successful={}",
                player.getGameProfile().getName(), progress.successfulWishes());
    }

    public static WishAdvancementProgress progress(MinecraftServer server, UUID playerId) {
        return WishAdvancementSavedData.get(server).progress(playerId);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The root is the page entry point. Granting only this vanilla advancement on login
            // makes the independent Wishing Willow tab visible before the player's first wish;
            // all gameplay achievements remain event-gated below.
            award(player, ROOT);
            sync(player, WishAdvancementSavedData.get(player.server).progress(player.getUUID()));
        }
    }

    /** Shared server-login boundary used by runtime and GameTest verification. */
    public static void onPlayerLoginForTest(ServerPlayer player) {
        award(player, ROOT);
        sync(player, WishAdvancementSavedData.get(player.server).progress(player.getUUID()));
    }

    private static void sync(ServerPlayer player, WishAdvancementProgress progress) {
        if (progress.totalWishesSubmitted() > 0) { award(player, ROOT); award(player, FIRST_WISH); }
        if (progress.successfulWishes() > 0) award(player, WISH_COME_TRUE);
        if (progress.successfulWishes() >= 5) award(player, DREAMER);
        if (progress.successfulWishes() >= 20) award(player, GREEDY);
        if (progress.successfulWishes() >= 50) award(player, VETERAN);
        if (progress.absurdWishes() > 0) award(player, ABSURD);
        if (progress.largestSuccessfulActionCount() >= 10) award(player, WORLD_HEARD);
        if (progress.persistentWishes() > 0) award(player, PERSISTENT);
        if (progress.negativeWishes() > 0) award(player, BACKFIRE);
        if (progress.catastrophicWishes() > 0) award(player, CAREFUL);
    }

    private static void award(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        boolean changed = false;
        java.util.ArrayList<String> remaining = new java.util.ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            changed |= player.getAdvancements().award(advancement, criterion);
        }
        if (changed) {
            WishingWillow.LOGGER.info("Advancement unlocked player={} advancement={}",
                    player.getGameProfile().getName(), id.getPath());
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(WishingWillow.MOD_ID, path);
    }
}
