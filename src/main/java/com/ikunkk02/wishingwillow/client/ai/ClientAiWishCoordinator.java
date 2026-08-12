package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.AiConfig;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.ai.AiErrorCategory;
import com.ikunkk02.wishingwillow.ai.AiService;
import com.ikunkk02.wishingwillow.ai.InterpretationState;
import com.ikunkk02.wishingwillow.ai.WishInterpretationResult;
import com.ikunkk02.wishingwillow.ai.WishInterpreter;
import com.ikunkk02.wishingwillow.client.hints.ClientWishProcessingHints;
import com.ikunkk02.wishingwillow.client.hints.WishProcessingPhase;
import com.ikunkk02.wishingwillow.config.WishingWillowClientConfig;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishInterpretationPacket;
import com.ikunkk02.wishingwillow.network.packet.WishPipelineStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.wish.WishLifecycleLog;
import com.ikunkk02.wishingwillow.wish.WishPipelineState;
import com.ikunkk02.wishingwillow.wish.WishSessionTerminationReason;
import com.ikunkk02.wishingwillow.wish.WishState;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAiWishCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WishInterpreter INTERPRETER = new WishInterpreter(AiService.getInstance());
    private static final PendingWishSessionRegistry PENDING = new PendingWishSessionRegistry();
    private static final ConcurrentLinkedQueue<CompletedWish> COMPLETED = new ConcurrentLinkedQueue<>();
    /** Covers AI retries, planning/research and the longest native execution watchdog plus margin. */
    private static final long PIPELINE_TIMEOUT_MS = Duration.ofMinutes(7).toMillis();
    private static volatile long connectionGeneration;

    private ClientAiWishCoordinator() { }

    public static void register(WishStartedPacket packet) {
        AiConfig config = AiConfigManager.getInstance().get();
        boolean matches = config.isConfigured()
                && config.providerType() == packet.providerType()
                && config.model().equals(packet.model());
        long now = System.currentTimeMillis();
        PendingWishSession session = new PendingWishSession(packet.sessionId(), packet.originalWish(),
                matches ? config : null, connectionGeneration, now);
        session.cinematicStarted(now);
        PendingWishSession previous = PENDING.register(session);
        if (previous != null) {
            terminateDetached(previous, WishSessionTerminationReason.SUPERSEDED, now);
        }
        WishLifecycleLog.event(packet.sessionId(), "SESSION_CREATED",
                "generation=" + connectionGeneration);
        WishLifecycleLog.event(packet.sessionId(), "CINEMATIC_STARTED", "state=PLAYING");
    }

    /** Handles only the physical/cinematic state of the willow item. */
    public static void updateState(WishStatePacket packet) {
        if (packet.state() == WishState.SNAPPED) {
            begin(packet.correlationId());
            return;
        }
        PendingWishSession session = PENDING.get(packet.correlationId());
        if (packet.state() == WishState.FINISHED) {
            if (session != null) {
                session.cinematicFinished(System.currentTimeMillis());
                WishLifecycleLog.event(packet.correlationId(), "CINEMATIC_FINISHED",
                        "pipelineState=" + session.pipelineState());
            }
            return;
        }
        if (packet.state() == WishState.CANCELLED) {
            terminateSession(packet.correlationId(), WishSessionTerminationReason.SERVER_REJECTED,
                    "wishState=CANCELLED reason=" + packet.reason());
        }
    }

    public static void updatePipelineState(WishPipelineStatePacket packet) {
        PendingWishSession session = PENDING.get(packet.sessionId());
        if (session == null) {
            logDropped(packet.sessionId(), "PIPELINE_STATE_SESSION_NOT_PENDING", null);
            return;
        }
        if (packet.state().terminal()) {
            terminateSession(packet.sessionId(), packet.reason(),
                    "serverState=" + packet.state() + " detail=" + packet.detail());
            return;
        }
        session.transition(packet.state(), System.currentTimeMillis());
        applyHintPhase(packet.sessionId(), packet.state());
        WishLifecycleLog.event(packet.sessionId(), packet.state().name(), packet.detail());
    }

    public static void markPlanning(UUID sessionId) {
        transition(sessionId, WishPipelineState.PLANNING, WishProcessingPhase.PLANNING,
                "PLANNING_STARTED", "source=SERVER_REQUEST");
    }

    public static void markResearching(UUID sessionId) {
        transition(sessionId, WishPipelineState.RESEARCHING, WishProcessingPhase.RESEARCHING,
                "RESEARCH_STARTED", "source=COMPLEX_AGENT");
    }

    public static void markProgramReady(UUID sessionId) {
        PendingWishSession session = PENDING.get(sessionId);
        if (session != null && session.pipelineState() == WishPipelineState.RESEARCHING) {
            WishLifecycleLog.event(sessionId, "RESEARCH_COMPLETED", "status=SUCCESS");
        }
        transition(sessionId, WishPipelineState.PROGRAM_READY, WishProcessingPhase.PREPARING,
                "PROGRAM_CREATED", "status=READY");
    }

    public static void markProgramSent(UUID sessionId) {
        transition(sessionId, WishPipelineState.PROGRAM_SENT, WishProcessingPhase.PREPARING,
                "PROGRAM_SENT", "destination=SERVER");
    }

    public static void failPlanning(UUID sessionId, String detail) {
        terminateSession(sessionId, WishSessionTerminationReason.PLANNING_FAILED, detail);
    }

    private static void transition(UUID sessionId, WishPipelineState state,
                                   WishProcessingPhase hintPhase, String event, String detail) {
        PendingWishSession session = PENDING.get(sessionId);
        if (session == null) {
            logDropped(sessionId, "PIPELINE_TRANSITION_SESSION_NOT_PENDING", null);
            return;
        }
        if (session.transition(state, System.currentTimeMillis())) {
            ClientWishProcessingHints.setPhase(sessionId, hintPhase);
            WishLifecycleLog.event(sessionId, event, detail);
        }
    }

    private static void begin(UUID sessionId) {
        PendingWishSession session = PENDING.get(sessionId);
        if (session == null) {
            logDropped(sessionId, "AI_START_SESSION_NOT_PENDING", null);
            return;
        }
        ClientWishProcessingHints.setPhase(sessionId, WishProcessingPhase.INTERPRETING);
        CompletableFuture<WishInterpretationResult> future;
        if (session.config() == null) {
            future = CompletableFuture.completedFuture(
                    WishInterpretationResult.requestFailure(AiErrorCategory.NOT_CONFIGURED, 0));
        } else {
            LOGGER.info("AI request started session={} provider={} model={} wishLength={}",
                    sessionId, session.config().providerType(), safeModel(session.config().model()),
                    session.wish().length());
            future = INTERPRETER.interpret(session.config(), session.wish(),
                    WishingWillowClientConfig.FULFILLMENT_MODE.get(), sessionId);
        }
        if (!session.aiStarted(future, System.currentTimeMillis())) return;
        WishLifecycleLog.event(sessionId, "AI_REQUEST_STARTED",
                "provider=" + (session.config() == null ? "NOT_CONFIGURED" : session.config().providerType()));
        future.whenComplete((result, throwable) -> completeAi(session, result, throwable));
    }

    /** Runs on the AI completion thread. It never touches Minecraft client/player/network state. */
    private static void completeAi(PendingWishSession owner, WishInterpretationResult result,
                                   Throwable throwable) {
        WishInterpretationResult completed = result;
        if (throwable != null || completed == null) {
            completed = WishInterpretationResult.requestFailure(AiErrorCategory.UNKNOWN, 0);
        }
        PendingWishSession current = PENDING.get(owner.sessionId());
        if (current != owner || owner.generation() != connectionGeneration) {
            logDropped(owner.sessionId(), "SESSION_NOT_PENDING", owner);
            return;
        }
        if (!owner.aiCompleted(completed, System.currentTimeMillis())) {
            logDropped(owner.sessionId(), "SESSION_TERMINAL_BEFORE_AI_RESULT", owner);
            return;
        }
        COMPLETED.add(new CompletedWish(owner.sessionId(), owner.generation()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        expireTimedOutSessions();
        CompletedWish completed;
        while ((completed = COMPLETED.poll()) != null) consume(completed);
    }

    private static void consume(CompletedWish completed) {
        PendingWishSession session = PENDING.get(completed.sessionId());
        if (session == null || session.generation() != completed.generation()
                || completed.generation() != connectionGeneration) {
            logDropped(completed.sessionId(), "SESSION_NOT_PENDING", session);
            return;
        }
        WishInterpretationResult result = session.takeAiResult();
        if (result == null) {
            logDropped(completed.sessionId(), "AI_RESULT_MISSING", session);
            return;
        }
        LOGGER.info("AI response received session={} state={} validation={}",
                completed.sessionId(), result.state(),
                result.state() == InterpretationState.SUCCESS ? "success" : "failure");

        ModNetworking.sendToServer(new SubmitWishInterpretationPacket(completed.sessionId(),
                result.state(), result.errorCategory(), result.interpretation(), result.program()));
        if (result.state() == InterpretationState.SUCCESS) {
            session.transition(WishPipelineState.INTERPRETATION_SENT, System.currentTimeMillis());
            WishLifecycleLog.event(completed.sessionId(), "INTERPRETATION_COMPLETED",
                    "status=SUCCESS submittedTo=SERVER");
        } else {
            terminateSession(completed.sessionId(), WishSessionTerminationReason.AI_FAILED,
                    "state=" + result.state() + " error=" + result.errorCategory());
        }
    }

    private static void expireTimedOutSessions() {
        long now = System.currentTimeMillis();
        for (PendingWishSession session : PENDING.sessions()) {
            if (now - session.createdAt() > PIPELINE_TIMEOUT_MS) {
                terminateSession(session.sessionId(), WishSessionTerminationReason.PIPELINE_TIMEOUT,
                        "pipelineState=" + session.pipelineState() + " lastUpdatedAt=" + session.lastUpdatedAt());
            }
        }
    }

    private static void terminateSession(UUID sessionId, WishSessionTerminationReason reason,
                                         String detail) {
        long now = System.currentTimeMillis();
        PendingWishSession terminated = PENDING.terminate(sessionId, reason, now);
        if (terminated == null) return;
        ClientWishProcessingHints.stop(sessionId);
        WishLifecycleLog.terminated(sessionId, reason, terminated.pipelineState(),
                terminated.createdAt(), now);
        if (detail != null && !detail.isBlank()) {
            WishLifecycleLog.event(sessionId, "SESSION_TERMINATION_DETAIL", detail);
        }
    }

    private static void terminateDetached(PendingWishSession session,
                                          WishSessionTerminationReason reason, long now) {
        if (!session.terminate(reason, now)) return;
        ClientWishProcessingHints.stop(session.sessionId());
        WishLifecycleLog.terminated(session.sessionId(), reason, session.pipelineState(),
                session.createdAt(), now);
    }

    private static void applyHintPhase(UUID sessionId, WishPipelineState state) {
        switch (state) {
            case PLANNING -> ClientWishProcessingHints.setPhase(sessionId, WishProcessingPhase.PLANNING);
            case RESEARCHING -> ClientWishProcessingHints.setPhase(sessionId, WishProcessingPhase.RESEARCHING);
            case PROGRAM_READY, PROGRAM_SENT ->
                    ClientWishProcessingHints.setPhase(sessionId, WishProcessingPhase.PREPARING);
            case EXECUTING -> ClientWishProcessingHints.setPhase(sessionId, WishProcessingPhase.EXECUTING);
            default -> { }
        }
    }

    private static void logDropped(UUID sessionId, String reason, PendingWishSession session) {
        long now = System.currentTimeMillis();
        LOGGER.warn("AI result dropped session={} reason={} aiFutureDone={} aiFutureCancelled={} "
                        + "pipelineState={} cinematicState={} lastState={} cleanupReason={} createdAt={} now={}",
                sessionId, reason,
                session != null && session.aiFutureDone(),
                session != null && session.aiFutureCancelled(),
                session == null ? "MISSING" : session.pipelineState(),
                session == null ? "UNKNOWN" : session.cinematicState(),
                session == null ? "UNKNOWN" : session.pipelineState(),
                session == null ? "UNKNOWN" : session.terminalReason(),
                session == null ? -1L : session.createdAt(), now);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        connectionGeneration++;
        long now = System.currentTimeMillis();
        for (PendingWishSession session : PENDING.terminateAll(
                WishSessionTerminationReason.PLAYER_DISCONNECT, now)) {
            WishLifecycleLog.terminated(session.sessionId(), WishSessionTerminationReason.PLAYER_DISCONNECT,
                    session.pipelineState(), session.createdAt(), now);
        }
        COMPLETED.clear();
    }

    private static String safeModel(String model) {
        return model.chars()
                .map(character -> Character.isISOControl(character) ? '?' : character)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }

    private record CompletedWish(UUID sessionId, long generation) { }
}
