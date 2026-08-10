package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.client.animation.ClientUnboxingSequence;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WishingWillow.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonUnboxingRenderer {
    private FirstPersonUnboxingRenderer() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        InteractionHand activeHand = ClientUnboxingSequence.activeHand();
        if (activeHand == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSleeping() || player.isPassenger()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != activeHand) {
            return;
        }
        render(event, minecraft, player, activeHand);
    }

    private static void render(RenderHandEvent event, Minecraft minecraft, LocalPlayer player,
                               InteractionHand activeHand) {
        float elapsed = ClientUnboxingSequence.elapsedTicks(event.getPartialTick());
        float raise = smooth(elapsed, 0.0F, 6.0F);
        float assist = smooth(elapsed, 6.0F, 12.0F);
        float open = smooth(elapsed, 12.0F, 24.0F);
        float extract = smooth(elapsed, 24.0F, 40.0F);
        float lower = smooth(elapsed, 40.0F, 52.0F);

        HumanoidArm activeArm = activeHand == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        HumanoidArm assistingArm = activeArm.getOpposite();
        float side = activeArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (!player.isInvisible()) {
            FirstPersonArmRenderer.renderArm(event.getPoseStack(), event, renderer, player, activeArm,
                    side * Mth.lerp(raise, 0.72F, 0.34F),
                    Mth.lerp(raise, -0.72F, -0.08F) - lower * 0.18F,
                    Mth.lerp(raise, -0.72F, -0.92F),
                    side * (-5.0F + open * 4.0F));
            float assistSide = assistingArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            FirstPersonArmRenderer.renderArm(event.getPoseStack(), event, renderer, player, assistingArm,
                    assistSide * Mth.lerp(assist, 1.12F, 0.28F)
                            + assistSide * extract * 0.12F,
                    Mth.lerp(assist, -0.72F, -0.01F) - extract * 0.05F,
                    Mth.lerp(assist, -0.70F, -0.86F),
                    -assistSide * (open * 12.0F - extract * 5.0F));
        }

        ItemStack ghost = ClientUnboxingSequence.ghostStack();
        if (ghost.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * Mth.lerp(raise, 0.30F, 0.02F),
                Mth.lerp(raise, -0.48F, -0.12F) - lower * 0.08F,
                Mth.lerp(raise, -0.85F, -1.06F));
        pose.mulPose(Axis.ZP.rotationDegrees(side * Mth.lerp(raise, -14.0F, -2.0F)));
        pose.mulPose(Axis.YP.rotationDegrees(side * 3.0F));
        pose.scale(0.78F, 0.78F, 0.78F);
        minecraft.getItemRenderer().renderStatic(player, ghost, ItemDisplayContext.NONE, false,
                pose, event.getMultiBufferSource(), player.level(), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
    }

    private static float smooth(float value, float start, float end) {
        float progress = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
