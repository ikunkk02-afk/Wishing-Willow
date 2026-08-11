package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.ToolCallingSupport;
import com.ikunkk02.wishingwillow.agent.ai.WishingWillowChatModelAdapter;
import com.ikunkk02.wishingwillow.agent.core.WishAgentLoop;
import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import com.ikunkk02.wishingwillow.agent.core.WishAgentDebugSnapshot;
import com.ikunkk02.wishingwillow.agent.core.WishFinalizationState;
import com.ikunkk02.wishingwillow.agent.core.WishPlanningMode;
import com.ikunkk02.wishingwillow.agent.core.WishVerificationState;
import com.ikunkk02.wishingwillow.agent.tool.WishAgentToolRuntime;
import com.ikunkk02.wishingwillow.client.agent.ForgeMinecraftToolPlatform;
import com.ikunkk02.wishingwillow.contract.WishContractReviewer;
import com.ikunkk02.wishingwillow.contract.WishContractReviewVerdict;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningProgressPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.network.packet.WishAgentDebugPacket;
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
import java.util.List;

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
    private static volatile long generation;

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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            send(packet, requestGeneration, WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED), null);
            return;
        }
        CapabilityCatalog emptyCatalog = CapabilityCatalog.create(List.of(), List.of(),
                knowledge.state().name(), "", registry.digest());
        ForgeMinecraftToolPlatform frozenPlatform = ForgeMinecraftToolPlatform.capture(minecraft.player,
                packet.context(), registry, knowledge, emptyCatalog);
        CompletableFuture.supplyAsync(() -> MATCHER.match(packet.originalWish(), packet.interpretation(),
                        knowledge, registry, packet.executionSettings()), MATCH_EXECUTOR)
                .thenCompose(catalog -> {
                    clientSend(new WishPlanningProgressPacket(packet.sessionId(), packet.attemptId(),
                            WishPlanState.PLANNING), requestGeneration);
                    ForgeMinecraftToolPlatform platform = frozenPlatform.withCatalog(catalog);
                    if (AiService.getInstance().toolCallingSupport(config) == ToolCallingSupport.UNSUPPORTED) {
                        return compatibility(config, packet, catalog, registry, null);
                    }
                    return CompletableFuture.supplyAsync(() -> {
                        var provider = AiService.getInstance().provider(config);
                        WishAgentSession session = new WishAgentSession(packet.sessionId(), packet.originalWish(),
                                packet.interpretation(), packet.context(), registry, knowledge,
                                packet.executionSettings(), catalog, platform,
                                () -> requestGeneration != generation);
                        WishAgentToolRuntime tools = new WishAgentToolRuntime((interpretation, draft) ->
                                WishContractReviewer.review(provider, interpretation, draft).join().verdict()
                                        == WishContractReviewVerdict.FULFILLED);
                        WishAgentLoop loop = new WishAgentLoop(new WishingWillowChatModelAdapter(provider, 2048), tools);
                        return loop.run(session);
                    }, MATCH_EXECUTOR).thenCompose(agent -> {
                        if (agent.result().draft() != null) {
                            return CompletableFuture.completedFuture(new Completed(agent.result(), agent.catalog(), agent.debug()));
                        }
                        if (requestGeneration != generation) {
                            return CompletableFuture.completedFuture(new Completed(agent.result(), null, agent.debug()));
                        }
                        return compatibility(config, packet, catalog, registry, agent.debug());
                    });
                })
                .exceptionally(throwable -> new Completed(WishPlanResult.failed(WishPlanError.UNKNOWN), null, null))
                .thenAccept(completed -> send(packet, requestGeneration, completed.result, completed.catalog, completed.debug));
    }

    private static CompletableFuture<Completed> compatibility(AiConfig config, WishPlanningRequestPacket packet,
                                                               CapabilityCatalog catalog, RegistrySnapshot registry,
                                                               WishAgentDebugSnapshot prior) {
        return PLANNER.plan(config, packet.originalWish(), packet.interpretation(), packet.context(), catalog,
                        new RegistrySnapshotEnvironment(registry), packet.executionSettings())
                .thenApply(result -> {
                    WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(packet.sessionId(),
                            WishPlanningMode.COMPATIBILITY_JSON_MODE,
                            prior == null ? 0 : prior.iterations(), prior == null ? 0 : prior.toolCalls(),
                            prior == null ? List.of() : prior.toolsUsed(),
                            prior == null ? WishVerificationState.NOT_VERIFIED : prior.verificationState(),
                            result.draft() == null ? WishFinalizationState.TECHNICAL_FAILURE : WishFinalizationState.SUCCESS);
                    return new Completed(result, catalog, debug);
                });
    }

    private static void send(WishPlanningRequestPacket packet, long requestGeneration,
                             WishPlanResult result, CapabilityCatalog catalog) {
        send(packet, requestGeneration, result, catalog, null);
    }

    private static void send(WishPlanningRequestPacket packet, long requestGeneration,
                             WishPlanResult result, CapabilityCatalog catalog, WishAgentDebugSnapshot debug) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (requestGeneration != generation || minecraft.getConnection() == null) return;
            LOGGER.info("Wish planning completed session={} attempt={} state={} error={} steps={}",
                    packet.sessionId(), packet.attemptId(), result.state(), result.error(),
                    result.draft() == null ? 0 : result.draft().steps().size());
            if (debug != null) ModNetworking.sendToServer(new WishAgentDebugPacket(debug));
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

    private record Completed(WishPlanResult result, CapabilityCatalog catalog, WishAgentDebugSnapshot debug) { }
}
