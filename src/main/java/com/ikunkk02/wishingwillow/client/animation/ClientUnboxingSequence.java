package com.ikunkk02.wishingwillow.client.animation;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.UnboxingStatePacket;
import com.ikunkk02.wishingwillow.registry.ModSounds;
import com.ikunkk02.wishingwillow.unboxing.UnboxingSession;
import com.ikunkk02.wishingwillow.unboxing.UnboxingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientUnboxingSequence {
    private static final int TIMEOUT_TICKS = 180;
    @Nullable private static ClientSession active;
    private static long clientTicks;
    private static long renderingItemId = Long.MIN_VALUE;

    private ClientUnboxingSequence() {
    }

    public static void start(UnboxingStartedPacket packet) {
        active = new ClientSession(packet.sessionId(), packet.hand(), packet.itemInstanceId(),
                packet.stackSnapshot().copy(), clientTicks);
        play(ModSounds.UNBOXING_MUSIC.get(), 0.62F, 1.0F);
    }

    public static void updateState(UnboxingStatePacket packet) {
        if (active == null || !active.sessionId.equals(packet.sessionId())) {
            return;
        }
        active.state = packet.state();
        if (packet.state().terminal()) {
            active = null;
            renderingItemId = Long.MIN_VALUE;
        }
    }

    public static void beginRender(ItemStack stack) {
        renderingItemId = GeoItem.getId(stack);
    }

    public static void endRender() {
        renderingItemId = Long.MIN_VALUE;
    }

    public static boolean shouldAnimateRenderedItem() {
        return active != null && renderingItemId == active.itemInstanceId;
    }

    @Nullable
    public static InteractionHand activeHand() {
        return active == null ? null : active.hand;
    }

    public static ItemStack ghostStack() {
        return active == null ? ItemStack.EMPTY : active.ghostStack;
    }

    public static float elapsedTicks(float partialTick) {
        return active == null ? 0.0F : clientTicks - active.startedClientTick + partialTick;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTicks++;
        ClientSession session = active;
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null) {
            return;
        }
        long age = clientTicks - session.startedClientTick;
        if (!session.rustlePlayed && age >= 8) {
            session.rustlePlayed = true;
            play(ModSounds.PACKAGE_RUSTLE.get(), 0.52F, 0.96F);
        }
        if (!session.flapPlayed && age >= 18) {
            session.flapPlayed = true;
            play(ModSounds.PACKAGE_FLAP.get(), 0.58F, 1.02F);
        }
        if (!session.slidePlayed && age >= 28) {
            session.slidePlayed = true;
            play(ModSounds.WILLOW_SLIDE.get(), 0.48F, 0.92F);
        }
        if (minecraft.player == null || minecraft.level == null || age > TIMEOUT_TICKS) {
            active = null;
            renderingItemId = Long.MIN_VALUE;
        }
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getEyeY(),
                    minecraft.player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientSession session = active;
        if (session == null) {
            return;
        }
        float age = elapsedTicks(event.getPartialTick());
        if (age < 52.0F || age > UnboxingSession.FINISH_TICK) {
            return;
        }
        float progress = (age - 52.0F) / (UnboxingSession.FINISH_TICK - 52.0F);
        float alpha = Mth.sin(Mth.clamp(progress, 0.0F, 1.0F) * (float) Math.PI) * 0.78F;
        GuiGraphics graphics = event.getGuiGraphics();
        int color = ((int) (alpha * 255.0F) << 24) | 0xE8DCC4;
        graphics.drawCenteredString(Minecraft.getInstance().font,
                net.minecraft.network.chat.Component.translatable("item.wishing_willow.wishing_willow"),
                graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 26, color);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = null;
        renderingItemId = Long.MIN_VALUE;
    }

    private static final class ClientSession {
        private final UUID sessionId;
        private final InteractionHand hand;
        private final long itemInstanceId;
        private final ItemStack ghostStack;
        private final long startedClientTick;
        private UnboxingState state = UnboxingState.UNBOXING;
        private boolean rustlePlayed;
        private boolean flapPlayed;
        private boolean slidePlayed;

        private ClientSession(UUID sessionId, InteractionHand hand, long itemInstanceId,
                              ItemStack ghostStack, long startedClientTick) {
            this.sessionId = sessionId;
            this.hand = hand;
            this.itemInstanceId = itemInstanceId;
            this.ghostStack = ghostStack;
            this.startedClientTick = startedClientTick;
        }
    }
}
