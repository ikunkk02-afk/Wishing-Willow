package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import java.util.List;

public record ModFeature(
        String name,
        FeatureType type,
        String description,
        List<WishCapability> possibleCapabilities,
        List<String> registryCandidates,
        List<VerifiedRegistryResource> verifiedRegistryResources,
        double confidence
) {
    public ModFeature {
        possibleCapabilities = List.copyOf(possibleCapabilities);
        registryCandidates = List.copyOf(registryCandidates);
        verifiedRegistryResources = List.copyOf(verifiedRegistryResources);
    }
}
