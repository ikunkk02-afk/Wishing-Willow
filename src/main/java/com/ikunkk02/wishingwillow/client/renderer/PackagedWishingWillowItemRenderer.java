package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.client.animation.ClientUnboxingSequence;
import com.ikunkk02.wishingwillow.item.PackagedWishingWillowItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class PackagedWishingWillowItemRenderer extends GeoItemRenderer<PackagedWishingWillowItem> {
    private static final ResourceLocation GUI_ICON = new ResourceLocation(
            WishingWillow.MOD_ID, "textures/item/packaged_wishing_willow.png");

    public PackagedWishingWillowItemRenderer() {
        super(new PackagedWishingWillowItemModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (displayContext == ItemDisplayContext.GUI) {
            renderGuiIcon(poseStack, bufferSource, packedLight);
            return;
        }
        ClientUnboxingSequence.beginRender(stack);
        poseStack.pushPose();
        try {
            float scale = switch (displayContext) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.30F;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.34F;
                case GROUND -> 0.30F;
                case FIXED -> 0.42F;
                default -> 0.36F;
            };
            poseStack.scale(scale, scale, scale);
            super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        } finally {
            poseStack.popPose();
            ClientUnboxingSequence.endRender();
        }
    }

    private static void renderGuiIcon(PoseStack poseStack, MultiBufferSource source, int light) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.94F, 0.94F, 0.94F);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer vertices = source.getBuffer(RenderType.entityCutoutNoCull(GUI_ICON));
        vertex(vertices, pose, normal, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F, light);
        vertex(vertices, pose, normal, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, light);
        vertex(vertices, pose, normal, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light);
        vertex(vertices, pose, normal, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v, int light) {
        vertices.vertex(pose, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
