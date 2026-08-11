package com.ikunkk02.wishingwillow.item;

import com.ikunkk02.wishingwillow.unboxing.UnboxingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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

public final class PackagedWishingWillowItem extends Item implements GeoItem {
    private static final String CONTROLLER = "unboxing_controller";
    private static final String TRIGGER = "unbox";
    private static final RawAnimation IDLE = RawAnimation.begin()
            .thenLoop("animation.packaged_wishing_willow.idle");
    private static final RawAnimation UNBOX = RawAnimation.begin()
            .thenPlay("animation.packaged_wishing_willow.unbox");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public PackagedWishingWillowItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            UnboxingManager.tryStart(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public void triggerUnboxingAnimation(ServerPlayer player, long itemInstanceId) {
        triggerAnim(player, itemInstanceId, CONTROLLER, TRIGGER);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle_controller", 8,
                state -> state.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(TRIGGER, UNBOX));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.title")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.once")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.after")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.hold")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.speak")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.crack")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.wishing_willow.packaged.quote")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_RED));
    }
}
