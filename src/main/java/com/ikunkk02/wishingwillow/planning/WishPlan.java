package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WishPlan(
        UUID planId,
        UUID wishSessionId,
        int schemaVersion,
        String summary,
        WishDelivery delivery,
        int severity,
        WishEstimatedDuration estimatedDuration,
        List<WishPlanStep> steps,
        Set<String> selectedModIds,
        Set<String> selectedRegistryIds,
        Set<WishCapability> unfulfilledCapabilities,
        long createdGameTime,
        long createdAtEpochMillis,
        String knowledgeState,
        String knowledgeDigest,
        String registryDigest,
        String catalogHash
) {
    public WishPlan {
        steps = List.copyOf(steps);
        selectedModIds = Set.copyOf(selectedModIds);
        selectedRegistryIds = Set.copyOf(selectedRegistryIds);
        unfulfilledCapabilities = Set.copyOf(unfulfilledCapabilities);
    }
}
