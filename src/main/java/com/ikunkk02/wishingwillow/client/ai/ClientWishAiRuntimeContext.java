package com.ikunkk02.wishingwillow.client.ai;

import com.ikunkk02.wishingwillow.ai.prompt.WishAiContextCache;
import com.ikunkk02.wishingwillow.ai.prompt.WishAiContextSnapshot;
import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowRuntimeContext;
import com.ikunkk02.wishingwillow.research.ModResearchManager;
import net.minecraft.client.Minecraft;

/** Builds dynamic client world context on the render thread before an AI request starts. */
public final class ClientWishAiRuntimeContext {
    private ClientWishAiRuntimeContext() { }

    public static WishingWillowRuntimeContext capture() {
        WishAiContextSnapshot cached = WishAiContextCache.initialize();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return new WishingWillowRuntimeContext(cached.minecraftVersion(), cached.loader(), "unknown",
                    "unknown", "unknown", "unknown", "unknown", cached.installedMods(),
                    "registryDigest=unavailable", true, "server policy and validation remain authoritative");
        }
        var player = minecraft.player;
        var pos = player.blockPosition();
        String registryDigest;
        try { registryDigest = ModResearchManager.getInstance().registrySnapshot().digest(); }
        catch (RuntimeException ignored) { registryDigest = "unavailable"; }
        return new WishingWillowRuntimeContext(cached.minecraftVersion(), cached.loader(),
                minecraft.level.dimension().location().toString(), player.getUUID().toString(),
                pos.getX() + "," + pos.getY() + "," + pos.getZ(), "unknown",
                minecraft.level.getDifficulty().getKey(), cached.installedMods(),
                "registryDigest=" + registryDigest, true,
                "WishProgramValidator, WishActionPolicy, server policy, permissions, entity caps and world limits");
    }
}