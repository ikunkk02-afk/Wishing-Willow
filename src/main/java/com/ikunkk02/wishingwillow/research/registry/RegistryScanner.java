package com.ikunkk02.wishingwillow.research.registry;

import com.ikunkk02.wishingwillow.research.InstalledModInfo;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RegistryScanner {
    private RegistryScanner() {
    }

    /** Must be invoked on the Minecraft client thread. It only copies namespaced IDs. */
    public static RegistrySnapshot scan(LocalPlayer player, List<InstalledModInfo> mods) {
        EnumMap<RegistryEntryType, List<String>> entries = new EnumMap<>(RegistryEntryType.class);
        entries.put(RegistryEntryType.ITEM, strings(ForgeRegistries.ITEMS.getKeys()));
        entries.put(RegistryEntryType.BLOCK, strings(ForgeRegistries.BLOCKS.getKeys()));
        entries.put(RegistryEntryType.ENTITY, strings(ForgeRegistries.ENTITY_TYPES.getKeys()));
        entries.put(RegistryEntryType.EFFECT, strings(ForgeRegistries.MOB_EFFECTS.getKeys()));
        entries.put(RegistryEntryType.SOUND, strings(ForgeRegistries.SOUND_EVENTS.getKeys()));
        entries.put(RegistryEntryType.PARTICLE, strings(ForgeRegistries.PARTICLE_TYPES.getKeys()));
        entries.put(RegistryEntryType.BIOME, dynamic(player, Registries.BIOME));
        entries.put(RegistryEntryType.STRUCTURE, dynamic(player, Registries.STRUCTURE));
        entries.put(RegistryEntryType.DIMENSION, dynamic(player, Registries.LEVEL_STEM));

        Map<String, String> owners = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (InstalledModInfo mod : mods) {
            registerOwner(owners, ambiguous, mod.modId(), mod.modId());
            registerOwner(owners, ambiguous, mod.namespace(), mod.modId());
        }
        return new RegistrySnapshot(entries, owners, ambiguous);
    }

    private static void registerOwner(Map<String, String> owners, Set<String> ambiguous,
                                      String namespace, String modId) {
        if (namespace == null || namespace.isBlank() || ambiguous.contains(namespace)) {
            return;
        }
        String previous = owners.putIfAbsent(namespace, modId);
        if (previous != null && !previous.equals(modId)) {
            owners.remove(namespace);
            ambiguous.add(namespace);
        }
    }

    private static List<String> strings(Set<ResourceLocation> keys) {
        return keys.stream().map(ResourceLocation::toString).sorted().toList();
    }

    private static <T> List<String> dynamic(LocalPlayer player, ResourceKey<Registry<T>> key) {
        return player.level().registryAccess().registry(key)
                .map(registry -> registry.keySet().stream().map(ResourceLocation::toString).sorted().toList())
                .orElse(List.of());
    }
}
