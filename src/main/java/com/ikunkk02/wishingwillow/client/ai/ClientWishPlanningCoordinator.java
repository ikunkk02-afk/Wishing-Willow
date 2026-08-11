package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.agent.ai.WishingWillowChatModelAdapter;
import com.ikunkk02.wishingwillow.agent.core.*;
import com.ikunkk02.wishingwillow.agent.tool.WishAgentToolRuntime;
import com.ikunkk02.wishingwillow.client.agent.ForgeMinecraftToolPlatform;
import com.ikunkk02.wishingwillow.contract.WishContractReviewer;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishProgramPacket;
import com.ikunkk02.wishingwillow.network.packet.CancelWishPlanningPacket;
import com.ikunkk02.wishingwillow.network.packet.WishAgentDebugPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.planning.direct.LegacyPlanToProgramAdapter;
import com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramError;
import com.ikunkk02.wishingwillow.program.WishProgramJson;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
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

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-side coordinator for the NEW WishProgram path.
 *
 * <p>Direct programs are only schema-validated locally and submitted with
 * {@link SubmitWishProgramPacket}; the server performs all registry/policy/budget validation and
 * executes the program natively. The legacy plan compiler ({@code WishProgramCompiler} lowering,
 * {@code WishPlanDraft}, {@code SubmitWishPlanPacket}) is never used here.</p>
 *
 * <p>Only {@code program.requiresAgent()} routes enter the Complex Agent. The agent's researched
 * result is converted OLD-to-NEW into a {@link WishProgram} and submitted through the same
 * packet, so every wish converges on the one native server executor.</p>
 */
