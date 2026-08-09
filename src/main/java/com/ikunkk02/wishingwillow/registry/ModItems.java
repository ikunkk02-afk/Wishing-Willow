package com.ikunkk02.wishingwillow.registry;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.item.WishingWillowItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WishingWillow.MOD_ID);

    public static final RegistryObject<Item> WISHING_WILLOW = ITEMS.register(
            "wishing_willow",
            () -> new WishingWillowItem(new Item.Properties())
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
