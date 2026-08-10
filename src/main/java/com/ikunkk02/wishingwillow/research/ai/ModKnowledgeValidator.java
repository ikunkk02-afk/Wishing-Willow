package com.ikunkk02.wishingwillow.research.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.ModCategory;
import com.ikunkk02.wishingwillow.research.ModFeature;
import com.ikunkk02.wishingwillow.research.ModKnowledge;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.ResearchSource;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModKnowledgeValidator {
    private static final Gson GSON = new Gson();
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "mod_id", "name", "version", "category", "summary", "horror_score",
            "wish_relevance", "themes", "features", "available_capabilities", "research_confidence"
    );
    private static final Set<String> FEATURE_FIELDS = Set.of(
            "name", "type", "description", "possible_capabilities", "registry_candidates", "confidence"
    );

    private ModKnowledgeValidator() {
    }

    public static ModKnowledge parseAndValidate(String raw, InstalledModInfo mod, RegistrySnapshot snapshot,
                                                Set<ResearchSource> sources, double identityConfidence) {
        JsonObject root = JsonParser.parseString(stripFence(raw)).getAsJsonObject();
        if (!root.keySet().equals(ROOT_FIELDS) || integer(root, "schema_version", 1, 1) != 1
                || !string(root, "mod_id", 64).equals(mod.modId())) {
            throw invalid();
        }
        String returnedName = string(root, "name", 256);
        if (!string(root, "version", 128).equals(mod.version())) {
            throw invalid();
        }
        ModCategory category = enumValue(root, "category", ModCategory.class);
        String summary = string(root, "summary", 2048);
        int horror = integer(root, "horror_score", 0, 100);
        int relevance = integer(root, "wish_relevance", 0, 100);
        List<String> themes = stringList(root, "themes", 16, 64);
        Set<WishCapability> capabilities = capabilitySet(root.getAsJsonArray("available_capabilities"), 32);

        JsonArray featureArray = root.getAsJsonArray("features");
        if (featureArray == null || featureArray.size() > 32) {
            throw invalid();
        }
        List<ModFeature> features = new ArrayList<>();
        int invalidCandidates = 0;
        int verifiedCount = 0;
        for (JsonElement element : featureArray) {
            if (!element.isJsonObject() || !element.getAsJsonObject().keySet().equals(FEATURE_FIELDS)) {
                throw invalid();
            }
            JsonObject feature = element.getAsJsonObject();
            FeatureType type = enumValue(feature, "type", FeatureType.class);
            Set<WishCapability> possible = capabilitySet(feature.getAsJsonArray("possible_capabilities"), 12);
            List<String> candidates = stringList(feature, "registry_candidates", 32, 256);
            List<String> retained = new ArrayList<>();
            List<VerifiedRegistryResource> verified = new ArrayList<>();
            RegistryEntryType registryType = registryType(type);
            for (String candidate : candidates) {
                if (registryType != null && belongsToMod(candidate, mod)
                        && snapshot.contains(registryType, candidate)) {
                    retained.add(candidate);
                    verified.add(new VerifiedRegistryResource(registryType, candidate));
                    verifiedCount++;
                } else {
                    invalidCandidates++;
                }
            }
            features.add(new ModFeature(
                    string(feature, "name", 128), type, string(feature, "description", 1024),
                    List.copyOf(possible), retained, verified, decimal(feature, "confidence")
            ));
        }
        double aiConfidence = decimal(root, "research_confidence");
        double confidence = Math.max(0.0, Math.min(aiConfidence, Math.max(0.45, identityConfidence))
                - Math.min(0.5, invalidCandidates * 0.05));
        KnowledgeLevel level = verifiedCount > 0 ? KnowledgeLevel.VERIFIED : KnowledgeLevel.UNDERSTOOD;
        Set<ResearchSource> finalSources = new LinkedHashSet<>(sources);
        finalSources.add(ResearchSource.LOCAL_REGISTRY);
        return new ModKnowledge(1, mod.modId(), returnedName, mod.version(), category,
                summary, horror, relevance, themes, features, capabilities, confidence, finalSources,
                level, snapshot.digest());
    }

    public static JsonObject jsonSchema() {
        JsonObject schema = JsonParser.parseString("""
                {"type":"object","additionalProperties":false,
                 "required":["schema_version","mod_id","name","version","category","summary","horror_score","wish_relevance","themes","features","available_capabilities","research_confidence"],
                 "properties":{
                   "schema_version":{"type":"integer","const":1},
                   "mod_id":{"type":"string","minLength":1,"maxLength":64},
                   "name":{"type":"string","minLength":1,"maxLength":256},
                   "version":{"type":"string","minLength":1,"maxLength":128},
                   "category":{"type":"string"},
                   "summary":{"type":"string","minLength":1,"maxLength":2048},
                   "horror_score":{"type":"integer","minimum":0,"maximum":100},
                   "wish_relevance":{"type":"integer","minimum":0,"maximum":100},
                   "themes":{"type":"array","maxItems":16,"uniqueItems":true,"items":{"type":"string","maxLength":64}},
                   "features":{"type":"array","maxItems":32,"items":{"type":"object","additionalProperties":false,
                     "required":["name","type","description","possible_capabilities","registry_candidates","confidence"],
                     "properties":{"name":{"type":"string","maxLength":128},"type":{"type":"string"},
                       "description":{"type":"string","maxLength":1024},
                       "possible_capabilities":{"type":"array","maxItems":12,"uniqueItems":true,"items":{"type":"string"}},
                       "registry_candidates":{"type":"array","maxItems":32,"uniqueItems":true,"items":{"type":"string","maxLength":256}},
                       "confidence":{"type":"number","minimum":0,"maximum":1}}}},
                   "available_capabilities":{"type":"array","maxItems":32,"uniqueItems":true,"items":{"type":"string"}},
                   "research_confidence":{"type":"number","minimum":0,"maximum":1}}}
                """).getAsJsonObject();
        enumSchema(schema.getAsJsonObject("properties").getAsJsonObject("category"), ModCategory.values());
        JsonObject featureProperties = schema.getAsJsonObject("properties").getAsJsonObject("features")
                .getAsJsonObject("items").getAsJsonObject("properties");
        enumSchema(featureProperties.getAsJsonObject("type"), FeatureType.values());
        JsonArray capabilityValues = enumArray(WishCapability.values());
        featureProperties.getAsJsonObject("possible_capabilities").getAsJsonObject("items")
                .add("enum", capabilityValues.deepCopy());
        schema.getAsJsonObject("properties").getAsJsonObject("available_capabilities")
                .getAsJsonObject("items").add("enum", capabilityValues);
        return schema;
    }

    private static boolean belongsToMod(String id, InstalledModInfo mod) {
        int colon = id.indexOf(':');
        if (colon < 1) {
            return false;
        }
        String namespace = id.substring(0, colon);
        return namespace.equals(mod.modId()) || namespace.equals(mod.namespace());
    }

    private static RegistryEntryType registryType(FeatureType type) {
        return switch (type) {
            case ENTITY -> RegistryEntryType.ENTITY;
            case ITEM -> RegistryEntryType.ITEM;
            case BLOCK -> RegistryEntryType.BLOCK;
            case EFFECT -> RegistryEntryType.EFFECT;
            case DIMENSION -> RegistryEntryType.DIMENSION;
            case STRUCTURE -> RegistryEntryType.STRUCTURE;
            case SOUND -> RegistryEntryType.SOUND;
            default -> null;
        };
    }

    private static Set<WishCapability> capabilitySet(JsonArray array, int max) {
        if (array == null || array.size() > max) {
            throw invalid();
        }
        Set<WishCapability> result = EnumSet.noneOf(WishCapability.class);
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid();
            }
            try {
                result.add(WishCapability.valueOf(element.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // Reject the individual unsupported capability without invalidating useful knowledge.
            }
        }
        return result;
    }

    private static List<String> stringList(JsonObject object, String name, int maxItems, int maxLength) {
        JsonArray array = object.getAsJsonArray(name);
        if (array == null || array.size() > maxItems) {
            throw invalid();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid();
            }
            String value = element.getAsString().strip();
            if (value.isEmpty() || value.length() > maxLength || !seen.add(value)) {
                throw invalid();
            }
            result.add(value);
        }
        return result;
    }

    private static String string(JsonObject object, String name, int maxLength) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid();
        }
        String value = element.getAsString().strip();
        if (value.isEmpty() || value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static int integer(JsonObject object, String name, int min, int max) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid();
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || value < min || value > max) {
            throw invalid();
        }
        return (int) value;
    }

    private static double decimal(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid();
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw invalid();
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String name, Class<E> type) {
        try {
            return Enum.valueOf(type, string(object, name, 64));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static void enumSchema(JsonObject target, Enum<?>[] values) {
        target.add("enum", enumArray(values));
    }

    private static JsonArray enumArray(Enum<?>[] values) {
        JsonArray array = new JsonArray();
        for (Enum<?> value : values) {
            array.add(value.name());
        }
        return array;
    }

    private static String stripFence(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (!value.startsWith("```")) {
            return value;
        }
        int newline = value.indexOf('\n');
        if (newline < 0 || !value.endsWith("```")) {
            throw invalid();
        }
        return value.substring(newline + 1, value.length() - 3).strip();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("INVALID_MOD_KNOWLEDGE");
    }
}