@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishPlanningCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CapabilityMatcher MATCHER = new CapabilityMatcher();
    private static final WishActionRouter ROUTER = new WishActionRouter();
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
        WishRouteDecision route = ROUTER.select(packet.program());
        LOGGER.info("Wish route selected session={} route={} reason={}",
                packet.sessionId(), route.route(), route.reason());
        if (!config.isConfigured() || config.providerType() != packet.providerType()
                || !config.model().equals(packet.model())) {
            send(packet, token, failedOutcome(packet, WishProgramError.INVALID_PROGRAM), planningStarted);
            return;
        }

        ModResearchManager research = ModResearchManager.getInstance();
        KnowledgeBaseSnapshot knowledge = research.knowledgeBase().snapshot();
        RegistrySnapshot registry = research.registrySnapshot();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            send(packet, token, failedOutcome(packet, WishProgramError.INVALID_PROGRAM), planningStarted);
            return;
        }
        CapabilityCatalog emptyCatalog = CapabilityCatalog.create(List.of(), List.of(),
                knowledge.state().name(), "", registry.digest());
        ForgeMinecraftToolPlatform frozenPlatform = ForgeMinecraftToolPlatform.capture(minecraft.player,
                packet.context(), registry, knowledge, emptyCatalog);
        AiService service = AiService.getInstance();
        AiProvider provider = service.provider(config);
        clientSend(new WishAgentDebugPacket(routeSnapshot(packet, route)), token);

        CompletableFuture<ProgramOutcome> planning;
        if (route.route() == WishExecutionRoute.DIRECT_ACTION) {
            try {
                WishProgramJson.validate(packet.program(),
                        com.ikunkk02.wishingwillow.execution.action.WishActionRegistry.defaults());
                WishSkillRegistry.defaults().validateSelection(packet.program());
                if (packet.program().requiresAgent()) throw new IllegalArgumentException("UNKNOWN_CAPABILITY");
                LOGGER.info("Wish program accepted session={} program={} coreActions={} presentationActions={}",
                        packet.sessionId(), packet.program().goal(),
                        packet.program().coreActions().stream().map(a -> a.action()).toList(),
                        packet.program().presentationActions().stream().map(a -> a.action()).toList());
                if (packet.program().usesSkill()) LOGGER.info("Skill selected session={} id={}",
                        packet.sessionId(), packet.program().skill());
                planning = CompletableFuture.completedFuture(programOutcome(packet, route));
            } catch (RuntimeException error) {
                LOGGER.warn("Wish program validation failed session={} detail={}",
                        packet.sessionId(), error.getMessage());
                planning = CompletableFuture.completedFuture(
                        failedOutcome(packet, WishProgramError.INVALID_PROGRAM));
            }
        } else {
            planning = complexPlanning(packet, token, knowledge, registry, frozenPlatform, provider,
                    config, service, route.reason());
        }
        planning = planning.exceptionally(error -> token.cancelled()
                ? failedOutcome(packet, WishProgramError.UNKNOWN)
                : failedOutcome(packet, classify(error)));

        CompletableFuture<Void> terminal = planning.thenAccept(outcome -> send(packet, token, outcome, planningStarted));
        GENERATION.track(token, terminal);
    }

    private static CompletableFuture<ProgramOutcome> complexPlanning(
            WishPlanningRequestPacket packet, WishPlanningGeneration.Token token,
            KnowledgeBaseSnapshot knowledge, RegistrySnapshot registry,
            ForgeMinecraftToolPlatform frozenPlatform, AiProvider provider, AiConfig config,
            AiService service, String routeReason
    ) {
        ToolCallingSupport support = service.toolCallingSupport(config);
        LOGGER.info("Unknown capability session={} reason={}", packet.sessionId(), routeReason);
        LOGGER.info("Complex agent started session={} toolSupport={}", packet.sessionId(), support);
        return CompletableFuture.supplyAsync(() -> {
                    if (token.cancelled()) throw new CancellationException("SUPERSEDED_WISH");
                    return MATCHER.match(packet.originalWish(), packet.interpretation(), knowledge, registry,
                            packet.executionSettings());
                }, MATCH_EXECUTOR)
                .thenCompose(catalog -> {
                    if (token.cancelled()) return CompletableFuture.failedFuture(
                            new CancellationException("SUPERSEDED_WISH"));
                    ForgeMinecraftToolPlatform platform = frozenPlatform.withCatalog(catalog);
                    if (support != ToolCallingSupport.SUPPORTED) {
                        return CompletableFuture.completedFuture(
                                failedOutcome(packet, WishProgramError.UNSUPPORTED_ACTION));
                    }
                    return CompletableFuture.supplyAsync(() -> runAgent(packet, token, knowledge,
                                    registry, catalog, platform, provider), AI_EXECUTOR)
                            .thenApply(result -> toProgramOutcome(packet, result));
                });
    }

    /**
     * The Complex Agent returns a legacy draft; it is converted OLD-to-NEW into a WishProgram so
     * the result runs on the same native server executor as every other wish.
     */
    private static ProgramOutcome toProgramOutcome(WishPlanningRequestPacket packet,
                                                   WishAgentRunResult result) {
        WishAgentDebugSnapshot debug = result.debug();
        if (result.result().draft() == null || debug == null) {
            WishPlanError error = result.result().error() == WishPlanError.NONE
                    ? WishPlanError.UNKNOWN : result.result().error();
            return new ProgramOutcome(null, debug, toProgramError(error));
        }
        try {
            WishProgram program = LegacyPlanToProgramAdapter.toProgram(result.result().draft(),
                    packet.program().goal());
            WishProgramJson.validate(program,
                    com.ikunkk02.wishingwillow.execution.action.WishActionRegistry.defaults());
            WishSkillRegistry.defaults().validateSelection(program);
            LOGGER.info("Agent program accepted session={} coreActions={} presentationActions={}",
                    packet.sessionId(),
                    program.coreActions().stream().map(a -> a.action()).toList(),
                    program.presentationActions().stream().map(a -> a.action()).toList());
            return new ProgramOutcome(program, debug, null);
        } catch (IllegalArgumentException error) {
            LOGGER.warn("Agent program conversion failed session={} detail={}",
                    packet.sessionId(), error.getMessage());
            return new ProgramOutcome(null, debug, WishProgramError.INVALID_PROGRAM);
        }
    }

    private static ProgramOutcome programOutcome(WishPlanningRequestPacket packet,
                                                 WishRouteDecision route) {
        WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(packet.sessionId(),
                WishPlanningMode.DIRECT_ACTION_MODE, WishAgentDebugState.COMPLETED,
                0, 0, List.of(), "", "SUCCESS", WishVerificationState.CONTRACT_FULFILLED,
                WishFinalizationState.SUCCESS, WishAgentFallbackReason.NONE, 0L,
                WishExecutionRoute.DIRECT_ACTION, route.reason(), packet.program().goal(),
                WishAbsurdityStyle.NONE, packet.interpretation().fulfillment().absurdity(),
                java.util.stream.Stream.concat(
                        packet.program().coreActions().stream().map(value -> "CORE:" + value.action()),
                        packet.program().presentationActions().stream().map(value -> "PRESENTATION:" + value.action()))
                        .toList());
        return new ProgramOutcome(packet.program(), debug, null);
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
                    == com.ikunkk02.wishingwillow.contract.WishContractReviewVerdict.FULFILLED;
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
                             ProgramOutcome outcome, long planningStarted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!GENERATION.isCurrent(token) || minecraft.getConnection() == null) return;
            ACTIVE_REQUEST.compareAndSet(packet, null);
            long totalElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - planningStarted);
            WishAgentDebugSnapshot debug = withElapsed(outcome.debug(), totalElapsed);
            if (debug != null) ModNetworking.sendToServer(new WishAgentDebugPacket(debug));
            if (outcome.program() == null) {
                WishProgramError error = outcome.error() == null
                        ? WishProgramError.INVALID_PROGRAM : outcome.error();
                LOGGER.info("Wish program failed session={} attempt={} error={} elapsedMs={}",
                        packet.sessionId(), packet.attemptId(), error, totalElapsed);
                ModNetworking.sendToServer(SubmitWishProgramPacket.failure(packet.sessionId(),
                        packet.attemptId(), packet.program().schemaVersion(), error));
                return;
            }
            ModNetworking.sendToServer(SubmitWishProgramPacket.success(packet.sessionId(),
                    packet.attemptId(), packet.program().schemaVersion(),
                    WishProgramJson.toJson(outcome.program())));
            LOGGER.info("SubmitWishProgramPacket sent session={} attempt={} coreActions={}",
                    packet.sessionId(), packet.attemptId(),
                    outcome.program().coreActions().stream().map(a -> a.action()).toList());
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

    private static ProgramOutcome failedOutcome(WishPlanningRequestPacket packet, WishProgramError error) {
        WishAgentDebugSnapshot debug = new WishAgentDebugSnapshot(packet.sessionId(),
                WishPlanningMode.COMPATIBILITY_JSON_MODE,
                WishAgentDebugState.FAILED, 0, 0, List.of(), "", "", WishVerificationState.NOT_VERIFIED,
                WishFinalizationState.TECHNICAL_FAILURE, WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE, 0L);
        return new ProgramOutcome(null, debug, error);
    }

    private static WishProgramError classify(Throwable error) {
        Throwable cause = root(error);
        return cause instanceof TimeoutException
                || cause instanceof AiRequestException request && request.category() == AiErrorCategory.TIMEOUT
                ? WishProgramError.TIMEOUT : WishProgramError.UNKNOWN;
    }

    private static WishProgramError toProgramError(WishPlanError error) {
        return switch (error) {
            case UNSUPPORTED_ACTION -> WishProgramError.UNSUPPORTED_ACTION;
            case INVALID_PARAMETER, INVALID_CANDIDATE -> WishProgramError.INVALID_PARAMETER;
            case INVALID_REGISTRY -> WishProgramError.INVALID_REGISTRY;
            case BUDGET_EXCEEDED -> WishProgramError.BUDGET_EXCEEDED;
            case AI_TIMEOUT -> WishProgramError.TIMEOUT;
            default -> WishProgramError.INVALID_PROGRAM;
        };
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

    private record ProgramOutcome(@Nullable WishProgram program,
                                  @Nullable WishAgentDebugSnapshot debug,
                                  @Nullable WishProgramError error) { }
}
