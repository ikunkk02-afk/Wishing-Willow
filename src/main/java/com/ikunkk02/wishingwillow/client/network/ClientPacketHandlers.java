package com.ikunkk02.wishingwillow.client.network;

import com.ikunkk02.wishingwillow.client.animation.ClientWishSequence;
import com.ikunkk02.wishingwillow.client.gui.WishScreen;
import com.ikunkk02.wishingwillow.network.packet.OpenWishScreenPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void openWishScreen(OpenWishScreenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !ClientWishSequence.isActive()) {
            minecraft.setScreen(new WishScreen(packet.hand()));
        }
    }

    public static void startWish(WishStartedPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
        ClientWishSequence.start(packet);
    }

    public static void updateWishState(WishStatePacket packet) {
        ClientWishSequence.updateState(packet);
    }
}
