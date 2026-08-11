package com.ikunkk02.wishingwillow.agent.core;

import com.ikunkk02.wishingwillow.agent.platform.MinecraftToolPlatform;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishContract;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.CapabilityCandidate;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.CapabilityMatchSet;
import com.ikunkk02.wishingwillow.planning.MatchType;
import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import com.ikunkk02.wishingwillow.planning.WishEstimatedDuration;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Mutable state of one agent invocation; all frozen inputs are immutable snapshots. */
public final class WishAgentSession {
    public static final int MAX_ITERATIONS = 8;
    public static final int MAX_TOTAL_TOOL_CALLS = 20;
    public static final int MAX_RAW_STEPS = 512;
    public static final Duration MAX_DURATION = Duration.ofSeconds(60);

    private final UUID sessionId;
    private final String originalWish;
    private final WishContract contract;
    private final WishInterpretation interpretation;
    private final WishContextSnapshot context;
    private final RegistrySnapshot registrySnapshot;
    private final KnowledgeBaseSnapshot knowledgeBaseSnapshot;
    private final ExecutionSettingsSnapshot executionSettingsSnapshot;
    private final MinecraftToolPlatform platform;
    private final BooleanSupplier cancelled;
    private final Consumer<WishAgentDebugSnapshot> debugListener;
    private final Instant startedAt = Instant.now();
    private final List<WishPlanStep> steps = new ArrayList<>();
    private final Map<String, CapabilityCandidate> candidates = new LinkedHashMap<>();
    private final List<ToolCallHistoryEntry> history = new ArrayList<>();
    private final Set<String> discoveredTools = new LinkedHashSet<>();
    private int iterations;
    private int toolCallCount;
    private int revision;
    private int verifiedRevision = -1;
    private int validRevision = -1;
    private WishPlanningMode mode = WishPlanningMode.AGENT_TOOL_MODE;
    private WishVerificationState verificationState = WishVerificationState.NOT_VERIFIED;
    private WishFinalizationState finalizationState = WishFinalizationState.NOT_ATTEMPTED;
    private WishAgentDebugState debugState = WishAgentDebugState.AGENT_STARTED;
    private WishAgentFallbackReason fallbackReason = WishAgentFallbackReason.NONE;
    private String lastTool = "";
    private String lastToolStatus = "";
    private boolean skillActivated;

    public WishAgentSession(UUID sessionId, String originalWish, WishInterpretation interpretation,
                            WishContextSnapshot context, RegistrySnapshot registrySnapshot,
                            KnowledgeBaseSnapshot knowledgeBaseSnapshot,
                            ExecutionSettingsSnapshot executionSettingsSnapshot,
                             CapabilityCatalog initialCatalog, MinecraftToolPlatform platform,
                             BooleanSupplier cancelled) {
        this(sessionId, originalWish, interpretation, context, registrySnapshot, knowledgeBaseSnapshot,
                executionSettingsSnapshot, initialCatalog, platform, cancelled, ignored -> { });
    }

