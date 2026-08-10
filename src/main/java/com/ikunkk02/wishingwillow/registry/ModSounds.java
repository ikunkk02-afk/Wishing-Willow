package com.ikunkk02.wishingwillow.registry;

import com.ikunkk02.wishingwillow.WishingWillow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WishingWillow.MOD_ID);

    public static final RegistryObject<SoundEvent> UNBOXING_MUSIC = register("unboxing_music");
    public static final RegistryObject<SoundEvent> PACKAGE_RUSTLE = register("package_rustle");
    public static final RegistryObject<SoundEvent> PACKAGE_FLAP = register("package_flap");
    public static final RegistryObject<SoundEvent> WILLOW_SLIDE = register("willow_slide");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id = new ResourceLocation(WishingWillow.MOD_ID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
