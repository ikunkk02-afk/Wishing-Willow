package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.contract.WishConstraintKind;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WishingWillowBuiltinCapabilityProvider {
    public List<CapabilityCandidate> candidates(WishCapability requested, WishInterpretation interpretation,
                                                RegistrySnapshot registry, CapabilityRelationGraph graph,
                                                int severity) {
        List<CapabilityCandidate> result = new ArrayList<>();
        addContractResources(result, requested, interpretation, registry, graph, severity);
        addRegistryBuiltin(result, requested, WishingWillowBuiltinCapability.SPAWN_BASIC_ENTITY,
                RegistryEntryType.ENTITY, "minecraft:wolf", registry, graph, severity);
        addRegistryBuiltin(result, requested, WishingWillowBuiltinCapability.FOLLOW_BEHAVIOR,
                RegistryEntryType.ENTITY, "minecraft:wolf", registry, graph, severity);
        addRegistryBuiltin(result, requested, WishingWillowBuiltinCapability.PLAY_SOUND,
                RegistryEntryType.SOUND, "minecraft:ambient.cave", registry, graph, severity);
        addRegistryBuiltin(result, requested, WishingWillowBuiltinCapability.SPAWN_PARTICLE,
                RegistryEntryType.PARTICLE, "minecraft:smoke", registry, graph, severity);
        addAllPositiveEffects(result, requested, interpretation, graph, severity);

        for (WishingWillowBuiltinCapability builtin : List.of(
                WishingWillowBuiltinCapability.APPLY_PLAYER_STATE,
                WishingWillowBuiltinCapability.MODIFY_ATTRIBUTE,
                WishingWillowBuiltinCapability.TELEPORT_SAFE,
                WishingWillowBuiltinCapability.CHANGE_TIME,
                WishingWillowBuiltinCapability.CHANGE_WEATHER,
                WishingWillowBuiltinCapability.SOCIAL_REPUTATION,
                WishingWillowBuiltinCapability.CREATE_SIMPLE_STRUCTURE)) {
            addBuiltin(result, requested, builtin, graph, severity);
        }

        MatchType eventRelation = graph.relation(requested, WishCapability.WORLD_EVENT);
        if (eventRelation != MatchType.UNSATISFIED) {
            for (String eventId : PredefinedWishEventRegistry.worldEventIds()) {
                int risk = CapabilityMatcher.risk(WishCapability.WORLD_EVENT);
                result.add(new CapabilityCandidate("", requested, WishCapability.WORLD_EVENT, eventRelation,
                        CandidateSourceKind.MOD_FEATURE, WishingWillow.MOD_ID, "Wishing Willow", "1.0.0",
                        eventId, FeatureType.WORLD_SYSTEM, null,
                        "A server-whitelisted Wishing Willow world event.", KnowledgeLevel.VERIFIED,
                        1, 1, eventId.contains("stalker") ? 70 : 45, 80, risk,
                        CapabilityMatcher.score(eventRelation, KnowledgeLevel.VERIFIED, true,
                                1, 80, 2, severity, risk)));
            }
        }
        return result;
    }

    private static void addAllPositiveEffects(List<CapabilityCandidate> result, WishCapability requested,
                                              WishInterpretation interpretation,
                                              CapabilityRelationGraph graph, int severity) {
        if (interpretation.schemaVersion() < 2
                || !"all_positive_status_effects".equals(interpretation.contract()
                .semantic(WishConstraintKind.STATE_METRIC).orElse(""))) return;
        MatchType relation = graph.relation(requested, WishCapability.POWER_BUFF);
        if (relation == MatchType.UNSATISFIED) return;
        int risk = CapabilityMatcher.risk(WishCapability.POWER_BUFF);
        result.add(new CapabilityCandidate("", requested, WishCapability.POWER_BUFF, relation,
                CandidateSourceKind.MOD_FEATURE, WishingWillow.MOD_ID, "Wishing Willow", "1.0.0",
                PredefinedWishEventRegistry.ALL_POSITIVE_EFFECTS, FeatureType.PLAYER_SYSTEM, null,
                "Applies every registered beneficial status effect through a server-whitelisted Forge API event.",
                KnowledgeLevel.VERIFIED, 1, 1, 0, 100, risk,
                CapabilityMatcher.score(relation, KnowledgeLevel.VERIFIED, true,
                        1, 100, 1, severity, risk)));
    }

    private static void addContractResources(List<CapabilityCandidate> result, WishCapability requested,
                                             WishInterpretation interpretation, RegistrySnapshot registry,
                                             CapabilityRelationGraph graph, int severity) {
        if (interpretation.schemaVersion() < 2) return;
        String semantic = interpretation.contract().semantic(WishConstraintKind.RESOURCE_SEMANTIC).orElse("");
        if (semantic.isBlank()) return;
        addMatchingResources(result, requested, semantic, RegistryEntryType.ITEM,
                WishingWillowBuiltinCapability.GIVE_RESOURCE, registry, graph, severity);
        addMatchingResources(result, requested, semantic, RegistryEntryType.BLOCK,
                WishingWillowBuiltinCapability.PLACE_RESOURCE, registry, graph, severity);
    }

    private static void addMatchingResources(List<CapabilityCandidate> result, WishCapability requested,
                                             String semantic, RegistryEntryType type,
                                             WishingWillowBuiltinCapability builtin, RegistrySnapshot registry,
                                             CapabilityRelationGraph graph, int severity) {
        MatchType relation = graph.relation(requested, builtin.providedCapability());
        if (relation == MatchType.UNSATISFIED) return;
        for (String id : registry.entries().getOrDefault(type, List.of())) {
            String path = id.substring(id.indexOf(':') + 1);
            if (!normalize(path).equals(normalize(semantic))) continue;
            result.add(candidate(requested, builtin, relation,
                    new VerifiedRegistryResource(type, id), severity, 100));
        }
    }

    private static void addRegistryBuiltin(List<CapabilityCandidate> result, WishCapability requested,
                                           WishingWillowBuiltinCapability builtin, RegistryEntryType type,
                                           String id, RegistrySnapshot registry,
                                           CapabilityRelationGraph graph, int severity) {
        if (!registry.contains(type, id)) return;
        for (WishCapability provided : builtin.providedCapabilities()) {
            MatchType relation = graph.relation(requested, provided);
            if (relation == MatchType.UNSATISFIED) continue;
            result.add(candidate(requested, provided, builtin, relation,
                    new VerifiedRegistryResource(type, id), severity, 85));
        }
    }

    private static void addBuiltin(List<CapabilityCandidate> result, WishCapability requested,
                                   WishingWillowBuiltinCapability builtin, CapabilityRelationGraph graph,
                                   int severity) {
        for (WishCapability provided : builtin.providedCapabilities()) {
            MatchType relation = graph.relation(requested, provided);
            if (relation == MatchType.UNSATISFIED) continue;
            result.add(candidate(requested, provided, builtin, relation, null, severity, 78));
        }
    }

    private static CapabilityCandidate candidate(WishCapability requested,
                                                 WishingWillowBuiltinCapability builtin,
                                                 MatchType relation, VerifiedRegistryResource resource,
                                                 int severity, int relevance) {
        return candidate(requested, builtin.providedCapability(), builtin, relation, resource, severity, relevance);
    }

    private static CapabilityCandidate candidate(WishCapability requested, WishCapability provided,
                                                 WishingWillowBuiltinCapability builtin,
                                                 MatchType relation, VerifiedRegistryResource resource,
                                                 int severity, int relevance) {
        int risk = CapabilityMatcher.risk(provided);
        return new CapabilityCandidate("", requested, provided, relation,
                CandidateSourceKind.WISHING_WILLOW_BUILTIN, WishingWillow.MOD_ID,
                "Wishing Willow", "1.0.0", builtin.name(), builtin.featureType(), resource,
                "A server-whitelisted Wishing Willow built-in mapped to the existing action registry.",
                KnowledgeLevel.VERIFIED, 1, 1, 0, relevance, risk,
                CapabilityMatcher.score(relation, KnowledgeLevel.VERIFIED, resource != null,
                        1, relevance, 1, severity, risk));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
