package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.program.WishProgramResourceResolver;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
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
    public int maxStackSize(RegistryEntryType type, String id) {
        if (type != RegistryEntryType.ITEM || id == null || id.isBlank()) return 1;
        String exact = id.contains(":") ? id : "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(exact);
        var item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        return item == null ? 1 : Math.max(1, item.getMaxStackSize());
    }

    @Override
    @Nullable
    public String resolveEnchantment(String id) {
        ResourceLocation location = location(id);
        return location != null && ForgeRegistries.ENCHANTMENTS.containsKey(location) ? location.toString() : null;
    }

    @Override
    public int enchantmentMaxLevel(String id) {
        var enchantment = ForgeRegistries.ENCHANTMENTS.getValue(location(id));
        return enchantment == null ? 0 : enchantment.getMaxLevel();
    }

    @Override
    public boolean enchantmentCanApply(String enchantmentId, String itemId) {
        var enchantment = ForgeRegistries.ENCHANTMENTS.getValue(location(enchantmentId));
        var item = ForgeRegistries.ITEMS.getValue(location(itemId));
        return enchantment != null && item != null && enchantment.canEnchant(new ItemStack(item));
    }

    @Override
    public boolean enchantmentsCompatible(String firstId, String secondId) {
        var first = ForgeRegistries.ENCHANTMENTS.getValue(location(firstId));
        var second = ForgeRegistries.ENCHANTMENTS.getValue(location(secondId));
        return first != null && second != null && first.isCompatibleWith(second);
    }

    @Nullable
    private static ResourceLocation location(String id) {
        if (id == null || id.isBlank()) return null;
        return ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
    }

    @Override
    public boolean containsPredefinedEvent(String event) {
        return event != null && PredefinedWishEventRegistry.contains(event);
    }
}
