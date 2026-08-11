package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A small deterministic first-person preview; UVs match the authored atlas exactly. */
final class UnboxingPackagePreviewRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            WishingWillow.MOD_ID, "textures/item/packaged_wishing_willow_geo.png");
    private static final float U = 1.0F / 128.0F;

    private UnboxingPackagePreviewRenderer() {
    }

    static void render(PoseStack root, MultiBufferSource buffers, int light, float elapsed, float side,
                       Minecraft minecraft, LocalPlayer player) {
        float open = smooth(elapsed, 12.0F, 24.0F);
        float extract = smooth(elapsed, 24.0F, 40.0F);
        float inspect = smooth(elapsed, 40.0F, 52.0F);
        float lower = smooth(elapsed, 40.0F, 52.0F);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        root.pushPose();
        root.translate(0.0F, -0.13F, -1.04F);
        root.mulPose(Axis.ZP.rotationDegrees(side * -2.0F));
        root.mulPose(Axis.YP.rotationDegrees(side * 2.0F));
        root.scale(0.74F, 0.74F, 0.74F);

        if (elapsed < 52.0F) {
            root.pushPose();
            root.translate(-side * extract * 0.07F, -lower * 0.52F, lower * 0.08F);
            renderCarton(root, vertices, light, open, side);
            root.popPose();
        }

        if (elapsed >= 18.0F) {
            root.pushPose();
            float pulled = extract * side * 0.36F;
            root.translate(Mth.lerp(inspect, pulled, 0.0F), Mth.lerp(inspect, -0.015F, 0.07F), -0.015F);
            root.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(inspect, side * 4.0F, 0.0F)));
            renderWillow(root, buffers, light, minecraft, player);
            root.popPose();
        }
        root.popPose();
    }

    private static void renderCarton(PoseStack pose, VertexConsumer vertices, int light,
                                     float open, float side) {
        float left = -0.50F;
        float right = 0.50F;
        float ay = 0.115F, az = 0.0F;
        float by = -0.085F, bz = -0.11F;
        float cy = -0.085F, cz = 0.11F;

        // Back and bottom papers remain as the cheap triangular sleeve.
        quad(pose, vertices, light,
                left, ay, az, right, ay, az, right, by, bz, left, by, bz,
                2, 62, 126, 106);
        quad(pose, vertices, light,
                left, by, bz, right, by, bz, right, cy, cz, left, cy, cz,
                2, 44, 126, 60);

        // Printed front is a real lid that peels away from the long fold.
        pose.pushPose();
        pose.translate(0.0F, ay, az);
        pose.mulPose(Axis.XP.rotationDegrees(-open * 108.0F));
        quad(pose, vertices, light,
                left, 0, 0, right, 0, 0, right, cy - ay, cz - az, left, cy - ay, cz - az,
                2, 2, 126, 42);
        pose.popPose();

        // End papers; the extraction-side flap disappears as it is folded open.
        triangle(pose, vertices, light, -0.505F, ay, az, -0.505F, by, bz, -0.505F, cy, cz,
                2, 62, 28, 106);
        if (open < 0.82F) {
            triangle(pose, vertices, light, 0.505F, ay, az, 0.505F, cy, cz, 0.505F, by, bz,
                    100, 62, 126, 106);
        }
    }

    private static void renderWillow(PoseStack pose, MultiBufferSource buffers, int light,
                                     Minecraft minecraft, LocalPlayer player) {
        pose.pushPose();
        // Keep the extracted willow in front of the carton faces. The preview
        // carton spans roughly z=-0.11..0.11, so a positive offset prevents
        // the branch from being depth-tested behind the printed sleeve.
        pose.translate(0.0F, -0.070F, 0.18F);
        pose.scale(0.43F, 0.43F, 0.43F);
        minecraft.getItemRenderer().renderStatic(
                player,
                new ItemStack(ModItems.WISHING_WILLOW.get()),
                ItemDisplayContext.NONE,
                false,
                pose,
                buffers,
                player.level(),
                light,
                OverlayTexture.NO_OVERLAY,
                player.getId()
        );
        pose.popPose();
    }

    private static void triangle(PoseStack pose, VertexConsumer vertices, int light,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, int u0, int v0, int u1, int v1) {
        quad(pose, vertices, light, ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz, u0, v0, u1, v1);
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             int u0, int v0, int u1, int v1) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        vertex(vertices, matrix, normal, ax, ay, az, u0 * U, v0 * U, light);
        vertex(vertices, matrix, normal, bx, by, bz, u1 * U, v0 * U, light);
        vertex(vertices, matrix, normal, cx, cy, cz, u1 * U, v1 * U, light);
        vertex(vertices, matrix, normal, dx, dy, dz, u0 * U, v1 * U, light);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Matrix3f normal,
                               float x, float y, float z, float u, float v, int light) {
        vertices.vertex(pose, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F).endVertex();
    }

    private static float smooth(float value, float start, float end) {
        float progress = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
