package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VanillaCapabilityProvider {
    private static final Map<WishCapability, List<ResourceDescriptor>> RESOURCES = resources();
    private static final Set<WishCapability> BUILTINS = Set.of(
            WishCapability.CHANGE_TIME, WishCapability.CHANGE_WEATHER, WishCapability.TELEPORT,
            WishCapability.EXPLOSION, WishCapability.LIGHTNING, WishCapability.PLAYER_ATTRIBUTE,
            WishCapability.REPUTATION, WishCapability.MOB_BEHAVIOR, WishCapability.HEALING,
            WishCapability.DAMAGE, WishCapability.INVENTORY_CHANGE, WishCapability.BLOCK_CHANGE
    );

    public List<CapabilityCandidate> candidates(WishCapability requested, String wishText,
                                                RegistrySnapshot snapshot, CapabilityRelationGraph graph,
                                                int severity) {
        List<CapabilityCandidate> result = new ArrayList<>();
        String lowerWish = wishText.toLowerCase(Locale.ROOT);
        for (Map.Entry<WishCapability, List<ResourceDescriptor>> entry : RESOURCES.entrySet()) {
            MatchType relation = graph.relation(requested, entry.getKey());
            if (relation == MatchType.UNSATISFIED) continue;
            for (ResourceDescriptor descriptor : entry.getValue()) {
                if (!snapshot.contains(descriptor.type, descriptor.id)) continue;
                int relevance = descriptor.aliases.stream().anyMatch(lowerWish::contains) ? 100 : 65;
                int risk = CapabilityMatcher.risk(entry.getKey());
                int score = CapabilityMatcher.score(relation, KnowledgeLevel.VERIFIED, true,
                        1.0, relevance, 0, severity, risk);
                result.add(new CapabilityCandidate("", requested, entry.getKey(), relation,
                        CandidateSourceKind.VANILLA_REGISTRY, "minecraft", "Minecraft", "1.20.1",
                        descriptor.name, featureType(descriptor.type),
                        new VerifiedRegistryResource(descriptor.type, descriptor.id), descriptor.description,
                        KnowledgeLevel.VERIFIED, 1.0, 1.0, 0, relevance, risk, score));
            }
        }
        for (WishCapability builtin : BUILTINS) {
            MatchType relation = graph.relation(requested, builtin);
            if (relation == MatchType.UNSATISFIED) continue;
            int risk = CapabilityMatcher.risk(builtin);
            result.add(new CapabilityCandidate("", requested, builtin, relation,
                    CandidateSourceKind.VANILLA_BUILTIN, "minecraft", "Minecraft", "1.20.1",
                    builtin.name(), builtinType(builtin), null,
                    "Verified vanilla capability implemented by a future server action handler.",
                    KnowledgeLevel.VERIFIED, 1.0, 1.0, 0, 70, risk,
                    CapabilityMatcher.score(relation, KnowledgeLevel.VERIFIED, true, 1.0, 70,
                            0, severity, risk)));
        }
        return result;
    }

    private static Map<WishCapability, List<ResourceDescriptor>> resources() {
        Map<WishCapability, List<ResourceDescriptor>> map = new EnumMap<>(WishCapability.class);
        map.put(WishCapability.GIVE_ITEM, List.of(
                item("minecraft:diamond", "Diamond", "Verified vanilla diamond item", "diamond", "diamonds", "钻石"),
                item("minecraft:gold_ingot", "Gold Ingot", "Verified vanilla gold item", "gold", "金锭"),
                item("minecraft:bread", "Bread", "Verified vanilla food item", "bread", "面包")));
        map.put(WishCapability.STRONG_WEAPON, List.of(
                item("minecraft:netherite_sword", "Netherite Sword", "Powerful vanilla melee weapon", "sword", "剑", "武器"),
                item("minecraft:diamond_sword", "Diamond Sword", "Strong vanilla melee weapon", "diamond sword", "钻石剑")));
        map.put(WishCapability.FRIENDLY_ENTITY, List.of(
                entity("minecraft:wolf", "Wolf", "A tameable vanilla companion", "wolf", "狗", "狼"),
                entity("minecraft:villager", "Villager", "A passive vanilla resident", "villager", "村民")));
        map.put(WishCapability.HOSTILE_ENTITY, List.of(
                entity("minecraft:zombie", "Zombie", "A common hostile vanilla creature", "zombie", "僵尸")));
        map.put(WishCapability.POWERFUL_ENEMY, List.of(
                entity("minecraft:wither", "Wither", "A destructive vanilla boss", "wither", "凋灵")));
        map.put(WishCapability.DARKNESS, List.of(
                effect("minecraft:darkness", "Darkness", "The verified vanilla darkness effect", "darkness", "黑暗")));
        map.put(WishCapability.POWER_BUFF, List.of(
                effect("minecraft:strength", "Strength", "The verified vanilla strength effect", "strength", "力量")));
        map.put(WishCapability.SOUND_EVENT, List.of(
                sound("minecraft:ambient.cave", "Cave Ambience", "A verified vanilla cave ambience sound", "sound", "声音", "耳语")));
        map.put(WishCapability.VISUAL_EVENT, List.of(
                particle("minecraft:smoke", "Smoke", "A verified vanilla smoke particle", "smoke", "烟")));
        map.put(WishCapability.DIMENSION_TRAVEL, List.of(
                dimension("minecraft:the_nether", "The Nether", "A verified vanilla dimension", "nether", "下界"),
                dimension("minecraft:the_end", "The End", "A verified vanilla dimension", "end", "末地")));
        map.put(WishCapability.BLOCK_CHANGE, List.of(
                block("minecraft:stone", "Stone", "A verified vanilla block", "stone", "石头")));
        return Map.copyOf(map);
    }

    private static ResourceDescriptor item(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.ITEM, id, name, description, aliases);
    }
    private static ResourceDescriptor entity(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.ENTITY, id, name, description, aliases);
    }
    private static ResourceDescriptor effect(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.EFFECT, id, name, description, aliases);
    }
    private static ResourceDescriptor sound(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.SOUND, id, name, description, aliases);
    }
    private static ResourceDescriptor particle(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.PARTICLE, id, name, description, aliases);
    }
    private static ResourceDescriptor dimension(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.DIMENSION, id, name, description, aliases);
    }
    private static ResourceDescriptor block(String id, String name, String description, String... aliases) {
        return descriptor(RegistryEntryType.BLOCK, id, name, description, aliases);
    }
    private static ResourceDescriptor descriptor(RegistryEntryType type, String id, String name,
                                                 String description, String... aliases) {
        return new ResourceDescriptor(type, id, name, description,
                java.util.Arrays.stream(aliases).map(value -> value.toLowerCase(Locale.ROOT)).toList());
    }

    private static FeatureType featureType(RegistryEntryType type) {
        return switch (type) {
            case ITEM -> FeatureType.ITEM;
            case BLOCK -> FeatureType.BLOCK;
            case ENTITY -> FeatureType.ENTITY;
            case EFFECT -> FeatureType.EFFECT;
            case SOUND -> FeatureType.SOUND;
            case DIMENSION -> FeatureType.DIMENSION;
            case STRUCTURE -> FeatureType.STRUCTURE;
            default -> FeatureType.WORLD_SYSTEM;
        };
    }

    private static FeatureType builtinType(WishCapability capability) {
        return switch (capability) {
            case PLAYER_ATTRIBUTE, HEALING, DAMAGE, INVENTORY_CHANGE -> FeatureType.PLAYER_SYSTEM;
            case MOB_BEHAVIOR, REPUTATION -> FeatureType.ENTITY;
            default -> FeatureType.WORLD_SYSTEM;
        };
    }

    private record ResourceDescriptor(RegistryEntryType type, String id, String name,
                                      String description, List<String> aliases) { }
}