    public WishAgentSession(UUID sessionId, String originalWish, WishInterpretation interpretation,
                            WishContextSnapshot context, RegistrySnapshot registrySnapshot,
                            KnowledgeBaseSnapshot knowledgeBaseSnapshot,
                            ExecutionSettingsSnapshot executionSettingsSnapshot,
                            CapabilityCatalog initialCatalog, MinecraftToolPlatform platform,
                            BooleanSupplier cancelled, Consumer<WishAgentDebugSnapshot> debugListener) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.originalWish = java.util.Objects.requireNonNullElse(originalWish, "");
        this.interpretation = java.util.Objects.requireNonNull(interpretation);
        this.contract = interpretation.contract();
        this.context = java.util.Objects.requireNonNull(context);
        this.registrySnapshot = java.util.Objects.requireNonNull(registrySnapshot);
        this.knowledgeBaseSnapshot = java.util.Objects.requireNonNull(knowledgeBaseSnapshot);
        this.executionSettingsSnapshot = java.util.Objects.requireNonNull(executionSettingsSnapshot);
        this.platform = java.util.Objects.requireNonNull(platform);
        this.cancelled = cancelled == null ? () -> false : cancelled;
        this.debugListener = debugListener == null ? ignored -> { } : debugListener;
        if (initialCatalog != null) initialCatalog.candidates().forEach(this::addCandidate);
    }

    public UUID sessionId() { return sessionId; }
    public String originalWish() { return originalWish; }
    public WishContract contract() { return contract; }
    public WishInterpretation interpretation() { return interpretation; }
    public WishContextSnapshot context() { return context; }
    public RegistrySnapshot registrySnapshot() { return registrySnapshot; }
    public KnowledgeBaseSnapshot knowledgeBaseSnapshot() { return knowledgeBaseSnapshot; }
    public ExecutionSettingsSnapshot executionSettingsSnapshot() { return executionSettingsSnapshot; }
    public MinecraftToolPlatform platform() { return platform; }
    public synchronized int iterations() { return iterations; }
    public synchronized int toolCallCount() { return toolCallCount; }
    public synchronized int revision() { return revision; }
    public synchronized WishPlanningMode mode() { return mode; }
    public synchronized WishVerificationState verificationState() { return verificationState; }
    public synchronized WishFinalizationState finalizationState() { return finalizationState; }
    public synchronized WishAgentFallbackReason fallbackReason() { return fallbackReason; }
    public synchronized boolean skillActivated() { return skillActivated; }
    public synchronized List<ToolCallHistoryEntry> history() { return List.copyOf(history); }
    public synchronized Set<String> discoveredTools() { return Set.copyOf(discoveredTools); }
    public boolean cancelled() { return cancelled.getAsBoolean(); }
    public boolean timedOut() { return Duration.between(startedAt, Instant.now()).compareTo(MAX_DURATION) > 0; }
    public Duration remainingDuration() {
        Duration remaining = MAX_DURATION.minus(Duration.between(startedAt, Instant.now()));
        return remaining.isNegative() || remaining.isZero() ? Duration.ofMillis(1) : remaining;
    }
    public long elapsedMs() { return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis()); }

    public synchronized boolean beginIteration() {
        if (iterations >= MAX_ITERATIONS || cancelled() || timedOut()) return false;
        iterations++;
        return true;
    }

    public synchronized boolean reserveToolCall() {
        if (toolCallCount >= MAX_TOTAL_TOOL_CALLS || cancelled() || timedOut()) return false;
        toolCallCount++;
        return true;
    }

    public synchronized void activateSkill() { skillActivated = true; }
    public synchronized void setMode(WishPlanningMode value) { mode = value; }
    public synchronized void discover(String tool) { if (tool != null && !tool.isBlank()) discoveredTools.add(tool); }
    public synchronized void record(ToolCallHistoryEntry entry) {
        history.add(entry);
        lastTool = entry.toolName();
        lastToolStatus = entry.status().name() + "/" + entry.code();
    }

    public synchronized void markToolCalled(String tool) {
        lastTool = tool == null ? "" : tool;
        lastToolStatus = "RUNNING";
    }

    public synchronized void markFallbackReason(WishAgentFallbackReason reason) {
        if (reason != null && reason != WishAgentFallbackReason.NONE) fallbackReason = reason;
    }

    public void publish(WishAgentDebugState state) {
        WishAgentDebugSnapshot snapshot;
        synchronized (this) {
            debugState = java.util.Objects.requireNonNull(state);
            snapshot = debugSnapshotLocked();
        }
        debugListener.accept(snapshot);
    }

    public synchronized CapabilityCandidate candidate(String candidateId) { return candidates.get(candidateId); }
    public synchronized List<CapabilityCandidate> candidates() { return List.copyOf(candidates.values()); }

    public synchronized String addCandidate(CapabilityCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("INVALID_CANDIDATE");
        for (CapabilityCandidate existing : candidates.values()) {
            if (existing.requestedCapability() == candidate.requestedCapability()
                    && java.util.Objects.equals(existing.registryResource(), candidate.registryResource())
                    && existing.featureName().equals(candidate.featureName())) return existing.candidateId();
        }
        if (candidates.size() >= CapabilityCatalog.MAX_CANDIDATES) throw new IllegalArgumentException("TOO_MANY_CANDIDATES");
        String id = "candidate-%03d".formatted(candidates.size() + 1);
        CapabilityCandidate accepted = candidate.withCandidateId(id);
        candidates.put(id, accepted);
        return id;
    }

    public synchronized void addStep(WishPlanStep step) {
        if (finalizationState == WishFinalizationState.SUCCESS) throw new IllegalStateException("PLAN_FINALIZED");
        if (steps.size() >= MAX_RAW_STEPS) throw new IllegalArgumentException("TOO_MANY_PLAN_STEPS");
        steps.add(step);
        revision++;
        verifiedRevision = -1;
        validRevision = -1;
        verificationState = WishVerificationState.NOT_VERIFIED;
    }

    public synchronized int nextStepIndex() { return steps.size(); }
    public synchronized List<WishPlanStep> steps() { return List.copyOf(steps); }

    public synchronized WishPlanDraft draft() {
        String summary = interpretation.fulfillment().method().isBlank()
                ? "Agent-generated contract-preserving plan" : interpretation.fulfillment().method();
        return new WishPlanDraft(2, summary, interpretation.delivery(), interpretation.severity(),
                WishEstimatedDuration.INSTANT, List.copyOf(steps));
    }

    public synchronized CapabilityCatalog catalog() {
        Map<com.ikunkk02.wishingwillow.ai.WishCapability, List<CapabilityCandidate>> grouped =
                new EnumMap<>(com.ikunkk02.wishingwillow.ai.WishCapability.class);
        candidates.values().forEach(candidate -> grouped.computeIfAbsent(candidate.requestedCapability(),
                ignored -> new ArrayList<>()).add(candidate));
        List<CapabilityMatchSet> matchSets = new ArrayList<>();
        grouped.forEach((capability, values) -> matchSets.add(new CapabilityMatchSet(capability,
                values.isEmpty() ? MatchType.UNSATISFIED : values.get(0).matchType(), values)));
        return CapabilityCatalog.create(matchSets, List.copyOf(candidates.values()),
                knowledgeBaseSnapshot.state().name(), "", registrySnapshot.digest());
    }

    public synchronized void markVerification(WishVerificationState state) {
        verificationState = state;
        verifiedRevision = state == WishVerificationState.CONTRACT_FULFILLED ? revision : -1;
    }

    public synchronized void markValid(boolean valid) { validRevision = valid ? revision : -1; }
    public synchronized boolean canFinalize() {
        return verifiedRevision == revision && validRevision == revision
                && verificationState == WishVerificationState.CONTRACT_FULFILLED;
    }
    public synchronized void markFinalization(WishFinalizationState state) { finalizationState = state; }

    public synchronized WishAgentDebugSnapshot debugSnapshot() {
        return debugSnapshotLocked();
    }

    private WishAgentDebugSnapshot debugSnapshotLocked() {
        return new WishAgentDebugSnapshot(sessionId, mode, debugState, iterations, toolCallCount,
                history.stream().map(ToolCallHistoryEntry::toolName).distinct().toList(),
                lastTool, lastToolStatus, verificationState, finalizationState, fallbackReason, elapsedMs());
    }
}
