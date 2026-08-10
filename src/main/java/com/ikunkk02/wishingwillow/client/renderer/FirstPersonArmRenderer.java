package com.ikunkk02.wishingwillow.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.client.event.RenderHandEvent;

final class FirstPersonArmRenderer {
    private FirstPersonArmRenderer() {
    }

    static void renderArm(PoseStack rootPose, RenderHandEvent event, PlayerRenderer renderer,
                          LocalPlayer player, HumanoidArm arm, float x, float y, float z,
                          float zRotation) {
        PoseStack poseStack = rootPose;
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(zRotation));
        poseStack.translate(-side, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        poseStack.popPose();
    }
}
