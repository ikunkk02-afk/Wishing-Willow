package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.ai.WishTone;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeBaseSnapshot;
import com.ikunkk02.wishingwillow.research.KnowledgeEntry;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.ModFeature;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.execution.WishSafetyPolicy;
import com.ikunkk02.wishingwillow.execution.WishActionPolicy;
import com.ikunkk02.wishingwillow.WishingWillow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapabilityMatcher {
    public static final int MAX_PER_CAPABILITY = 5;
    private final CapabilityRelationGraph graph;
    private final VanillaCapabilityProvider vanilla;

    public CapabilityMatcher() {
        this(new CapabilityRelationGraph(), new VanillaCapabilityProvider());
    }

    public CapabilityMatcher(CapabilityRelationGraph graph, VanillaCapabilityProvider vanilla) {
        this.graph = graph;
        this.vanilla = vanilla;
    }

    public CapabilityCatalog match(String originalWish, WishInterpretation interpretation,
                                   KnowledgeBaseSnapshot knowledge, RegistrySnapshot registry) {
        return match(originalWish, interpretation, knowledge, registry,
                ExecutionSettingsSnapshot.permissive());
    }

    public CapabilityCatalog match(String originalWish, WishInterpretation interpretation,
                                   KnowledgeBaseSnapshot knowledge, RegistrySnapshot registry,
                                   ExecutionSettingsSnapshot settings) {
        Map<WishCapability, List<CapabilityCandidate>> ranked = new LinkedHashMap<>();
        String relevanceText = originalWish + " " + interpretation.literalGoal() + " " + interpretation.twistedOutcome();
        for (WishCapability requested : interpretation.requiredCapabilities()) {
            List<CapabilityCandidate> candidates = new ArrayList<>();
            candidates.addAll(vanilla.candidates(requested, relevanceText, registry, graph, interpretation.severity()));
            for (KnowledgeEntry entry : knowledge.entries()) {
                if (entry.knowledge() == null || entry.knowledgeLevel() == KnowledgeLevel.UNKNOWN) continue;
                for (ModFeature feature : entry.knowledge().features()) {
                    for (WishCapability provided : feature.possibleCapabilities()) {
                        MatchType relation = graph.relation(requested, provided);
                        if (relation == MatchType.UNSATISFIED) continue;
                        if (!feature.verifiedRegistryResources().isEmpty()) {
                            for (VerifiedRegistryResource resource : feature.verifiedRegistryResources()) {
                                if (registry.contains(resource.type(), resource.id())) {
                                    candidates.add(candidate(entry, feature, requested, provided, relation,
                                            resource, interpretation));
                                }
                            }
                        } else if (entry.knowledgeLevel() == KnowledgeLevel.VERIFIED && nonRegistry(feature.type())) {
                            candidates.add(candidate(entry, feature, requested, provided, relation,
                                    null, interpretation));
                        }
                    }
                }
            }
            List<CapabilityCandidate> top = candidates.stream().distinct()
                    .filter(candidate -> executableCandidate(candidate)
                            && WishSafetyPolicy.candidateAllowed(candidate.reference(),
                            interpretation.severity(), settings))
                    .sorted(order()).limit(MAX_PER_CAPABILITY).toList();
            ranked.put(requested, top);
        }

        List<CapabilityCandidate> selected = new ArrayList<>();
        for (int rank = 0; rank < MAX_PER_CAPABILITY && selected.size() < CapabilityCatalog.MAX_CANDIDATES; rank++) {
            for (WishCapability capability : interpretation.requiredCapabilities()) {
                List<CapabilityCandidate> values = ranked.getOrDefault(capability, List.of());
                if (rank < values.size() && selected.size() < CapabilityCatalog.MAX_CANDIDATES) selected.add(values.get(rank));
            }
        }
        Map<CapabilityCandidate, CapabilityCandidate> assigned = new LinkedHashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            assigned.put(selected.get(index), selected.get(index).withCandidateId("candidate-%03d".formatted(index + 1)));
        }
        List<CapabilityCandidate> catalogCandidates = new ArrayList<>(assigned.values());
        List<CapabilityMatchSet> sets = new ArrayList<>();
        for (WishCapability capability : interpretation.requiredCapabilities()) {
            List<CapabilityCandidate> values = ranked.getOrDefault(capability, List.of()).stream()
                    .filter(assigned::containsKey).map(assigned::get).toList();
            MatchType quality = values.isEmpty() ? MatchType.UNSATISFIED : values.get(0).matchType();
            sets.add(new CapabilityMatchSet(capability, quality, values));
        }
        return CapabilityCatalog.create(sets, catalogCandidates, knowledge.state().name(),
                knowledgeDigest(knowledge), registry.digest());
    }

    private static boolean executableCandidate(CapabilityCandidate candidate) {
        if (candidate.registryResource() != null) return true;
        if (WishActionPolicy.isTrustedBuiltin(candidate.reference())) return true;
        return candidate.sourceKind() == CandidateSourceKind.MOD_FEATURE
                && candidate.sourceModId().equals(WishingWillow.MOD_ID)
                && PredefinedWishEventRegistry.contains(candidate.featureName());
    }

    private static CapabilityCandidate candidate(KnowledgeEntry entry, ModFeature feature,
                                                 WishCapability requested, WishCapability provided,
                                                 MatchType relation, VerifiedRegistryResource resource,
                                                 WishInterpretation interpretation) {
        int risk = risk(provided);
        int score = score(relation, entry.knowledgeLevel(), resource != null,
                entry.knowledge().researchConfidence(), entry.knowledge().wishRelevance(),
                toneFit(interpretation.tone(), entry.knowledge().horrorScore()), interpretation.severity(), risk);
        return new CapabilityCandidate("", requested, provided, relation, CandidateSourceKind.MOD_FEATURE,
                entry.installed().modId(), entry.installed().displayName(), entry.installed().version(),
                feature.name(), feature.type(), resource, feature.description(), entry.knowledgeLevel(),
                entry.knowledge().researchConfidence(), feature.confidence(), entry.knowledge().horrorScore(),
                entry.knowledge().wishRelevance(), risk, score);
    }

    public static int score(MatchType relation, KnowledgeLevel level, boolean verifiedResource,
                            double researchConfidence, int wishRelevance, int toneFit,
                            int severity, int risk) {
        int relationScore = switch (relation) {
            case EXACT -> 40;
            case COMPATIBLE -> 28;
            case APPROXIMATE -> 16;
            case UNSATISFIED -> 0;
        };
        int knowledge = switch (level) {
            case VERIFIED -> 20;
            case UNDERSTOOD -> 13;
            case IDENTIFIED -> 5;
            case UNKNOWN -> 0;
        };
        int registry = verifiedResource ? 15 : level == KnowledgeLevel.VERIFIED ? 12 : 0;
        int research = (int) Math.round(clamp(researchConfidence, 0, 1) * 10);
        int relevance = (int) Math.round(clamp(wishRelevance, 0, 100) * 0.08);
        int severityFit = (int) Math.round(3 * (1.0 - Math.abs(severity - risk) / 100.0));
        return (int) clamp(relationScore + knowledge + registry + research + relevance
                + clamp(toneFit, 0, 4) + severityFit, 0, 100);
    }

    public static int risk(WishCapability capability) {
        return switch (capability) {
            case EXPLOSION -> 90;
            case POWERFUL_ENEMY -> 85;
            case HOSTILE_ENTITY, DAMAGE, LIGHTNING -> 70;
            case STALKING_ENTITY, PERSISTENT_FOLLOWER, DARKNESS -> 60;
            case BLOCK_CHANGE, WORLD_EVENT, POWER_DEBUFF -> 55;
            case TELEPORT, DIMENSION_TRAVEL, SPACE_TRAVEL, SPACECRAFT -> 45;
            case POWER_BUFF, STRONG_WEAPON, PLAYER_ATTRIBUTE -> 40;
            default -> 25;
        };
    }

    private static int toneFit(WishTone tone, int horrorScore) {
        return tone == WishTone.HORROR || tone == WishTone.DARK
                ? (int) Math.round(clamp(horrorScore, 0, 100) * 0.04) : 2;
    }

    private static boolean nonRegistry(FeatureType type) {
        return type == FeatureType.WEATHER || type == FeatureType.PLAYER_SYSTEM
                || type == FeatureType.WORLD_SYSTEM;
    }

    private static Comparator<CapabilityCandidate> order() {
        return Comparator.comparingInt(CapabilityCandidate::matchScore).reversed()
                .thenComparing(candidate -> candidate.knowledgeLevel() == KnowledgeLevel.VERIFIED ? 0 : 1)
                .thenComparing(CapabilityCandidate::sourceModId)
                .thenComparing(CapabilityCandidate::featureName)
                .thenComparing(candidate -> candidate.registryResource() == null ? "" : candidate.registryResource().id());
    }

    private static String knowledgeDigest(KnowledgeBaseSnapshot snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(snapshot.state().name().getBytes(StandardCharsets.UTF_8));
            for (KnowledgeEntry entry : snapshot.entries()) {
                digest.update(entry.installed().modId().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.fingerprint().cacheKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.registryDigest().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
