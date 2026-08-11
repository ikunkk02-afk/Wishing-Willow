package com.ikunkk02.wishingwillow.execution;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Persists positive villager relations and reapplies them when relevant villagers are loaded later. */
public final class WishPersistentSocialRules {
    private static final String KEY = "WishingWillowVillagerAffinity";
    private static boolean registered;
    private WishPersistentSocialRules() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(WishPersistentSocialRules::onJoin);
    }

    public static int grant(ServerPlayer player, int delta) {
        int value = Math.max(1, Math.min(100, delta));
        player.getPersistentData().putInt(KEY, Math.max(value, player.getPersistentData().getInt(KEY)));
        int affected = 0;
        for (var entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof Villager villager) { apply(villager, player, value); affected++; }
        }
        return affected;
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            int value = player.getPersistentData().getInt(KEY);
            if (value > 0) apply(villager, player, value);
        }
    }

    private static void apply(Villager villager, ServerPlayer player, int value) {
        villager.getGossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, value);
        villager.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, value);
    }
}
