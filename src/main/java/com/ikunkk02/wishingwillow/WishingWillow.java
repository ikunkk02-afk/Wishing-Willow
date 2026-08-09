package com.ikunkk02.wishingwillow;

import com.ikunkk02.wishingwillow.event.CommonModEvents;
import com.ikunkk02.wishingwillow.event.VillagerTradeEvents;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WishingWillow.MOD_ID)
public final class WishingWillow {
    public static final String MOD_ID = "wishing_willow";

    public WishingWillow(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModItems.register(modEventBus);
        modEventBus.addListener(CommonModEvents::addCreativeTabItems);
        VillagerTradeEvents.register();
    }
}
