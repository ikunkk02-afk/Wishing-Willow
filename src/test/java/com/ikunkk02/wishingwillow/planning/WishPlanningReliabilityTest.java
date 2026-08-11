package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.agent.core.*;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.network.packet.SubmitWishPlanPacket;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WishPlanningReliabilityTest {
    private static final Duration SHORT = Duration.ofMillis(40);

    @Test void supportedUsesSuccessfulAgentWithoutJsonFallback() throws Exception {
        AtomicInteger agentCalls = new AtomicInteger();
        AtomicInteger compatibilityCalls = new AtomicInteger();
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.SUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.UNSUPPORTED),
                () -> { agentCalls.incrementAndGet(); return completed(agentSuccess()); },
                () -> { compatibilityCalls.incrementAndGet(); return completed(planSuccess()); },
                () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertNotNull(outcome.result().draft());
        assertEquals(1, agentCalls.get());
        assertEquals(0, compatibilityCalls.get());
        assertEquals(WishPlanningMode.AGENT_TOOL_MODE, outcome.debug().mode());
    }

    @Test void unsupportedSkipsAgentAndUsesJsonPlanner() throws Exception {
        AtomicInteger agentCalls = new AtomicInteger();
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.UNSUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.SUPPORTED),
                () -> { agentCalls.incrementAndGet(); return completed(agentSuccess()); },
                () -> completed(planSuccess()), () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertEquals(0, agentCalls.get());
        assertNotNull(outcome.result().draft());
        assertEquals(WishAgentFallbackReason.TOOL_CALLING_UNSUPPORTED, outcome.debug().fallbackReason());
    }

    @Test void unknownProbeSupportedEntersAgent() throws Exception {
        AtomicInteger agentCalls = new AtomicInteger();
        AtomicInteger compatibilityCalls = new AtomicInteger();
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.UNKNOWN,
                catalog(), () -> completed(ToolCallingSupport.SUPPORTED),
                () -> { agentCalls.incrementAndGet(); return completed(agentSuccess()); },
                () -> { compatibilityCalls.incrementAndGet(); return completed(planSuccess()); },
                () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertNotNull(outcome.result().draft());
        assertEquals(1, agentCalls.get());
        assertEquals(0, compatibilityCalls.get());
    }

    @Test void unknownProbeTimeoutUsesJsonPlanner() throws Exception {
        AtomicInteger agentCalls = new AtomicInteger();
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.UNKNOWN,
                catalog(), CompletableFuture::new,
                () -> { agentCalls.incrementAndGet(); return completed(agentSuccess()); },
                () -> completed(planSuccess()), () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertEquals(0, agentCalls.get());
        assertNotNull(outcome.result().draft());
        assertEquals(WishAgentFallbackReason.TOOL_SUPPORT_PROBE_FAILED, outcome.debug().fallbackReason());
    }

    @Test void agentModelTimeoutFallsBackToJsonPlanner() throws Exception {
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.SUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.SUPPORTED),
                () -> CompletableFuture.failedFuture(new AiRequestException(AiErrorCategory.TIMEOUT,
                        new TimeoutException("model"))),
                () -> completed(planSuccess()), () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertNotNull(outcome.result().draft());
        assertEquals(WishPlanningMode.COMPATIBILITY_JSON_MODE, outcome.debug().mode());
        assertEquals(WishAgentFallbackReason.AI_REQUEST_TIMEOUT, outcome.debug().fallbackReason());
    }

    @Test void contractReviewTimeoutFallsBackToJsonPlanner() throws Exception {
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.SUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.SUPPORTED),
                () -> completed(agentFailure(WishAgentFallbackReason.CONTRACT_REVIEW_TIMEOUT)),
                () -> completed(planSuccess()), () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertNotNull(outcome.result().draft());
        assertEquals(WishAgentFallbackReason.CONTRACT_REVIEW_TIMEOUT, outcome.debug().fallbackReason());
    }

    @Test void newWishCancelsOldPlanningAndStartsImmediately() throws Exception {
        WishPlanningGeneration generation = new WishPlanningGeneration();
        WishPlanningGeneration.Token wishA = generation.begin(UUID.randomUUID());
        CompletableFuture<String> blockedA = new CompletableFuture<>();
        generation.track(wishA, blockedA);

        WishPlanningGeneration.Token wishB = generation.begin(UUID.randomUUID());
        CompletableFuture<String> resultB = CompletableFuture.completedFuture("B");
        generation.track(wishB, resultB);

        assertTrue(wishA.cancelled());
        assertTrue(blockedA.isCancelled());
        assertEquals("B", resultB.get(100, TimeUnit.MILLISECONDS));
        assertFalse(wishB.cancelled());
    }

    @Test void lateCancelledWishResponseCannotOverwriteNewWish() {
        WishPlanningGeneration generation = new WishPlanningGeneration();
        List<String> submitted = new ArrayList<>();
        CompletableFuture<String> lateModel = new CompletableFuture<>();
        WishPlanningGeneration.Token wishA = generation.begin(UUID.randomUUID());
        CompletableFuture<Void> terminalA = lateModel.thenAccept(value -> {
            if (generation.isCurrent(wishA)) submitted.add(value);
        });
        generation.track(wishA, terminalA);

        WishPlanningGeneration.Token wishB = generation.begin(UUID.randomUUID());
        if (generation.isCurrent(wishB)) submitted.add("B");
        lateModel.complete("A");

        assertEquals(List.of("B"), submitted);
        assertTrue(terminalA.isCancelled());
    }

    @Test void agentFailureJsonSuccessProducesServerValidSubmission() throws Exception {
        WishInterpretation interpretation = PlanningFixtures.interpretation(50, WishDelivery.IMMEDIATE,
                WishCapability.GIVE_ITEM);
        CapabilityCandidate candidate = PlanningFixtures.candidate("candidate-001", WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM, "minecraft:diamond");
        CapabilityCatalog catalog = PlanningFixtures.catalog(candidate);
        WishPlanValidation validation = WishPlanValidator.parseAndValidate(
                PlanningFixtures.planJson(interpretation, candidate.candidateId(), "{\"count\":1}",
                        WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM),
                interpretation, catalog, PlanningFixtures.environment(true, true),
                ExecutionSettingsSnapshot.permissive());
        assertEquals(WishPlanState.READY, validation.state());
        WishPlanResult jsonPlan = WishPlanResult.success(validation.draft());
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.SUPPORTED,
                catalog, () -> completed(ToolCallingSupport.SUPPORTED),
                () -> completed(agentFailure(WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE)),
                () -> completed(jsonPlan), () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);

        SubmitWishPlanPacket packet = SubmitWishPlanPacket.fromResult(UUID.randomUUID(), UUID.randomUUID(),
                outcome.result(), outcome.catalog());
        assertEquals(WishPlanError.NONE, packet.error());
        assertNotNull(packet.catalog());
        assertNotNull(packet.draftJson());
        assertEquals(WishPlanState.READY, WishPlanValidator.parseAndValidate(packet.draftJson(), interpretation,
                packet.catalog(), PlanningFixtures.environment(true, true),
                ExecutionSettingsSnapshot.permissive()).state());
    }

    @Test void agentAndJsonFailureReachExplicitTerminalFailure() throws Exception {
        List<WishAgentDebugState> states = new CopyOnWriteArrayList<>();
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.SUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.SUPPORTED),
                () -> completed(agentFailure(WishAgentFallbackReason.AGENT_TECHNICAL_FAILURE)),
                () -> completed(WishPlanResult.failed(WishPlanError.INVALID_JSON)),
                () -> false, debug -> states.add(debug.state())).get(1, TimeUnit.SECONDS);
        assertNull(outcome.result().draft());
        assertEquals(WishPlanState.FAILED, outcome.result().state());
        assertEquals(WishAgentDebugState.FAILED, outcome.debug().state());
        assertTrue(states.contains(WishAgentDebugState.FALLBACK_STARTED));
        assertTrue(states.contains(WishAgentDebugState.COMPATIBILITY_STARTED));
        assertTrue(states.contains(WishAgentDebugState.FAILED));
    }

    @Test void genuineContractFailureIsNotMisreportedAsAgentTechnicalFailure() throws Exception {
        WishPlanningOutcome outcome = orchestrator().plan(UUID.randomUUID(), ToolCallingSupport.UNSUPPORTED,
                catalog(), () -> completed(ToolCallingSupport.UNSUPPORTED),
                () -> completed(agentSuccess()),
                () -> completed(WishPlanResult.failed(WishPlanError.CONTRACT_NOT_FULFILLED)),
                () -> false, ignored -> { }).get(1, TimeUnit.SECONDS);
        assertEquals(WishFinalizationState.REJECTED, outcome.debug().finalizationState());
        assertEquals(WishPlanError.CONTRACT_NOT_FULFILLED, outcome.result().error());
    }

    private static WishPlanningOrchestrator orchestrator() {
        return new WishPlanningOrchestrator(SHORT, Duration.ofMillis(100), Duration.ofMillis(100));
    }

    private static CapabilityCatalog catalog() {
        return CapabilityCatalog.create(List.of(), List.of(), "READY", "", "registry");
    }

    private static WishPlanResult planSuccess() {
        return WishPlanResult.success(new WishPlanDraft(1, "compatibility", WishDelivery.IMMEDIATE,
                1, WishEstimatedDuration.INSTANT, List.of()));
    }

    private static WishAgentRunResult agentSuccess() {
        UUID session = UUID.randomUUID();
        return new WishAgentRunResult(planSuccess(), catalog(), debug(session, WishAgentDebugState.COMPLETED,
                WishFinalizationState.SUCCESS, WishAgentFallbackReason.NONE));
    }

    private static WishAgentRunResult agentFailure(WishAgentFallbackReason reason) {
        UUID session = UUID.randomUUID();
        return new WishAgentRunResult(WishPlanResult.failed(WishPlanError.AI_REQUEST_FAILED), catalog(),
                debug(session, WishAgentDebugState.FALLBACK_STARTED,
                        WishFinalizationState.TECHNICAL_FAILURE, reason));
    }

    private static WishAgentDebugSnapshot debug(UUID session, WishAgentDebugState state,
                                                 WishFinalizationState finalization,
                                                 WishAgentFallbackReason reason) {
        return new WishAgentDebugSnapshot(session, WishPlanningMode.AGENT_TOOL_MODE, state, 2, 3,
                List.of("search_minecraft_tools"), "search_minecraft_tools", "SUCCESS/TOOLS_FOUND",
                WishVerificationState.NOT_VERIFIED, finalization, reason, 10L);
    }

    private static <T> CompletableFuture<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }
}
