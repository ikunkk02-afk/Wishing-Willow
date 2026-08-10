package com.ikunkk02.wishingwillow.client.network;

import com.ikunkk02.wishingwillow.client.ai.ClientAiWishCoordinator;
import com.ikunkk02.wishingwillow.client.ai.ClientWishPlanningCoordinator;
import com.ikunkk02.wishingwillow.client.animation.ClientWishSequence;
import com.ikunkk02.wishingwillow.ai.AiConfigManager;
import com.ikunkk02.wishingwillow.client.gui.AiNotConfiguredScreen;
import com.ikunkk02.wishingwillow.client.gui.WishScreen;
import com.ikunkk02.wishingwillow.network.packet.OpenWishScreenPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishPlanningRequestPacket;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void openWishScreen(OpenWishScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !ClientWishSequence.isActive()) {
            if (AiConfigManager.getInstance().get().isConfigured()) {
                minecraft.setScreen(new WishScreen(packet.hand()));
            } else {
                minecraft.setScreen(new AiNotConfiguredScreen(minecraft.screen));
            }
        }
    }

    public static void startWish(WishStartedPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
        ClientAiWishCoordinator.register(packet);
        ClientWishSequence.start(packet);
    }

    public static void updateWishState(WishStatePacket packet) {
        ClientAiWishCoordinator.updateState(packet);
        ClientWishSequence.updateState(packet);
    }

    public static void startPlanning(WishPlanningRequestPacket packet) {
        ClientWishPlanningCoordinator.start(packet);
    }
}
