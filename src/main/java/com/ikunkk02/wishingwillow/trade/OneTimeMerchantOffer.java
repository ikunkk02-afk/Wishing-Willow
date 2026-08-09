package com.ikunkk02.wishingwillow.trade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public final class OneTimeMerchantOffer extends MerchantOffer {
    private static final int MAX_USES = 1;

    public OneTimeMerchantOffer(
            ItemStack baseCost,
            ItemStack secondaryCost,
            ItemStack result,
            int villagerXp,
            float priceMultiplier
    ) {
        super(baseCost, secondaryCost, result, MAX_USES, villagerXp, priceMultiplier);
    }

    @Override
    public ItemStack getCostA() {
        return getBaseCostA().copy();
    }

    @Override
    public void resetUses() {
        // This offer is intentionally never replenished by normal villager restocks.
    }
}
