package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;

import javax.annotation.Nullable;

public record CapabilityCandidate(
        String candidateId,
        WishCapability requestedCapability,
        WishCapability providedCapability,
        MatchType matchType,
        CandidateSourceKind sourceKind,
        String sourceModId,
        String sourceModName,
        String sourceModVersion,
        String featureName,
        FeatureType featureType,
        @Nullable VerifiedRegistryResource registryResource,
        String description,
        KnowledgeLevel knowledgeLevel,
        double researchConfidence,
        double featureConfidence,
        int horrorScore,
        int wishRelevance,
        int riskScore,
        int matchScore
) {
    public CapabilityCandidate withCandidateId(String id) {
        return new CapabilityCandidate(id, requestedCapability, providedCapability, matchType, sourceKind,
                sourceModId, sourceModName, sourceModVersion, featureName, featureType, registryResource,
                description, knowledgeLevel, researchConfidence, featureConfidence, horrorScore, wishRelevance,
                riskScore, matchScore);
    }

    public CandidateReference reference() {
        return new CandidateReference(candidateId, requestedCapability, providedCapability, matchType, sourceKind,
                sourceModId, sourceModVersion, featureName, featureType, registryResource, matchScore, riskScore);
    }
}
