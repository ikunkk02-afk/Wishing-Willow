package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.client.animation.ClientWishSequence;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonWishRenderer {
    private FirstPersonWishRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        InteractionHand activeHand = ClientWishSequence.activeHand();
        if (activeHand == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSleeping() || player.isPassenger()) {
            return;
        }

        if (event.getHand() != activeHand) {
            if (event.getItemStack().isEmpty()) {
                event.setCanceled(true);
            }
            return;
        }

        event.setCanceled(true);
        renderWishSequence(event, minecraft, player, activeHand);
    }

    private static void renderWishSequence(
            RenderHandEvent event,
            Minecraft minecraft,
            LocalPlayer player,
            InteractionHand activeHand
    ) {
        float elapsed = ClientWishSequence.elapsedTicks(event.getPartialTick());
        float prepare = smoothProgress(elapsed, 0.0F, ClientWishSequence.PREPARE_END_TICK);
        float bend = smoothProgress(
                elapsed,
                ClientWishSequence.PREPARE_END_TICK,
                ClientWishSequence.BEND_END_TICK
        );
        float snap = smoothProgress(elapsed, ClientWishSequence.BEND_END_TICK, ClientWishSequence.SNAP_END_TICK);
        float disappear = smoothProgress(
                elapsed,
                ClientWishSequence.BROKEN_END_TICK,
                ClientWishSequence.SEQUENCE_END_TICK
        );

        HumanoidArm activeArm = activeHand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        HumanoidArm assistingArm = activeArm.getOpposite();
        float side = activeArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        PlayerRenderer playerRenderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!player.isInvisible()) {
            renderArm(
                    event.getPoseStack(), event, playerRenderer, player, activeArm,
                    side * Mth.lerp(prepare, 0.68F, 0.30F) + side * snap * 0.08F,
                    Mth.lerp(prepare, -0.48F, -0.27F) - snap * 0.04F,
                    -0.72F,
                    side * Mth.lerp(bend, 22.0F, 8.0F) + side * snap * 12.0F
            );

            InteractionHand oppositeHand = activeHand == InteractionHand.MAIN_HAND
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;
            if (player.getItemInHand(oppositeHand).isEmpty()) {
                float assistEntrance = smoothProgress(elapsed, 4.0F, ClientWishSequence.PREPARE_END_TICK);
                float assistSide = assistingArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                renderArm(
                        event.getPoseStack(), event, playerRenderer, player, assistingArm,
                        assistSide * Mth.lerp(assistEntrance, 1.05F, 0.31F) - assistSide * snap * 0.10F,
                        Mth.lerp(assistEntrance, -0.62F, -0.25F) - snap * 0.04F,
                        -0.70F,
                        assistSide * Mth.lerp(bend, 28.0F, 5.0F) - assistSide * snap * 15.0F
                );
            }
        }

        ItemStack ghostStack = ClientWishSequence.ghostStack();
        if (ghostStack.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        float recoil = Mth.sin(snap * (float) Math.PI) * 0.07F;
        poseStack.translate(
                side * Mth.lerp(prepare, 0.42F, 0.0F) + side * recoil,
                Mth.lerp(prepare, -0.38F, -0.12F) + recoil * 0.35F,
                Mth.lerp(prepare, -0.64F, -0.58F)
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(side * Mth.lerp(prepare, -18.0F, 0.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(bend, 2.5F, -1.5F)));
        float scale = 1.18F * Mth.lerp(disappear, 1.0F, 0.08F);
        poseStack.scale(scale, scale, scale);

        ItemDisplayContext displayContext = activeArm == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        minecraft.getItemRenderer().renderStatic(
                player,
                ghostStack,
                displayContext,
                activeArm == HumanoidArm.LEFT,
                poseStack,
                event.getMultiBufferSource(),
                player.level(),
                event.getPackedLight(),
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                player.getId() + displayContext.ordinal()
        );
        poseStack.popPose();
    }

    private static void renderArm(
            PoseStack rootPose,
            RenderHandEvent event,
            PlayerRenderer renderer,
            LocalPlayer player,
            HumanoidArm arm,
            float x,
            float y,
            float z,
            float zRotation
    ) {
        PoseStack poseStack = rootPose;
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(-42.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 22.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRotation));
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        poseStack.popPose();
    }

    private static float smoothProgress(float value, float start, float end) {
        float progress = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
