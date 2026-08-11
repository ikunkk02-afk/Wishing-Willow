package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.program.WishProgramResourceResolver;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** Live-registry resolver used by the server-side WishProgram validator. */
public final class ForgeWishProgramResourceResolver implements WishProgramResourceResolver {
    private final MinecraftServer server;

    public ForgeWishProgramResourceResolver(MinecraftServer server) {
        this.server = server;
    }

    @Override
    @Nullable
    public String resolve(RegistryEntryType type, String id) {
        if (id == null || id.isBlank()) return null;
        String exact = id.contains(":") ? id : "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(exact);
        if (location == null) return null;
        boolean found = switch (type) {
            case ITEM -> ForgeRegistries.ITEMS.containsKey(location);
            case BLOCK -> ForgeRegistries.BLOCKS.containsKey(location);
            case ENTITY -> ForgeRegistries.ENTITY_TYPES.containsKey(location);
            case EFFECT -> ForgeRegistries.MOB_EFFECTS.containsKey(location);
            case SOUND -> ForgeRegistries.SOUND_EVENTS.containsKey(location);
            case PARTICLE -> ForgeRegistries.PARTICLE_TYPES.containsKey(location);
            default -> false;
        };
        return found ? exact : null;
    }

    @Override
    @Nullable
    public String resolveDimension(String id) {
        if (id == null || id.isBlank()) return null;
        String exact = id.contains(":") ? id : "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(exact);
        if (location == null) return null;
        return server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, location))
                ? exact : null;
    }

    @Override
    public boolean containsPredefinedEvent(String event) {
        return event != null && PredefinedWishEventRegistry.contains(event);
    }
}
