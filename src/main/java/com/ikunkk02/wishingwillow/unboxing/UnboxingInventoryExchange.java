package com.ikunkk02.wishingwillow.unboxing;

import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class UnboxingInventoryExchange {
    private UnboxingInventoryExchange() {
    }

    public static boolean exchange(ServerPlayer player, InteractionHand hand) {
        ItemStack packageStack = player.getItemInHand(hand);
        if (!packageStack.is(ModItems.PACKAGED_WISHING_WILLOW.get())) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            ItemStack retainedPackage = packageStack.copy();
            if (!player.getInventory().add(retainedPackage)) {
                player.drop(retainedPackage, false);
            }
        } else {
            packageStack.shrink(1);
        }
        player.setItemInHand(hand, new ItemStack(ModItems.WISHING_WILLOW.get()));
        player.getInventory().setChanged();
        return true;
    }
}
