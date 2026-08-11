package com.ikunkk02.wishingwillow.planning;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.stream.Collectors;

public record ServerPlanningEnvironment(MinecraftServer server) implements PlanningEnvironment {
    @Override
    public boolean contains(RegistryEntryType type, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return false;
        return switch (type) {
            case ITEM -> ForgeRegistries.ITEMS.containsKey(location);
            case BLOCK -> ForgeRegistries.BLOCKS.containsKey(location);
            case ENTITY -> ForgeRegistries.ENTITY_TYPES.containsKey(location);
            case EFFECT -> ForgeRegistries.MOB_EFFECTS.containsKey(location);
            case SOUND -> ForgeRegistries.SOUND_EVENTS.containsKey(location);
            case PARTICLE -> ForgeRegistries.PARTICLE_TYPES.containsKey(location);
            case BIOME -> dynamic(Registries.BIOME, location);
            case STRUCTURE -> dynamic(Registries.STRUCTURE, location);
            case DIMENSION -> dynamic(Registries.LEVEL_STEM, location);
        };
    }

    @Override
    public boolean modLoaded(String modId, String version) {
        if (modId.equals("minecraft")) return version.equals("1.20.1");
        if (modId.equals("wishing_willow")) return ModList.get().isLoaded(modId);
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString().equals(version))
                .orElse(false);
    }

    @Override
    public boolean modPresent(String modId, String storedVersion) {
        return modId.equals("minecraft") || ModList.get().isLoaded(modId);
    }

    @Override
    public Set<String> beneficialStatusEffectIds() {
        return ForgeRegistries.MOB_EFFECTS.getEntries().stream()
                .filter(entry -> entry.getValue().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL)
                .map(entry -> entry.getKey().location().toString())
                .collect(Collectors.toUnmodifiableSet());
    }

    private <T> boolean dynamic(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key,
                                ResourceLocation location) {
        return server.registryAccess().registry(key).map(registry -> registry.containsKey(location)).orElse(false);
    }
}
