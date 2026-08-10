package com.ikunkk02.wishingwillow.unboxing;

import com.ikunkk02.wishingwillow.item.PackagedWishingWillowItem;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStatePacket;
import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UnboxingManager {
    private static final Map<UUID, UnboxingSession> ACTIVE = new HashMap<>();

    private UnboxingManager() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onLogout);
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onDeath);
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onDimensionChange);
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(UnboxingManager::onServerStopped);
    }

    public static boolean tryStart(ServerPlayer player, InteractionHand hand) {
        if (ACTIVE.containsKey(player.getUUID()) || !player.isAlive()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.PACKAGED_WISHING_WILLOW.get())) {
            return false;
        }
        long gameTime = player.serverLevel().getGameTime();
        long itemId = GeoItem.getOrAssignId(stack, player.serverLevel());
        UnboxingSession session = new UnboxingSession(
                UUID.randomUUID(), player.getUUID(), hand, itemId, gameTime
        );
        if (!session.transition(UnboxingState.UNOPENED, UnboxingState.UNBOXING)) {
            return false;
        }
        ACTIVE.put(player.getUUID(), session);
        ModNetworking.sendToPlayer(player, new UnboxingStartedPacket(
                session.sessionId(), hand, itemId, stack.copy()
        ));
        ((PackagedWishingWillowItem) stack.getItem()).triggerUnboxingAnimation(player, itemId);
        return true;
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (UnboxingSession session : List.copyOf(ACTIVE.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player == null || !player.isAlive()) {
                endForLifecycle(session, player);
                continue;
            }
            long gameTime = player.serverLevel().getGameTime();
            if (session.state() == UnboxingState.UNBOXING) {
                if (!isHeldSessionItem(player, session)) {
                    cancel(session, player);
                } else if (session.readyToRemove(gameTime)) {
                    removeWillow(session, player);
                }
            } else if (session.readyToFinish(gameTime)) {
                finish(session, player);
            }
        }
    }

    private static boolean isHeldSessionItem(ServerPlayer player, UnboxingSession session) {
        ItemStack held = player.getItemInHand(session.hand());
        return held.is(ModItems.PACKAGED_WISHING_WILLOW.get())
                && GeoItem.getId(held) == session.itemInstanceId();
    }

    private static void removeWillow(UnboxingSession session, ServerPlayer player) {
        if (session.state() != UnboxingState.UNBOXING
                || !UnboxingInventoryExchange.exchange(player, session.hand())
                || !session.transition(UnboxingState.UNBOXING, UnboxingState.WILLOW_REMOVED)) {
            return;
        }
        ModNetworking.sendToPlayer(player, new UnboxingStatePacket(
                session.sessionId(), UnboxingState.WILLOW_REMOVED
        ));
    }

    private static void finish(UnboxingSession session, ServerPlayer player) {
        if (!session.transition(UnboxingState.WILLOW_REMOVED, UnboxingState.FINISHED)) {
            return;
        }
        ACTIVE.remove(session.playerId(), session);
        ModNetworking.sendToPlayer(player, new UnboxingStatePacket(
                session.sessionId(), UnboxingState.FINISHED
        ));
    }

    private static void cancel(UnboxingSession session, ServerPlayer player) {
        if (!session.transition(UnboxingState.UNBOXING, UnboxingState.CANCELLED)) {
            return;
        }
        ACTIVE.remove(session.playerId(), session);
        ModNetworking.sendToPlayer(player, new UnboxingStatePacket(
                session.sessionId(), UnboxingState.CANCELLED
        ));
    }

    private static void endForLifecycle(UnboxingSession session, ServerPlayer player) {
        if (session.state() == UnboxingState.UNBOXING) {
            if (player != null) {
                cancel(session, player);
            } else {
                session.transition(UnboxingState.UNBOXING, UnboxingState.CANCELLED);
                ACTIVE.remove(session.playerId(), session);
            }
        } else if (session.state() == UnboxingState.WILLOW_REMOVED) {
            session.transition(UnboxingState.WILLOW_REMOVED, UnboxingState.FINISHED);
            ACTIVE.remove(session.playerId(), session);
            if (player != null) {
                ModNetworking.sendToPlayer(player, new UnboxingStatePacket(
                        session.sessionId(), UnboxingState.FINISHED
                ));
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnboxingSession session = ACTIVE.get(player.getUUID());
            if (session != null) {
                endForLifecycle(session, null);
            }
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnboxingSession session = ACTIVE.get(player.getUUID());
            if (session != null) {
                endForLifecycle(session, player);
            }
        }
    }

    private static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UnboxingSession session = ACTIVE.get(player.getUUID());
            if (session != null && session.state() == UnboxingState.UNBOXING) {
                cancel(session, player);
            }
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        for (UnboxingSession session : List.copyOf(ACTIVE.values())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerId());
            endForLifecycle(session, player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
