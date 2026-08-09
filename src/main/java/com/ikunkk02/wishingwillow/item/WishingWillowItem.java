package com.ikunkk02.wishingwillow.item;

import com.ikunkk02.wishingwillow.client.renderer.WishingWillowItemRenderer;
import com.ikunkk02.wishingwillow.wish.WishManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class WishingWillowItem extends Item implements GeoItem {
    private static final String TOOLTIP_KEY = "tooltip.wishing_willow.wishing_willow";
    private static final String WISH_CONTROLLER = "wish_controller";
    private static final String WISH_TRIGGER = "wish";
    private static final RawAnimation IDLE = RawAnimation.begin()
            .thenLoop("animation.wishing_willow.idle");
    private static final RawAnimation WISH_SEQUENCE = RawAnimation.begin()
            .thenPlay("animation.wishing_willow.wish_prepare")
            .thenPlay("animation.wishing_willow.wish_bend")
            .thenPlay("animation.wishing_willow.wish_snap")
            .thenPlay("animation.wishing_willow.wish_broken")
            .thenPlay("animation.wishing_willow.wish_disappear");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public WishingWillowItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            WishManager.tryOpenScreen(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public void triggerWishAnimation(ServerPlayer player, long itemInstanceId) {
        triggerAnim(player, itemInstanceId, WISH_CONTROLLER, WISH_TRIGGER);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private WishingWillowItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new WishingWillowItemRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 8, state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, WISH_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(WISH_TRIGGER, WISH_SEQUENCE)
                .setCustomInstructionKeyframeHandler(event -> {
                    String instruction = event.getKeyframeData().getInstructions();
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> com.ikunkk02.wishingwillow.client.animation.ClientWishSequence
                                    .handleKeyframe(instruction)
                    );
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}
