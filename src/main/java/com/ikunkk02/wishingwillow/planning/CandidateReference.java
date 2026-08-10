package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;

import javax.annotation.Nullable;

public record CandidateReference(
        String candidateId,
        WishCapability requestedCapability,
        WishCapability providedCapability,
        MatchType matchType,
        CandidateSourceKind sourceKind,
        String sourceModId,
        String sourceModVersion,
        String featureName,
        FeatureType featureType,
        @Nullable VerifiedRegistryResource registryResource,
        int matchScore,
        int riskScore
) {
}
