package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningProgressPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.CapabilityMatcher;
import com.ikunkk02.wishingwillow.planning.RegistrySnapshotEnvironment;
import com.ikunkk02.wishingwillow.planning.WishPlanError;
import com.ikunkk02.wishingwillow.planning.WishPlanJson;
import com.ikunkk02.wishingwillow.planning.WishPlanResult;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import com.ikunkk02.wishingwillow.planning.WishPlanner;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishPlanningCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CapabilityMatcher MATCHER = new CapabilityMatcher();
    private static final WishPlanner PLANNER = new WishPlanner();
    private static final ExecutorService MATCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wishing-willow-matcher");
        thread.setDaemon(true);
        return thread;
    });
    private static long generation;

    private ClientWishPlanningCoordinator() { }

    public static void start(WishPlanningRequestPacket packet) {
        LOGGER.info("Wish planning started session={} attempt={}", packet.sessionId(), packet.attemptId());
        long requestGeneration = generation;
        AiConfig config = AiConfigManager.getInstance().get();
        if (!config.isConfigured() || config.providerType() != packet.providerType()
                || !config.model().equals(packet.model())) {
            send(packet, requestGeneration, WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED), null);
            return;
        }
        ModResearchManager research = ModResearchManager.getInstance();
        KnowledgeBaseSnapshot knowledge = research.knowledgeBase().snapshot();
        RegistrySnapshot registry = research.registrySnapshot();
        CompletableFuture.supplyAsync(() -> MATCHER.match(packet.originalWish(), packet.interpretation(),
                        knowledge, registry, packet.executionSettings()), MATCH_EXECUTOR)
                .thenCompose(catalog -> {
                    clientSend(new WishPlanningProgressPacket(packet.sessionId(), packet.attemptId(),
                            WishPlanState.PLANNING), requestGeneration);
                    return PLANNER.plan(config, packet.originalWish(), packet.interpretation(), packet.context(),
                                    catalog, new RegistrySnapshotEnvironment(registry), packet.executionSettings())
                            .thenApply(result -> new Completed(result, catalog));
                })
                .exceptionally(throwable -> new Completed(WishPlanResult.failed(WishPlanError.UNKNOWN), null))
                .thenAccept(completed -> send(packet, requestGeneration, completed.result, completed.catalog));
    }

    private static void send(WishPlanningRequestPacket packet, long requestGeneration,
                             WishPlanResult result, CapabilityCatalog catalog) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (requestGeneration != generation || minecraft.getConnection() == null) return;
            LOGGER.info("Wish planning completed session={} attempt={} state={} error={} steps={}",
                    packet.sessionId(), packet.attemptId(), result.state(), result.error(),
                    result.draft() == null ? 0 : result.draft().steps().size());
            if (result.draft() == null || catalog == null) {
                ModNetworking.sendToServer(new SubmitWishPlanPacket(packet.sessionId(), packet.attemptId(),
                        result.error(), result.attemptsUsed(), null, null));
                return;
            }
            ModNetworking.sendToServer(new WishPlanningProgressPacket(packet.sessionId(), packet.attemptId(),
                    WishPlanState.VALIDATING));
            ModNetworking.sendToServer(new SubmitWishPlanPacket(packet.sessionId(), packet.attemptId(),
                    WishPlanError.NONE, result.attemptsUsed(), catalog, WishPlanJson.toAiJson(result.draft())));
        });
    }

    private static void clientSend(Object packet, long requestGeneration) {
        Minecraft.getInstance().execute(() -> {
            if (requestGeneration == generation && Minecraft.getInstance().getConnection() != null) {
                ModNetworking.sendToServer(packet);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { generation++; }

    private record Completed(WishPlanResult result, CapabilityCatalog catalog) { }
}
