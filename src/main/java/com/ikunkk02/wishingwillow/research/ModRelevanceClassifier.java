package com.ikunkk02.wishingwillow.research;

import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModRelevanceClassifier {
    private static final Set<String> CORE = Set.of("wishing_willow", "minecraft", "forge");

    public Classification classify(InstalledModInfo mod, RegistrySnapshot snapshot, List<String> remoteCategories) {
        return classify(mod, snapshot, remoteCategories, false);
    }

    public Classification classify(InstalledModInfo mod, RegistrySnapshot snapshot, List<String> remoteCategories,
                                   boolean requiredByAnotherMod) {
        if (CORE.contains(mod.modId())) {
            return new Classification(ModCategory.API, true);
        }
        String text = (mod.displayName() + " " + mod.description() + " "
                + String.join(" ", remoteCategories)).toLowerCase(Locale.ROOT);
        Map<RegistryEntryType, Integer> counts = snapshot.countsForMod(mod.modId());
        int entities = counts.getOrDefault(RegistryEntryType.ENTITY, 0);
        int dimensions = counts.getOrDefault(RegistryEntryType.DIMENSION, 0);
        int structures = counts.getOrDefault(RegistryEntryType.STRUCTURE, 0);

        int contentCount = counts.getOrDefault(RegistryEntryType.ITEM, 0)
                + counts.getOrDefault(RegistryEntryType.BLOCK, 0) + entities + structures
                + counts.getOrDefault(RegistryEntryType.EFFECT, 0)
                + counts.getOrDefault(RegistryEntryType.DIMENSION, 0);
        if (containsAny(text, "library", " api ", "dependency", "multiplatform", "framework",
                "animation engine", "rendering engine", "developer toolkit")
                || (requiredByAnotherMod && contentCount == 0)) {
            return new Classification(text.contains("library") ? ModCategory.LIBRARY : ModCategory.API, true);
        }
        if (containsAny(text, "performance", "optimization", "optimisation", "memory usage", "fps", "culling")) {
            return new Classification(ModCategory.PERFORMANCE, true);
        }
        if (containsAny(text, "horror", "psychological", "jumpscare", "stalker", "scary", "fear", "dweller")
                && !containsAny(text, "remove horror", "removes horror", "disable horror", "disables horror", "without horror")) {
            return new Classification(ModCategory.HORROR, false);
        }
        if (containsAny(text, "technology", "automation", "machine", "energy", "kinetic")) {
            return new Classification(ModCategory.TECHNOLOGY, false);
        }
        if (containsAny(text, "magic", "spell", "mana")) {
            return new Classification(ModCategory.MAGIC, false);
        }
        if (containsAny(text, "combat", "weapon", "battle")) {
            return new Classification(ModCategory.COMBAT, false);
        }
        if (dimensions > 0 || containsAny(text, "dimension", "realm")) {
            return new Classification(ModCategory.DIMENSION, false);
        }
        if (entities > 2 || containsAny(text, "mobs", "creatures", "monster", "entity")) {
            return new Classification(ModCategory.MOBS, false);
        }
        if (structures > 0 || containsAny(text, "worldgen", "world generation", "biome", "structures")) {
            return new Classification(ModCategory.WORLDGEN, false);
        }
        return new Classification(contentCount > 4 ? ModCategory.CONTENT : ModCategory.UNKNOWN, false);
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public record Classification(ModCategory category, boolean ignored) {
    }
}
