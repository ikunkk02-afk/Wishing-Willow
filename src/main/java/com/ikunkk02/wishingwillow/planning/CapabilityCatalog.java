package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public record CapabilityCatalog(List<CapabilityMatchSet> matchSets, List<CapabilityCandidate> candidates,
                                String knowledgeState, String knowledgeDigest, String registryDigest,
                                String catalogHash) {
    public static final int MAX_CANDIDATES = 512;

    public CapabilityCatalog {
        matchSets = List.copyOf(matchSets);
        candidates = List.copyOf(candidates);
        if (candidates.size() > MAX_CANDIDATES) throw new IllegalArgumentException("TOO_MANY_CANDIDATES");
        Set<String> ids = new HashSet<>();
        CapabilityRelationGraph graph = new CapabilityRelationGraph();
        for (int index = 0; index < candidates.size(); index++) {
            CapabilityCandidate candidate = candidates.get(index);
            String expectedId = "candidate-%03d".formatted(index + 1);
            if (!candidate.candidateId().equals(expectedId) || !ids.add(candidate.candidateId())
                    || candidate.matchType() == MatchType.UNSATISFIED
                    || graph.relation(candidate.requestedCapability(), candidate.providedCapability()) != candidate.matchType()
                    || candidate.matchScore() < 0 || candidate.matchScore() > 100
                    || candidate.riskScore() < 0 || candidate.riskScore() > 100
                    || candidate.researchConfidence() < 0 || candidate.researchConfidence() > 1
                    || candidate.featureConfidence() < 0 || candidate.featureConfidence() > 1) {
                throw new IllegalArgumentException("INVALID_CATALOG");
            }
        }
    }

    public static CapabilityCatalog create(List<CapabilityMatchSet> sets, List<CapabilityCandidate> candidates,
                                           String knowledgeState, String knowledgeDigest, String registryDigest) {
        return new CapabilityCatalog(sets, candidates, knowledgeState, knowledgeDigest, registryDigest,
                hash(candidates, knowledgeDigest, registryDigest));
    }

    @Nullable
    public CapabilityCandidate find(String id) {
        return candidates.stream().filter(candidate -> candidate.candidateId().equals(id)).findFirst().orElse(null);
    }

    public Map<WishCapability, MatchType> qualityByCapability() {
        Map<WishCapability, MatchType> result = new LinkedHashMap<>();
        matchSets.forEach(set -> result.put(set.capability(), set.quality()));
        return Map.copyOf(result);
    }

    private static String hash(List<CapabilityCandidate> candidates, String knowledgeDigest, String registryDigest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(knowledgeDigest.getBytes(StandardCharsets.UTF_8));
            digest.update(registryDigest.getBytes(StandardCharsets.UTF_8));
            for (CapabilityCandidate candidate : candidates) {
                digest.update(candidate.candidateId().getBytes(StandardCharsets.UTF_8));
                digest.update(candidate.requestedCapability().name().getBytes(StandardCharsets.UTF_8));
                digest.update(candidate.sourceModId().getBytes(StandardCharsets.UTF_8));
                if (candidate.registryResource() != null) {
                    digest.update(candidate.registryResource().id().getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
