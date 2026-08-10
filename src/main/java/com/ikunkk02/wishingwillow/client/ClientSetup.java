package com.ikunkk02.wishingwillow.client;

import com.ikunkk02.wishingwillow.client.gui.AiSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new AiSettingsScreen(parent)
                )
        );
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::registerClientCommands);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("wishingwillow")
                        .then(Commands.literal("ai").executes(context -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.execute(() -> minecraft.setScreen(new AiSettingsScreen(minecraft.screen)));
                            return 1;
                        }))
        );
    }
}
