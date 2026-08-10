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
        data.update(record.withPlanning(WishPlanState.FAILED, error, null));
        return true;
    }

    public static boolean partial(MinecraftServer server, UUID sessionId, WishPlanError error) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) return false;
        data.update(record.withPlanning(WishPlanState.PARTIAL, error, null));
        return true;
    }

    public static WishPlanState accept(MinecraftServer server, UUID sessionId, WishInterpretation interpretation,
                                       String rawDraft, CapabilityCatalog catalog) {
        return accept(server, sessionId, UUID.randomUUID(), interpretation, rawDraft, catalog);
    }

    public static WishPlanState accept(MinecraftServer server, UUID sessionId, UUID planId,
                                       WishInterpretation interpretation, String rawDraft,
                                       CapabilityCatalog catalog) {
        WishSavedData data = WishSavedData.get(server);
        WishRecord record = data.getBySession(sessionId);
        if (record == null || record.planState() == WishPlanState.READY) throw new IllegalArgumentException("INVALID_SESSION");
        WishPlanValidation validation = WishPlanValidator.parseAndValidate(rawDraft, interpretation, catalog,
                new ServerPlanningEnvironment(server));
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
                catalog.knowledgeState(), catalog.knowledgeDigest(), catalog.registryDigest(), catalog.catalogHash());
        data.update(record.withPlanning(validation.state(), WishPlanError.NONE, plan));
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
                step.capability(), step.candidateId(), step.target(), step.parameters(), step.selectionReason(), canonical);
    }

    public static void revalidateAll(MinecraftServer server) {
        WishSavedData data = WishSavedData.get(server);
        for (WishRecord record : data.allRecords()) {
            if (record.planState() == WishPlanState.READY && record.plan() != null) {
                try {
                    WishPlanValidator.validateStored(record.plan(), new ServerPlanningEnvironment(server));
                } catch (IllegalArgumentException exception) {
                    data.update(record.withPlanning(WishPlanState.STALE, WishPlanError.STALE_RESOURCE, record.plan()));
                }
            }
        }
    }
}
