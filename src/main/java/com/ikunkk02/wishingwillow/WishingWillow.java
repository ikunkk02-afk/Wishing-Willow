package com.ikunkk02.wishingwillow;

import com.ikunkk02.wishingwillow.event.CommonModEvents;
import com.ikunkk02.wishingwillow.event.VillagerTradeEvents;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.registry.ModItems;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib.GeckoLib;

@Mod(WishingWillow.MOD_ID)
public final class WishingWillow {
    public static final String MOD_ID = "wishing_willow";

    public WishingWillow(FMLJavaModLoadingContext context) {
        GeckoLib.initialize();
        IEventBus modEventBus = context.getModEventBus();

        ModItems.register(modEventBus);
        modEventBus.addListener(CommonModEvents::addCreativeTabItems);
        modEventBus.addListener(this::commonSetup);
        VillagerTradeEvents.register();
        WishManager.register();
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.ikunkk02.wishingwillow.client.ClientSetup.register(context)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworking::register);
    }
}
