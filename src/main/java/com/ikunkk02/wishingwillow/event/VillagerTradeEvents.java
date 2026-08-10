package com.ikunkk02.wishingwillow.event;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.registry.ModItems;
import com.ikunkk02.wishingwillow.trade.OneTimeMerchantOffer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;

public final class VillagerTradeEvents {
    private static final String TRADE_COMPLETED_TAG = WishingWillow.MOD_ID + ":trade_completed";
    private static final int EMERALD_COST = 32;
    private static final int VILLAGER_XP = 30;
    private static final float PRICE_MULTIPLIER = 0.0F;

    private VillagerTradeEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(VillagerTradeEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(VillagerTradeEvents::onTradeWithVillager);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (event.getTarget() instanceof Villager villager && isMasterCleric(villager)) {
            ensureWishingWillowOffer(villager);
        }
    }

    private static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getAbstractVillager() instanceof Villager villager)
                || !isMasterCleric(villager)
                || !isWishingWillowOffer(event.getMerchantOffer())) {
            return;
        }

        villager.getPersistentData().putBoolean(TRADE_COMPLETED_TAG, true);
        event.getMerchantOffer().setToOutOfStock();
    }

    private static boolean isMasterCleric(Villager villager) {
        VillagerData data = villager.getVillagerData();
        return data.getProfession() == VillagerProfession.CLERIC
                && data.getLevel() == VillagerData.MAX_VILLAGER_LEVEL;
    }

    private static void ensureWishingWillowOffer(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        int existingOfferIndex = findWishingWillowOffer(offers);
        boolean completed = villager.getPersistentData().getBoolean(TRADE_COMPLETED_TAG);

        if (existingOfferIndex >= 0) {
            MerchantOffer existingOffer = offers.get(existingOfferIndex);

            if (completed || existingOffer.getUses() > 0) {
                villager.getPersistentData().putBoolean(TRADE_COMPLETED_TAG, true);
                existingOffer.setToOutOfStock();
            } else if (existingOffer.getResult().is(ModItems.PACKAGED_WISHING_WILLOW.get())
                    && !(existingOffer instanceof OneTimeMerchantOffer)) {
                offers.set(existingOfferIndex, createWishingWillowOffer());
            }
            return;
        }

        if (!completed) {
            offers.add(createWishingWillowOffer());
        }
    }

    private static int findWishingWillowOffer(MerchantOffers offers) {
        for (int index = 0; index < offers.size(); index++) {
            if (isWishingWillowOffer(offers.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isWishingWillowOffer(MerchantOffer offer) {
        return offer.getResult().is(ModItems.WISHING_WILLOW.get())
                || offer.getResult().is(ModItems.PACKAGED_WISHING_WILLOW.get());
    }

    private static MerchantOffer createWishingWillowOffer() {
        return new OneTimeMerchantOffer(
                new ItemStack(Items.EMERALD, EMERALD_COST),
                new ItemStack(Items.GOLD_INGOT),
                new ItemStack(ModItems.PACKAGED_WISHING_WILLOW.get()),
                VILLAGER_XP,
                PRICE_MULTIPLIER
        );
    }
}
