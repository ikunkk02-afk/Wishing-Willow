package com.ikunkk02.wishingwillow.client.animation;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.client.hints.ClientWishProcessingHints;
import com.ikunkk02.wishingwillow.network.ModNetworking;
import com.ikunkk02.wishingwillow.network.packet.WishAnimationEventPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStartedPacket;
import com.ikunkk02.wishingwillow.network.packet.WishStatePacket;
import com.ikunkk02.wishingwillow.network.packet.WishOmenPacket;
import com.ikunkk02.wishingwillow.omen.WishOmenHistory;
import com.ikunkk02.wishingwillow.wish.WishAnimationEvent;
import com.ikunkk02.wishingwillow.wish.WishRejectionReason;
import com.ikunkk02.wishingwillow.wish.WishState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import com.ikunkk02.wishingwillow.client.music.WishingWillowMusicController;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientWishSequence {
    public static final float PREPARE_END_TICK = 12.0F;
    public static final float BEND_END_TICK = 23.0F;
    public static final float SNAP_END_TICK = 26.0F;
    public static final float BROKEN_END_TICK = 34.0F;
    public static final float SEQUENCE_END_TICK = 45.0F;

    private static final ResourceLocation VIGNETTE =
            new ResourceLocation(WishingWillow.MOD_ID, "textures/gui/wish_vignette.png");
    private static final int COMPLETION_DURATION_TICKS = 50;
    private static final int VIGNETTE_DURATION_TICKS = 12;
    private static final int SHAKE_DURATION_TICKS = 3;
    private static final int CLIENT_TIMEOUT_TICKS = 240;
    private static final int OMEN_DURATION_TICKS = 60;

    @Nullable
    private static ClientSession activeSession;
    private static long clientTicks;
    private static long snapEffectStart = Long.MIN_VALUE;
    private static long completionStart = Long.MIN_VALUE;
    private static long rejectionStart = Long.MIN_VALUE;
    @Nullable
    private static Component rejectionMessage;
    private static long renderingItemId = Long.MIN_VALUE;
    private static final WishOmenHistory processedOmens = new WishOmenHistory(32);
    @Nullable
    private static UUID completionSessionId;
    @Nullable
    private static WishOmenPacket pendingOmen;
    @Nullable
    private static WishOmenPacket activeOmen;
    private static long omenStart = Long.MIN_VALUE;

    private ClientWishSequence() {
    }

    public static void start(WishStartedPacket packet) {
        WishingWillowMusicController.startWishSequence();
        activeSession = new ClientSession(
                packet.sessionId(),
                packet.hand(),
                packet.itemInstanceId(),
                packet.stackSnapshot().copy(),
                clientTicks
        );
        snapEffectStart = Long.MIN_VALUE;
        rejectionMessage = null;
    }

    public static void updateState(WishStatePacket packet) {
        ClientSession session = activeSession;
        if (packet.state() == WishState.CANCELLED) {
            WishingWillowMusicController.cancelWishSequence();
            if (session != null && session.sessionId.equals(packet.correlationId())) {
                clearActive();
            }
            if (packet.reason() != WishRejectionReason.NONE) {
                rejectionMessage = Component.translatable(
                        "message.wishing_willow.rejected." + packet.reason().name().toLowerCase(Locale.ROOT)
                );
                rejectionStart = clientTicks;
            }
        } else if (packet.state() == WishState.FINISHED
                && session != null
                && session.sessionId.equals(packet.correlationId())) {
            completionSessionId = session.sessionId;
            clearActive();
            completionStart = clientTicks;
            if (pendingOmen != null && pendingOmen.sessionId().equals(completionSessionId)) {
                scheduleOmen(pendingOmen);
                pendingOmen = null;
            }
        }
    }

    public static void receiveOmen(WishOmenPacket packet) {
        if (!packet.translationKey().startsWith("omen.wishing_willow.")
                || packet.delayTicks() < 40 || packet.delayTicks() > 100
                || !processedOmens.accept(packet.sessionId())) {
            return;
        }
        ClientWishProcessingHints.stop();
        if (packet.sessionId().equals(completionSessionId) && completionStart != Long.MIN_VALUE) {
            scheduleOmen(packet);
        } else {
            pendingOmen = packet;
        }
    }

    public static void beginRender(ItemStack stack) {
        renderingItemId = GeoItem.getId(stack);
    }

    public static void endRender() {
        renderingItemId = Long.MIN_VALUE;
    }

    public static void handleKeyframe(String rawInstruction) {
        ClientSession session = activeSession;
        if (session == null || renderingItemId != session.itemInstanceId) {
            return;
        }
        String instruction = rawInstruction.replace("\"", "").trim();
        if ("snap".equals(instruction) && !session.snapSent) {
            session.snapSent = true;
            playSnapEffects(session.sessionId);
            ModNetworking.sendToServer(
                    new WishAnimationEventPacket(session.sessionId, WishAnimationEvent.SNAP)
            );
        } else if ("finish".equals(instruction) && !session.finishSent) {
            session.finishSent = true;
            ModNetworking.sendToServer(
                    new WishAnimationEventPacket(session.sessionId, WishAnimationEvent.FINISH)
            );
        }
    }

    public static boolean isActive() {
        return activeSession != null;
    }

    @Nullable
    public static InteractionHand activeHand() {
        return activeSession == null ? null : activeSession.hand;
    }

    public static ItemStack ghostStack() {
        return activeSession == null ? ItemStack.EMPTY : activeSession.ghostStack;
    }

    public static float elapsedTicks(float partialTick) {
        return activeSession == null ? 0.0F : clientTicks - activeSession.startedClientTick + partialTick;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTicks++;
        Minecraft minecraft = Minecraft.getInstance();
        if (activeSession != null) {
            if (minecraft.player == null
                    || minecraft.level == null
                    || clientTicks - activeSession.startedClientTick > CLIENT_TIMEOUT_TICKS) {
                clearActive();
            }
        }
        if (activeOmen != null && clientTicks - omenStart > OMEN_DURATION_TICKS) {
            activeOmen = null;
            omenStart = Long.MIN_VALUE;
            WishingWillowMusicController.omenFinished();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        long age = clientTicks - snapEffectStart;
        if (age < 0 || age > SHAKE_DURATION_TICKS) {
            return;
        }
        float progress = (age + (float) event.getPartialTick()) / SHAKE_DURATION_TICKS;
        float strength = (1.0F - Mth.clamp(progress, 0.0F, 1.0F)) * 0.65F;
        event.setYaw(event.getYaw() + Mth.sin(progress * 19.0F) * strength);
        event.setPitch(event.getPitch() + Mth.cos(progress * 23.0F) * strength * 0.65F);
        event.setRoll(event.getRoll() + Mth.sin(progress * 15.0F) * strength * 0.45F);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        GuiGraphics graphics = event.getGuiGraphics();
        renderSnapOverlay(graphics, event.getPartialTick());
        renderCenteredMessages(graphics, event.getPartialTick());
        renderOmen(graphics, event.getPartialTick());
    }

    private static void playSnapEffects(UUID sessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        snapEffectStart = clientTicks;
        minecraft.level.playLocalSound(
                minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
                SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.75F, 0.92F, false
        );
        minecraft.level.playLocalSound(
                minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
                SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.28F, 0.72F, false
        );

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vector3f left = camera.getLeftVector();
        Vec3 origin = cameraPosition.add(
                look.x() * 1.18F + left.x() * 0.02F,
                look.y() * 1.18F - 0.07F + left.y() * 0.02F,
                look.z() * 1.18F + left.z() * 0.02F
        );
        RandomSource random = RandomSource.create(sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits());
        for (int index = 0; index < 6; index++) {
            boolean pale = index < 2;
            BlockParticleOption particle = new BlockParticleOption(
                    ParticleTypes.BLOCK,
                    pale ? Blocks.STRIPPED_OAK_LOG.defaultBlockState() : Blocks.DARK_OAK_LOG.defaultBlockState()
            );
            minecraft.level.addParticle(
                    particle,
                    origin.x, origin.y, origin.z,
                    (random.nextDouble() - 0.5D) * 0.038D,
                    (random.nextDouble() - 0.15D) * 0.032D,
                    (random.nextDouble() - 0.5D) * 0.038D
            );
        }
    }

    private static void renderSnapOverlay(GuiGraphics graphics, float partialTick) {
        long age = clientTicks - snapEffectStart;
        if (age < 0 || age > VIGNETTE_DURATION_TICKS) {
            return;
        }
        float progress = (age + partialTick) / VIGNETTE_DURATION_TICKS;
        float alpha = Mth.sin(Mth.clamp(progress, 0.0F, 1.0F) * (float) Math.PI) * 0.72F;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, ((int) (alpha * 45.0F) << 24));
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderCenteredMessages(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        long completionAge = clientTicks - completionStart;
        if (completionAge >= 0 && completionAge <= COMPLETION_DURATION_TICKS) {
            float alpha = fadeAlpha(completionAge + partialTick, COMPLETION_DURATION_TICKS, 8.0F);
            int color = ((int) (alpha * 255.0F) << 24) | 0xD8D5CE;
            graphics.drawCenteredString(
                    minecraft.font,
                    Component.translatable("message.wishing_willow.heard"),
                    graphics.guiWidth() / 2,
                    graphics.guiHeight() / 2 - 12,
                    color
            );
        }

        long rejectionAge = clientTicks - rejectionStart;
        if (rejectionMessage != null && rejectionAge >= 0 && rejectionAge <= 40) {
            float alpha = fadeAlpha(rejectionAge + partialTick, 40.0F, 6.0F);
            int color = ((int) (alpha * 255.0F) << 24) | 0xBDB7AF;
            graphics.drawCenteredString(
                    minecraft.font,
                    rejectionMessage,
                    graphics.guiWidth() / 2,
                    graphics.guiHeight() / 2 + 14,
                    color
            );
        }
    }

    private static float fadeAlpha(float age, float duration, float fadeDuration) {
        return Mth.clamp(Math.min(age / fadeDuration, (duration - age) / fadeDuration), 0.0F, 1.0F);
    }

    private static void scheduleOmen(WishOmenPacket packet) {
        activeOmen = packet;
        long afterHeard = completionStart + COMPLETION_DURATION_TICKS + 1L;
        omenStart = Math.max(clientTicks, afterHeard) + packet.delayTicks();
    }

    private static void renderOmen(GuiGraphics graphics, float partialTick) {
        if (activeOmen == null) {
            return;
        }
        float age = clientTicks - omenStart + partialTick;
        if (age < 0.0F || age > OMEN_DURATION_TICKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float alpha = fadeAlpha(age, OMEN_DURATION_TICKS, 10.0F);
        int color = ((int) (alpha * 255.0F) << 24) | 0xA99C8A;
        int maxWidth = Math.min(280, Math.max(120, graphics.guiWidth() - 48));
        List<FormattedCharSequence> lines = minecraft.font.split(
                Component.translatable(activeOmen.translationKey()), maxWidth
        );
        int count = Math.min(2, lines.size());
        int y = Math.max(24, graphics.guiHeight() / 3 - count * 5);
        for (int index = 0; index < count; index++) {
            FormattedCharSequence line = lines.get(index);
            int x = (graphics.guiWidth() - minecraft.font.width(line)) / 2;
            graphics.drawString(minecraft.font, line, x, y + index * 11, color, true);
        }
    }

    private static void clearActive() {
        activeSession = null;
        renderingItemId = Long.MIN_VALUE;
    }

    private static void clearAll() {
        WishingWillowMusicController.clear();
        clearActive();
        snapEffectStart = Long.MIN_VALUE;
        completionStart = Long.MIN_VALUE;
        rejectionStart = Long.MIN_VALUE;
        rejectionMessage = null;
        completionSessionId = null;
        pendingOmen = null;
        activeOmen = null;
        omenStart = Long.MIN_VALUE;
        processedOmens.clear();
    }

    private static final class ClientSession {
        private final UUID sessionId;
        private final InteractionHand hand;
        private final long itemInstanceId;
        private final ItemStack ghostStack;
        private final long startedClientTick;
        private boolean snapSent;
        private boolean finishSent;

        private ClientSession(
                UUID sessionId,
                InteractionHand hand,
                long itemInstanceId,
                ItemStack ghostStack,
                long startedClientTick
        ) {
            this.sessionId = sessionId;
            this.hand = hand;
            this.itemInstanceId = itemInstanceId;
            this.ghostStack = ghostStack;
            this.startedClientTick = startedClientTick;
        }
    }
}
