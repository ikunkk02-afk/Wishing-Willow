package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.item.PackagedWishingWillowItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class PackagedWishingWillowItemModel extends GeoModel<PackagedWishingWillowItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            WishingWillow.MOD_ID, "geo/packaged_wishing_willow.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            WishingWillow.MOD_ID, "textures/item/packaged_wishing_willow_geo.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(
            WishingWillow.MOD_ID, "animations/packaged_wishing_willow.animation.json");

    @Override
    public ResourceLocation getModelResource(PackagedWishingWillowItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PackagedWishingWillowItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PackagedWishingWillowItem animatable) {
        return ANIMATION;
    }
}
