package com.ikunkk02.wishingwillow;

import com.mojang.logging.LogUtils;
import com.ikunkk02.wishingwillow.event.CommonModEvents;
import com.ikunkk02.wishingwillow.event.VillagerTradeEvents;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.registry.ModItems;
import com.ikunkk02.wishingwillow.registry.ModSounds;
import com.ikunkk02.wishingwillow.unboxing.UnboxingManager;
import com.ikunkk02.wishingwillow.unboxing.UnboxingGameTests;
import com.ikunkk02.wishingwillow.wish.WishManager;
import com.ikunkk02.wishingwillow.execution.WishExecutionConfig;
import com.ikunkk02.wishingwillow.execution.WishExecutionManager;
import com.ikunkk02.wishingwillow.execution.WishExecutionGameTests;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import software.bernie.geckolib.GeckoLib;
import org.slf4j.Logger;

@Mod(WishingWillow.MOD_ID)
public final class WishingWillow {
    public static final String MOD_ID = "wishing_willow";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WishingWillow(FMLJavaModLoadingContext context) {
        GeckoLib.initialize();
        IEventBus modEventBus = context.getModEventBus();

        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        modEventBus.addListener(CommonModEvents::addCreativeTabItems);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(WishExecutionGameTests::register);
        modEventBus.addListener(UnboxingGameTests::register);
        VillagerTradeEvents.register();
        WishManager.register();
        UnboxingManager.register();
        WishExecutionManager.register();
        com.ikunkk02.wishingwillow.wish.WishPipelineConsistencyChecker.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WishExecutionConfig.SPEC,
                "wishing_willow-server.toml");
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.ikunkk02.wishingwillow.client.ClientSetup.register(context)
        );
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworking::register);
    }
}
