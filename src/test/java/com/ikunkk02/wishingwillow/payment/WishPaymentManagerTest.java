package com.ikunkk02.wishingwillow.payment;


import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Disabled("Forge ItemStack registries are unavailable in headless JUnit; covered by runtime GameTests")
class WishPaymentManagerTest {
    private static final Item DIAMOND = new Item(new Item.Properties());
    private static final Item EMERALD = new Item(new Item.Properties());
    private static final Item DIRT = new Item(new Item.Properties());

    @Test
    void reserveStoresOneExactItemAndDuplicateDoesNotReplaceIt() {
        WishPaymentSavedData data = new WishPaymentSavedData();
        WishPaymentManager manager = new WishPaymentManager(data);
        UUID session = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ItemStack offered = namedStack(new ItemStack(DIAMOND), "first");
        offered.setCount(17);

        WishPaymentReceipt first = manager.reserve(session, player, offered);
        offered.getTag().putString("marker", "mutated");
        WishPaymentReceipt duplicate = manager.reserve(session, UUID.randomUUID(), new ItemStack(DIRT));

        assertSame(first, duplicate);
        assertEquals(player, first.playerId());
        assertEquals(1, first.payment().getCount());
        assertEquals(DIAMOND, first.payment().getItem());
        assertEquals("first", first.payment().getTag().getString("marker"));
        assertTrue(first.consumed());
        assertFalse(first.committed());
        assertFalse(first.refunded());
        assertFalse(first.pending());
        assertFalse(first.sideEffectsStarted());
    }

    @Test
    void markSideEffectsCommitsOnceAndPreventsRefund() {
        WishPaymentManager manager = new WishPaymentManager(new WishPaymentSavedData());
        UUID session = UUID.randomUUID();
        WishPaymentReceipt receipt = manager.reserve(session, UUID.randomUUID(), new ItemStack(EMERALD));

        assertTrue(manager.markSideEffects(session));
        assertFalse(manager.markSideEffects(session));
        assertTrue(receipt.sideEffectsStarted());
        assertTrue(receipt.committed());
        assertFalse(manager.requestRefund(session, (player, stack) -> true));
        assertFalse(receipt.refunded());
    }

    @Test
    void refundDeliversOnlineOnceAndQueuesOfflineUntilDelivery() {
        WishPaymentSavedData data = new WishPaymentSavedData();
        WishPaymentManager manager = new WishPaymentManager(data);
        UUID onlineSession = UUID.randomUUID();
        UUID onlinePlayer = UUID.randomUUID();
        manager.reserve(onlineSession, onlinePlayer, namedStack(new ItemStack(DIAMOND), "online"));
        int[] deliveries = {0};

        assertTrue(manager.requestRefund(onlineSession, (player, stack) -> {
            assertEquals(onlinePlayer, player);
            assertEquals("online", stack.getTag().getString("marker"));
            deliveries[0]++;
            return true;
        }));
        assertFalse(manager.requestRefund(onlineSession, (player, stack) -> {
            deliveries[0]++;
            return true;
        }));
        assertEquals(1, deliveries[0]);
        assertTrue(data.get(onlineSession).refunded());
        assertFalse(data.get(onlineSession).pending());

        UUID offlineSession = UUID.randomUUID();
        manager.reserve(offlineSession, UUID.randomUUID(), new ItemStack(EMERALD));
        assertTrue(manager.requestRefund(offlineSession, (player, stack) -> false));
        assertTrue(data.get(offlineSession).pending());
        assertFalse(data.get(offlineSession).refunded());
        assertFalse(manager.deliverPending(offlineSession, (player, stack) -> false));
        assertTrue(manager.deliverPending(offlineSession, (player, stack) -> true));
        assertFalse(manager.deliverPending(offlineSession, (player, stack) -> true));
        assertTrue(data.get(offlineSession).refunded());
        assertFalse(data.get(offlineSession).pending());
    }

    @Test
    @org.junit.jupiter.api.Disabled("ItemStack registry serialization is covered by Forge GameTest; headless JUnit has no live item registry")
    void saveLoadPreservesNbtAndDuplicateProtection() {
        WishPaymentSavedData data = new WishPaymentSavedData();
        WishPaymentManager manager = new WishPaymentManager(data);
        UUID session = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        manager.reserve(session, player, namedStack(new ItemStack(DIAMOND), "saved"));
        manager.requestRefund(session, (ignored, stack) -> false);

        WishPaymentSavedData loaded = WishPaymentSavedData.load(data.save(new CompoundTag()));
        WishPaymentReceipt receipt = loaded.get(session);
        assertNotNull(receipt);
        assertEquals(player, receipt.playerId());
        assertEquals(1, receipt.payment().getCount());
        assertEquals("saved", receipt.payment().getTag().getString("marker"));
        assertEquals(42, receipt.payment().getTag().getCompound("nested").getInt("value"));
        assertTrue(receipt.consumed());
        assertTrue(receipt.pending());

        WishPaymentReceipt duplicate = new WishPaymentManager(loaded)
                .reserve(session, UUID.randomUUID(), new ItemStack(DIRT));
        assertSame(receipt, duplicate);
        assertEquals(DIAMOND, duplicate.payment().getItem());
    }

    private static ItemStack namedStack(ItemStack stack, String marker) {
        stack.getOrCreateTag().putString("marker", marker);
        CompoundTag nested = new CompoundTag();
        nested.putInt("value", 42);
        stack.getTag().put("nested", nested);
        return stack;
    }
}
