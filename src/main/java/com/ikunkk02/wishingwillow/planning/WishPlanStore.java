package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.wish.WishRecord;
import com.ikunkk02.wishingwillow.wish.WishSavedData;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import java.util.EnumMap;
import java.util.Map;

public final class WishPlanStore {
    private WishPlanStore() { }

    public static boolean updateState(MinecraftServer server, UUID sessionId, WishPlanState state) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY || record.planState() == WishPlanState.STALE) return false;
        data.update(record.withPlanning(state, WishPlanError.NONE, null));
        return true;
    }

    public static boolean fail(MinecraftServer server, UUID sessionId, WishPlanError error) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return false;
        data.update(record.withPlanning(WishPlanState.FAILED, error, null)
                .withExecution(null, WishExecutionState.FAILED, executionError(error),
                        "planning=" + error.name()));
        return true;
    }

    public static boolean partial(MinecraftServer server, UUID sessionId, WishPlanError error) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return false;
        data.update(record.withPlanning(WishPlanState.PARTIAL, error, null)
                .withExecution(null, WishExecutionState.FAILED, WishExecutionAcceptError.VALIDATION_FAILED,
                        "partial plan has no executable primary step: " + error.name()));
        return true;
    }

    public static WishPlanState accept(MinecraftServer server, UUID sessionId, WishInterpretation interpretation,
                                       String rawDraft, CapabilityCatalog catalog) {
        return accept(server, sessionId, UUID.randomUUID(), interpretation, rawDraft, catalog);
    }

    public static WishPlanState accept(MinecraftServer server, UUID sessionId, UUID planId,
                                       WishInterpretation interpretation, String rawDraft,
                                       CapabilityCatalog catalog) {
        return store(server,sessionId,planId,interpretation,rawDraft,catalog,false);
    }

    public static WishPlanState replaceAfterExecutionRejection(MinecraftServer server, UUID sessionId,
                                                               WishInterpretation interpretation,
                                                               WishPlanDraft draft, CapabilityCatalog catalog) {
        return store(server,sessionId,UUID.randomUUID(),interpretation,WishPlanJson.toAiJson(draft),catalog,true);
    }

    private static WishPlanState store(MinecraftServer server, UUID sessionId, UUID planId,
                                       WishInterpretation interpretation, String rawDraft,
                                       CapabilityCatalog catalog, boolean replaceRejected) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || (!replaceRejected && record.planState() == WishPlanState.READY)
                || (replaceRejected && (record.executionId()!=null
                || record.executionState()!=WishExecutionState.FAILED)))
            throw new IllegalArgumentException("INVALID_SESSION");
        CapabilityCatalog canonicalCatalog = canonicalizeCatalog(catalog);
        WishPlanValidation validation = WishPlanValidator.parseAndValidate(rawDraft, interpretation,
                canonicalCatalog, new ServerPlanningEnvironment(server), ExecutionSettingsSnapshot.planning());
        Set<String> mods = new LinkedHashSet<>();
        Set<String> registries = new LinkedHashSet<>();
        validation.draft().steps().forEach(step -> {
            mods.add(step.candidateReference().sourceModId());
            if (step.candidateReference().registryResource() != null) {
                registries.add(step.candidateReference().registryResource().id());
            }
        });
        List<WishPlanStep> canonicalSteps = new ArrayList<>();
        for (WishPlanStep step : validation.draft().steps()) canonicalSteps.add(canonicalize(step));
        WishPlan plan = new WishPlan(planId, sessionId, 1, validation.draft().summary(),
                validation.draft().delivery(), validation.draft().severity(),
                validation.draft().estimatedDuration(), canonicalSteps, mods, registries,
                validation.unfulfilledCapabilities(), server.overworld().getGameTime(), System.currentTimeMillis(),
                canonicalCatalog.knowledgeState(), canonicalCatalog.knowledgeDigest(),
                canonicalCatalog.registryDigest(), canonicalCatalog.catalogHash());
        WishPlanError storedError=validation.state()==WishPlanState.PARTIAL
                ?WishPlanError.UNSATISFIED_CAPABILITIES:WishPlanError.NONE;
        data.update(record.withPlanning(validation.state(), storedError, plan)
                .withExecution(null, WishExecutionState.NOT_ACCEPTED, WishExecutionAcceptError.NONE, ""));
        return validation.state();
    }

    private static WishPlanStep canonicalize(WishPlanStep step) {
        CandidateReference source = step.candidateReference();
        VerifiedRegistryResource resource = source.registryResource();
        String modId = resource == null ? source.sourceModId()
                : net.minecraft.resources.ResourceLocation.tryParse(resource.id()).getNamespace();
        String version = modId.equals("minecraft") ? "1.20.1" : ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        CandidateSourceKind kind = resource == null ? source.sourceKind()
                : modId.equals("minecraft") ? CandidateSourceKind.VANILLA_REGISTRY : CandidateSourceKind.MOD_FEATURE;
        int risk = CapabilityMatcher.risk(source.providedCapability());
        if (resource != null && resource.type() == com.ikunkk02.wishingwillow.research.RegistryEntryType.ENTITY
                && !modId.equals("minecraft")) risk = Math.max(70, risk);
        CandidateReference canonical = new CandidateReference(source.candidateId(), source.requestedCapability(),
                source.providedCapability(), new CapabilityRelationGraph().relation(source.requestedCapability(),
                source.providedCapability()), kind, modId, version, source.featureName(), source.featureType(),
                resource, 0, risk);
        return new WishPlanStep(step.stepIndex(), step.timing(), step.delaySeconds(), step.trigger(), step.action(),
                step.capability(), step.candidateId(), step.target(), step.parameters(), step.selectionReason(),
                canonical, step.batchId());
    }

    private static CapabilityCatalog canonicalizeCatalog(CapabilityCatalog catalog) {
        List<CapabilityCandidate> candidates = new ArrayList<>();
        for (CapabilityCandidate source : catalog.candidates()) {
            VerifiedRegistryResource resource = source.registryResource();
            String modId = source.sourceModId();
            String version = source.sourceModVersion();
            CandidateSourceKind kind = source.sourceKind();
            if (resource != null) {
                net.minecraft.resources.ResourceLocation id =
                        net.minecraft.resources.ResourceLocation.tryParse(resource.id());
                if (id != null) {
                    modId = id.getNamespace();
                    version = modId.equals("minecraft") ? "1.20.1" : ModList.get()
                            .getModContainerById(modId)
                            .map(container -> container.getModInfo().getVersion().toString()).orElse("");
                    kind = modId.equals("minecraft")
                            ? CandidateSourceKind.VANILLA_REGISTRY : CandidateSourceKind.MOD_FEATURE;
                }
            }
            int risk = CapabilityMatcher.risk(source.providedCapability());
            if (resource != null && resource.type() == com.ikunkk02.wishingwillow.research.RegistryEntryType.ENTITY
                    && !resource.id().startsWith("minecraft:")) risk = Math.max(70, risk);
            candidates.add(new CapabilityCandidate(source.candidateId(), source.requestedCapability(),
                    source.providedCapability(), new CapabilityRelationGraph().relation(
                    source.requestedCapability(), source.providedCapability()), kind, modId,
                    source.sourceModName(), version, source.featureName(), source.featureType(), resource,
                    source.description(), source.knowledgeLevel(), source.researchConfidence(),
                    source.featureConfidence(), source.horrorScore(), source.wishRelevance(), risk,
                    source.matchScore()));
        }
        Map<WishCapability, List<CapabilityCandidate>> grouped = new EnumMap<>(WishCapability.class);
        candidates.forEach(candidate -> grouped.computeIfAbsent(candidate.requestedCapability(),
                ignored -> new ArrayList<>()).add(candidate));
        List<CapabilityMatchSet> sets = new ArrayList<>();
        grouped.forEach((capability, values) -> sets.add(new CapabilityMatchSet(capability,
                values.isEmpty() ? MatchType.UNSATISFIED : values.get(0).matchType(), values)));
        return CapabilityCatalog.create(sets, candidates, catalog.knowledgeState(),
                catalog.knowledgeDigest(), catalog.registryDigest());
    }

    public static void revalidateAll(MinecraftServer server) {
        WishSavedData data = WishSavedData.get(server);
        for (WishRecord record : data.allRecords()) {
            if ((record.planState() == WishPlanState.READY || record.planState() == WishPlanState.PARTIAL)
                    && record.plan() != null && !record.executionState().terminal()) {
                try {
                    WishPlanValidator.validateStored(record.plan(), new ServerPlanningEnvironment(server),
                            ExecutionSettingsSnapshot.planning());
                } catch (IllegalArgumentException exception) {
                    WishRecord stale=record.withPlanning(WishPlanState.STALE, WishPlanError.STALE_RESOURCE, record.plan());
                    if(record.executionId()==null)stale=stale.withExecution(null,WishExecutionState.STALE,
                            WishExecutionAcceptError.STALE_RESOURCE,"startup plan revalidation: "+exception.getMessage());
                    data.update(stale);
                }
            }
        }
    }

    private static WishExecutionAcceptError executionError(WishPlanError error){
        return switch(error){
            case EXECUTION_DISABLED->WishExecutionAcceptError.EXECUTION_DISABLED;
            case INVALID_CANDIDATE->WishExecutionAcceptError.INVALID_CANDIDATE;
            case INVALID_REGISTRY,INVALID_ACTION->WishExecutionAcceptError.INVALID_RESOURCE;
            case INVALID_PARAMETER->WishExecutionAcceptError.INVALID_PARAMETER;
            case BUDGET_EXCEEDED->WishExecutionAcceptError.BUDGET_EXCEEDED;
            case RISK_TOO_HIGH->WishExecutionAcceptError.RISK_TOO_HIGH;
            case THIRD_PARTY_ENTITY_DISABLED->WishExecutionAcceptError.THIRD_PARTY_ENTITY_DISABLED;
            case THIRD_PARTY_ENTITY_SEVERITY->WishExecutionAcceptError.THIRD_PARTY_ENTITY_SEVERITY;
            case BLOCK_MODIFICATION_DISABLED->WishExecutionAcceptError.BLOCK_MODIFICATION_DISABLED;
            case EXPLOSIONS_DISABLED->WishExecutionAcceptError.EXPLOSIONS_DISABLED;
            case DESTRUCTIVE_EXPLOSIONS_DISABLED->WishExecutionAcceptError.DESTRUCTIVE_EXPLOSIONS_DISABLED;
            case CROSS_DIMENSION_TELEPORT_DISABLED->WishExecutionAcceptError.CROSS_DIMENSION_TELEPORT_DISABLED;
            case DESTRUCTIVE_SEVERITY_DISABLED->WishExecutionAcceptError.DESTRUCTIVE_SEVERITY_DISABLED;
            case DEBUG_SAFE_MODE->WishExecutionAcceptError.DEBUG_SAFE_MODE;
            case INVALID_EVENT->WishExecutionAcceptError.INVALID_EVENT;
            case UNTRUSTED_REGISTRY_CANDIDATE->WishExecutionAcceptError.UNTRUSTED_REGISTRY_CANDIDATE;
            case UNSUPPORTED_ACTION->WishExecutionAcceptError.UNSUPPORTED_ACTION;
            case STALE_RESOURCE,MISSING_MOD->WishExecutionAcceptError.STALE_RESOURCE;
            default->WishExecutionAcceptError.VALIDATION_FAILED;
        };
    }
}
