package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WishPlanNbt {
    private WishPlanNbt() { }

    public static CompoundTag save(WishPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PlanId", plan.planId());
        tag.putUUID("WishSessionId", plan.wishSessionId());
        tag.putInt("SchemaVersion", plan.schemaVersion());
        tag.putString("Summary", plan.summary());
        tag.putString("Delivery", plan.delivery().name());
        tag.putInt("Severity", plan.severity());
        tag.putString("EstimatedDuration", plan.estimatedDuration().name());
        ListTag steps = new ListTag();
        plan.steps().stream().map(WishPlanNbt::saveStep).forEach(steps::add);
        tag.put("Steps", steps);
        tag.put("SelectedModIds", strings(plan.selectedModIds()));
        tag.put("SelectedRegistryIds", strings(plan.selectedRegistryIds()));
        ListTag unfulfilled = new ListTag();
        plan.unfulfilledCapabilities().stream().map(Enum::name).sorted()
                .map(StringTag::valueOf).forEach(unfulfilled::add);
        tag.put("UnfulfilledCapabilities", unfulfilled);
        tag.putLong("CreatedGameTime", plan.createdGameTime());
        tag.putLong("CreatedAt", plan.createdAtEpochMillis());
        tag.putString("KnowledgeState", plan.knowledgeState());
        tag.putString("KnowledgeDigest", plan.knowledgeDigest());
        tag.putString("RegistryDigest", plan.registryDigest());
        tag.putString("CatalogHash", plan.catalogHash());
        return tag;
    }

    public static WishPlan load(CompoundTag tag) {
        List<WishPlanStep> steps = new ArrayList<>();
        for (Tag value : tag.getList("Steps", Tag.TAG_COMPOUND)) steps.add(loadStep((CompoundTag) value));
        Set<WishCapability> unfulfilled = new HashSet<>();
        for (Tag value : tag.getList("UnfulfilledCapabilities", Tag.TAG_STRING)) {
            unfulfilled.add(WishCapability.valueOf(value.getAsString()));
        }
        return new WishPlan(tag.getUUID("PlanId"), tag.getUUID("WishSessionId"), tag.getInt("SchemaVersion"),
                tag.getString("Summary"), WishDelivery.valueOf(tag.getString("Delivery")), tag.getInt("Severity"),
                WishEstimatedDuration.valueOf(tag.getString("EstimatedDuration")), steps,
                readStrings(tag.getList("SelectedModIds", Tag.TAG_STRING)),
                readStrings(tag.getList("SelectedRegistryIds", Tag.TAG_STRING)), unfulfilled,
                tag.getLong("CreatedGameTime"), tag.getLong("CreatedAt"), tag.getString("KnowledgeState"),
                tag.getString("KnowledgeDigest"), tag.getString("RegistryDigest"), tag.getString("CatalogHash"));
    }

    private static CompoundTag saveStep(WishPlanStep step) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StepIndex", step.stepIndex());
        tag.putString("Timing", step.timing().name());
        tag.putInt("DelaySeconds", step.delaySeconds());
        tag.putString("Trigger", step.trigger().name());
        tag.putString("Action", step.action().name());
        tag.putString("Capability", step.capability().name());
        tag.putString("CandidateId", step.candidateId());
        tag.putString("Target", step.target().name());
        tag.putString("ParametersJson", step.parameters().toString());
        tag.putString("SelectionReason", step.selectionReason());
        tag.putString("BatchId", step.batchId());
        tag.put("CandidateReference", saveReference(step.candidateReference()));
        return tag;
    }

    private static WishPlanStep loadStep(CompoundTag tag) {
        return new WishPlanStep(tag.getInt("StepIndex"), WishStepTiming.valueOf(tag.getString("Timing")),
                tag.getInt("DelaySeconds"), WishTriggerType.valueOf(tag.getString("Trigger")),
                WishActionType.valueOf(tag.getString("Action")), WishCapability.valueOf(tag.getString("Capability")),
                tag.getString("CandidateId"), WishTargetType.valueOf(tag.getString("Target")),
                JsonParser.parseString(tag.getString("ParametersJson")).getAsJsonObject(),
                tag.getString("SelectionReason"), loadReference(tag.getCompound("CandidateReference")),
                tag.getString("BatchId"));
    }

    private static CompoundTag saveReference(CandidateReference reference) {
        CompoundTag tag = new CompoundTag();
        tag.putString("CandidateId", reference.candidateId());
        tag.putString("RequestedCapability", reference.requestedCapability().name());
        tag.putString("ProvidedCapability", reference.providedCapability().name());
        tag.putString("MatchType", reference.matchType().name());
        tag.putString("SourceKind", reference.sourceKind().name());
        tag.putString("SourceModId", reference.sourceModId());
        tag.putString("SourceModVersion", reference.sourceModVersion());
        tag.putString("FeatureName", reference.featureName());
        tag.putString("FeatureType", reference.featureType().name());
        tag.putInt("MatchScore", reference.matchScore());
        tag.putInt("RiskScore", reference.riskScore());
        if (reference.registryResource() != null) {
            tag.putString("RegistryType", reference.registryResource().type().name());
            tag.putString("RegistryId", reference.registryResource().id());
        }
        return tag;
    }

    private static CandidateReference loadReference(CompoundTag tag) {
        VerifiedRegistryResource resource = tag.contains("RegistryId", Tag.TAG_STRING)
                ? new VerifiedRegistryResource(RegistryEntryType.valueOf(tag.getString("RegistryType")),
                tag.getString("RegistryId")) : null;
        return new CandidateReference(tag.getString("CandidateId"),
                WishCapability.valueOf(tag.getString("RequestedCapability")),
                WishCapability.valueOf(tag.getString("ProvidedCapability")),
                MatchType.valueOf(tag.getString("MatchType")), CandidateSourceKind.valueOf(tag.getString("SourceKind")),
                tag.getString("SourceModId"), tag.getString("SourceModVersion"), tag.getString("FeatureName"),
                FeatureType.valueOf(tag.getString("FeatureType")), resource,
                tag.getInt("MatchScore"), tag.getInt("RiskScore"));
    }

    private static ListTag strings(Set<String> values) {
        ListTag result = new ListTag();
        values.stream().sorted().map(StringTag::valueOf).forEach(result::add);
        return result;
    }

    private static Set<String> readStrings(ListTag values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return result;
    }
}
