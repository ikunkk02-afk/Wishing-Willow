package com.ikunkk02.wishingwillow.client.renderer;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.item.WishingWillowItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class WishingWillowItemModel extends GeoModel<WishingWillowItem> {
    private static final ResourceLocation MODEL =
            new ResourceLocation(WishingWillow.MOD_ID, "geo/wishing_willow.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(WishingWillow.MOD_ID, "textures/item/wishing_willow_geo.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(WishingWillow.MOD_ID, "animations/wishing_willow.animation.json");

    @Override
    public ResourceLocation getModelResource(WishingWillowItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WishingWillowItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WishingWillowItem animatable) {
        return ANIMATION;
    }
}
