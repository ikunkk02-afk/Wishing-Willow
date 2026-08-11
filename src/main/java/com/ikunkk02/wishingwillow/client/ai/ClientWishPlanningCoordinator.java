package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.agent.ai.WishingWillowChatModelAdapter;
import com.ikunkk02.wishingwillow.agent.core.*;
import com.ikunkk02.wishingwillow.agent.tool.WishAgentToolRuntime;
import com.ikunkk02.wishingwillow.client.agent.ForgeMinecraftToolPlatform;
import com.ikunkk02.wishingwillow.contract.WishContractReviewVerdict;
import com.ikunkk02.wishingwillow.contract.WishContractReviewer;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.network.packet.CancelWishPlanningPacket;
import com.ikunkk02.wishingwillow.network.packet.WishAgentDebugPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningProgressPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.planning.direct.DirectActionPlanningResult;
import com.ikunkk02.wishingwillow.planning.direct.DirectWishActionPlanner;
import com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishPlanningCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CapabilityMatcher MATCHER = new CapabilityMatcher();
    private static final WishPlanner PLANNER = new WishPlanner();
    private static final WishPlanningOrchestrator ORCHESTRATOR = new WishPlanningOrchestrator();
    private static final WishActionRouter ROUTER = new WishActionRouter();
    private static final DirectWishActionPlanner DIRECT_PLANNER = new DirectWishActionPlanner();
    private static final WishPlanningGeneration GENERATION = new WishPlanningGeneration();
    private static final AtomicReference<WishPlanningRequestPacket> ACTIVE_REQUEST = new AtomicReference<>();
    private static final ExecutorService MATCH_EXECUTOR = Executors.newFixedThreadPool(2,
            namedThreads("wishing-willow-matcher"));
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(4,
            namedThreads("wishing-willow-agent"));

    private ClientWishPlanningCoordinator() { }

    public static void start(WishPlanningRequestPacket packet) {
        long planningStarted = System.nanoTime();
        WishPlanningRequestPacket previous = ACTIVE_REQUEST.getAndSet(packet);
        WishPlanningGeneration.Token token = GENERATION.begin(packet.sessionId());
        cancelPrevious(previous, packet);
        AiConfig config = AiConfigManager.getInstance().get();
        WishRouteDecision route = ROUTER.select(packet.originalWish(), packet.interpretation());
        LOGGER.info("Wish route selected session={} route={} reason={}",
                packet.sessionId(), route.route(), route.reason());
        LOGGER.info("Wish planning started session={} attempt={} mode={} generation={}",
                packet.sessionId(), packet.attemptId(), route.route(), token.generation());
        if (!config.isConfigured() || config.providerType() != packet.providerType()
                || !config.model().equals(packet.model())) {
            send(packet, token, failedOutcome(packet, WishPlanError.AI_REQUEST_FAILED), planningStarted);
            return;
        }

        ModResearchManager research = ModResearchManager.getInstance();
        KnowledgeBaseSnapshot knowledge = research.knowledgeBase().snapshot();
        RegistrySnapshot registry = research.registrySnapshot();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            send(packet, token, failedOutcome(packet, WishPlanError.AI_REQUEST_FAILED), planningStarted);
            return;
        }
        CapabilityCatalog emptyCatalog = CapabilityCatalog.create(List.of(), List.of(),
                knowledge.state().name(), "", registry.digest());
        ForgeMinecraftToolPlatform frozenPlatform = ForgeMinecraftToolPlatform.capture(minecraft.player,
                packet.context(), registry, knowledge, emptyCatalog);
        AiService service = AiService.getInstance();
        AiProvider provider = service.provider(config);
        clientSend(new WishAgentDebugPacket(routeSnapshot(packet, route)), token);
        clientSend(new WishPlanningProgressPacket(packet.sessionId(), packet.attemptId(),
                WishPlanState.PLANNING), token);

        CompletableFuture<WishPlanningOutcome> planning;
        if (route.route() == WishExecutionRoute.DIRECT_ACTION) {
            planning = DIRECT_PLANNER.plan(packet.sessionId(), provider, packet.originalWish(),
                            packet.interpretation(), emptyCatalog, registry, packet.executionSettings())
                    .thenCompose(result -> {
                        if (token.cancelled()) return CompletableFuture.failedFuture(
                                new CancellationException("SUPERSEDED_WISH"));
                        if (result.state() == DirectActionPlanningResult.State.UNSUPPORTED_ACTION) {
                            LOGGER.info("Wish route escalated session={} from=DIRECT_ACTION to=COMPLEX_AGENT reason={}",
                                    packet.sessionId(), result.detail());
                            return complexPlanning(packet, token, knowledge, registry, frozenPlatform,
                                    provider, config, service, "direct_unsupported=" + result.detail());
                        }
                        return CompletableFuture.completedFuture(directOutcome(packet, route, result));
                    });
        } else {
            planning = complexPlanning(packet, token, knowledge, registry, frozenPlatform, provider,
                    config, service, route.reason());
        }
        planning = planning.exceptionally(error -> token.cancelled()
                ? failedOutcome(packet, WishPlanError.AI_REQUEST_FAILED)
                : failedOutcome(packet, classify(error)));

        CompletableFuture<Void> terminal = planning.thenAccept(outcome -> send(packet, token, outcome, planningStarted));
        GENERATION.track(token, terminal);
    }

    private static CompletableFuture<WishPlanningOutcome> complexPlanning(
            WishPlanningRequestPacket packet, WishPlanningGeneration.Token token,
            KnowledgeBaseSnapshot knowledge, RegistrySnapshot registry,
            ForgeMinecraftToolPlatform frozenPlatform, AiProvider provider, AiConfig config,
            AiService service, String routeReason
    ) {
        ToolCallingSupport support = service.toolCallingSupport(config);
        LOGGER.info("Complex Agent route started session={} reason={} toolSupport={}",
                packet.sessionId(), routeReason, support);
        return CompletableFuture.supplyAsync(() -> {
                    if (token.cancelled()) throw new CancellationException("SUPERSEDED_WISH");
                    return MATCHER.match(packet.originalWish(), packet.interpretation(), knowledge, registry,
                            packet.executionSettings());
                }, MATCH_EXECUTOR)
                .thenCompose(catalog -> {
                    if (token.cancelled()) return CompletableFuture.failedFuture(
                            new CancellationException("SUPERSEDED_WISH"));
                    ForgeMinecraftToolPlatform platform = frozenPlatform.withCatalog(catalog);
                    return ORCHESTRATOR.plan(packet.sessionId(), support, catalog,
                                    () -> service.probeToolCallingSupport(config),
                                    () -> CompletableFuture.supplyAsync(() -> runAgent(packet, token, knowledge,
                                            registry, catalog, platform, provider), AI_EXECUTOR),
                                    () -> PLANNER.plan(config, packet.originalWish(), packet.interpretation(),
                                            packet.context(), catalog, new RegistrySnapshotEnvironment(registry),
                                            packet.executionSettings()), token::cancelled,
                                    snapshot -> clientSend(new WishAgentDebugPacket(snapshot), token))
                            .thenApply(outcome -> {
                                if (outcome.debug().fallbackReason()
                                        == WishAgentFallbackReason.TOOL_CALLING_UNSUPPORTED) {
                                    service.recordToolCallingSupport(config, ToolCallingSupport.UNSUPPORTED);
                                }
                                return outcome;
                            });
                });
    }

    private static WishPlanningOutcome directOutcome(WishPlanningRequestPacket packet,
                                                     WishRouteDecision route,
                                                     DirectActionPlanningResult result) {
        boolean success = result.state() == DirectActionPlanningResult.State.SUCCESS
                && result.compiled() != null;
        var compiled = result.compiled();
        WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(packet.sessionId(),
                WishPlanningMode.DIRECT_ACTION_MODE,
                success ? WishAgentDebugState.COMPLETED : WishAgentDebugState.FAILED,
                0, 0, List.of(), "", success ? "SUCCESS" : result.result().error().name(),
                success ? WishVerificationState.CONTRACT_FULFILLED : WishVerificationState.NOT_FULFILLED,
                success ? WishFinalizationState.SUCCESS : WishFinalizationState.REJECTED,
                WishAgentFallbackReason.NONE, 0L, WishExecutionRoute.DIRECT_ACTION, route.reason(),
                packet.interpretation().contract().requiredOutcome(),
                success ? compiled.absurdity().style() : WishAbsurdityStyle.NONE,
                success ? compiled.absurdity().intensity() : 0,
                success ? compiled.directActions() : List.of());
        return new WishPlanningOutcome(result.result(), result.catalog(), debug);
    }

    private static WishAgentDebugSnapshot routeSnapshot(WishPlanningRequestPacket packet,
                                                        WishRouteDecision route) {
        return new WishAgentDebugSnapshot(packet.sessionId(),
                route.route() == WishExecutionRoute.DIRECT_ACTION
                        ? WishPlanningMode.DIRECT_ACTION_MODE : WishPlanningMode.AGENT_TOOL_MODE,
                WishAgentDebugState.ROUTE_SELECTED, 0, 0, List.of(), "", "",
                WishVerificationState.NOT_VERIFIED, WishFinalizationState.NOT_ATTEMPTED,
                WishAgentFallbackReason.NONE, 0L, route.route(), route.reason(),
                packet.interpretation().contract().requiredOutcome(), WishAbsurdityStyle.NONE,
                packet.interpretation().fulfillment().absurdity(), List.of());
    }

    private static WishAgentRunResult runAgent(WishPlanningRequestPacket packet,
                                               WishPlanningGeneration.Token token,
                                               KnowledgeBaseSnapshot knowledge,
                                               RegistrySnapshot registry,
                                               CapabilityCatalog catalog,
                                               ForgeMinecraftToolPlatform platform,
                                               AiProvider provider) {
        WishAgentSession session = new WishAgentSession(packet.sessionId(), packet.originalWish(),
                packet.interpretation(), packet.context(), registry, knowledge, packet.executionSettings(),
                catalog, platform, token::cancelled,
                snapshot -> clientSend(new WishAgentDebugPacket(snapshot), token));
        WishAgentToolRuntime tools = new WishAgentToolRuntime((interpretation, draft) ->
                reviewContract(provider, session, interpretation, draft));
        WishingWillowChatModelAdapter model = new WishingWillowChatModelAdapter(provider, 2048,
                WishingWillowChatModelAdapter.DEFAULT_AGENT_REQUEST_TIMEOUT,
                () -> session.remainingDuration().toMillis(), session::cancelled);
        return new WishAgentLoop(model, tools).run(session);
    }

    private static boolean reviewContract(AiProvider provider, WishAgentSession session,
                                          WishInterpretation interpretation, WishPlanDraft draft) {
        Duration timeout = min(WishContractReviewer.REVIEW_TIMEOUT, session.remainingDuration());
        CompletableFuture<com.ikunkk02.wishingwillow.contract.WishContractReview> review =
                WishContractReviewer.review(provider, interpretation, draft, timeout);
        try {
            return review.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS).verdict()
                    == WishContractReviewVerdict.FULFILLED;
        } catch (TimeoutException exception) {
            review.cancel(true);
            session.markFallbackReason(WishAgentFallbackReason.CONTRACT_REVIEW_TIMEOUT);
            throw new CompletionException(exception);
        } catch (InterruptedException exception) {
            review.cancel(true);
            Thread.currentThread().interrupt();
            session.markFallbackReason(WishAgentFallbackReason.CANCELLED);
            throw new CompletionException(exception);
        } catch (ExecutionException exception) {
            Throwable cause = root(exception);
            session.markFallbackReason(cause instanceof TimeoutException
                    ? WishAgentFallbackReason.CONTRACT_REVIEW_TIMEOUT
                    : WishAgentFallbackReason.REVIEW_TECHNICAL_FAILURE);
            throw new CompletionException(cause);
        }
    }

    private static void send(WishPlanningRequestPacket packet, WishPlanningGeneration.Token token,
                             WishPlanningOutcome outcome, long planningStarted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!GENERATION.isCurrent(token) || minecraft.getConnection() == null) return;
            ACTIVE_REQUEST.compareAndSet(packet, null);
            WishPlanResult result = outcome.result();
            long totalElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - planningStarted);
            WishAgentDebugSnapshot debug = withElapsed(outcome.debug(), totalElapsed);
            LOGGER.info("Wish planning completed session={} attempt={} mode={} state={} error={} steps={} elapsedMs={}",
                    packet.sessionId(), packet.attemptId(), debug == null ? "UNKNOWN" : debug.mode(),
                    result.state(), result.error(), result.draft() == null ? 0 : result.draft().steps().size(),
                    totalElapsed);
            if (debug != null) ModNetworking.sendToServer(new WishAgentDebugPacket(debug));
            if (result.draft() == null || outcome.catalog() == null) {
                ModNetworking.sendToServer(SubmitWishPlanPacket.fromResult(packet.sessionId(),
                        packet.attemptId(), result, outcome.catalog()));
                return;
            }
            ModNetworking.sendToServer(new WishPlanningProgressPacket(packet.sessionId(), packet.attemptId(),
                    WishPlanState.VALIDATING));
            if (debug != null && debug.route() == WishExecutionRoute.DIRECT_ACTION) {
                LOGGER.info("Direct action submitted session={} actions={}",
                        packet.sessionId(), debug.directActions());
            }
            ModNetworking.sendToServer(SubmitWishPlanPacket.fromResult(packet.sessionId(),
                    packet.attemptId(), result, outcome.catalog()));
        });
    }

    private static WishAgentDebugSnapshot withElapsed(WishAgentDebugSnapshot debug, long elapsedMs) {
        if (debug == null) return null;
        return new WishAgentDebugSnapshot(debug.sessionId(), debug.mode(), debug.state(), debug.iterations(),
                debug.toolCalls(), debug.toolsUsed(), debug.lastTool(), debug.lastToolStatus(),
                debug.verificationState(), debug.finalizationState(), debug.fallbackReason(), elapsedMs,
                debug.route(), debug.routeReason(), debug.coreOutcome(), debug.absurdityStyle(),
                debug.absurdityIntensity(), debug.directActions());
    }

    private static void clientSend(Object packet, WishPlanningGeneration.Token token) {
        Minecraft.getInstance().execute(() -> {
            if (GENERATION.isCurrent(token) && Minecraft.getInstance().getConnection() != null) {
                ModNetworking.sendToServer(packet);
            }
        });
    }

    private static void cancelPrevious(WishPlanningRequestPacket previous, WishPlanningRequestPacket replacement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (previous == null || previous.attemptId().equals(replacement.attemptId())
                || minecraft.getConnection() == null) return;
        LOGGER.info("Wish planning cancelled session={} attempt={} reason=SUPERSEDED replacementSession={}",
                previous.sessionId(), previous.attemptId(), replacement.sessionId());
        ModNetworking.sendToServer(new WishAgentDebugPacket(new WishAgentDebugSnapshot(previous.sessionId(),
                WishPlanningMode.AGENT_TOOL_MODE, WishAgentDebugState.CANCELLED, 0, 0, List.of(), "", "",
                WishVerificationState.NOT_VERIFIED, WishFinalizationState.CANCELLED,
                WishAgentFallbackReason.CANCELLED, 0L)));
        ModNetworking.sendToServer(new CancelWishPlanningPacket(previous.sessionId(), previous.attemptId()));
    }

    private static WishPlanningOutcome failedOutcome(WishPlanningRequestPacket packet, WishPlanError error) {
        WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(packet.sessionId(), WishPlanningMode.COMPATIBILITY_JSON_MODE,
                WishAgentDebugState.FAILED, 0, 0, List.of(), "", "", WishVerificationState.NOT_VERIFIED,
                WishFinalizationState.TECHNICAL_FAILURE, WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE, 0L);
        return new WishPlanningOutcome(WishPlanResult.failed(error), null, debug);
    }

    private static WishPlanError classify(Throwable error) {
        Throwable cause = root(error);
        return cause instanceof TimeoutException
                || cause instanceof AiRequestException request && request.category() == AiErrorCategory.TIMEOUT
                ? WishPlanError.AI_TIMEOUT : WishPlanError.UNKNOWN;
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current != null && (current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_REQUEST.set(null);
        GENERATION.cancelAll();
    }
}
