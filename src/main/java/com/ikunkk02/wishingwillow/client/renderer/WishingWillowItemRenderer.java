package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.client.animation.ClientWishSequence;
import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.item.WishingWillowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class WishingWillowItemRenderer extends GeoItemRenderer<WishingWillowItem> {
    private static final ResourceLocation GUI_ICON =
            new ResourceLocation(WishingWillow.MOD_ID, "textures/item/wishing_willow.png");

    public WishingWillowItemRenderer() {
        super(new WishingWillowItemModel());
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (displayContext == ItemDisplayContext.GUI) {
            renderGuiIcon(poseStack, bufferSource, packedLight);
            return;
        }
        ClientWishSequence.beginRender(stack);
        try {
            super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            ClientWishSequence.endRender();
        }
    }

    private static void renderGuiIcon(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.92F, 0.92F, 0.92F);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.entityCutoutNoCull(GUI_ICON));
        vertex(vertices, pose, normal, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F, packedLight);
        vertex(vertices, pose, normal, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, packedLight);
        vertex(vertices, pose, normal, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, packedLight);
        vertex(vertices, pose, normal, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, packedLight);
        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer vertices,
            Matrix4f pose,
            Matrix3f normal,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight
    ) {
        vertices.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
