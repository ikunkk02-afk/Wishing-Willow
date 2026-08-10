package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import java.util.List;
import java.util.Set;

public record ModKnowledge(
        int schemaVersion,
        String modId,
        String name,
        String version,
        ModCategory category,
        String summary,
        int horrorScore,
        int wishRelevance,
        List<String> themes,
        List<ModFeature> features,
        Set<WishCapability> availableCapabilities,
        double researchConfidence,
        Set<ResearchSource> researchSources,
        KnowledgeLevel knowledgeLevel,
        String registryDigest
) {
    public ModKnowledge {
        themes = List.copyOf(themes);
        features = List.copyOf(features);
        availableCapabilities = Set.copyOf(availableCapabilities);
        researchSources = Set.copyOf(researchSources);
    }
}
