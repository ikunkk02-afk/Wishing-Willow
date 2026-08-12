package com.ikunkk02.wishingwillow.payment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WishPaymentSavedData extends SavedData {
    private static final String DATA_NAME = "wishing_willow_payments";
    private static final String RECEIPTS = "Receipts";
    private final Map<UUID, WishPaymentReceipt> receipts = new HashMap<>();

    public static WishPaymentSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WishPaymentSavedData::load, WishPaymentSavedData::new, DATA_NAME);
    }

    public static WishPaymentSavedData load(CompoundTag tag) {
        WishPaymentSavedData data = new WishPaymentSavedData();
        for (Tag entry : tag.getList(RECEIPTS, Tag.TAG_COMPOUND)) {
            WishPaymentReceipt receipt = WishPaymentReceipt.load((CompoundTag) entry);
            data.receipts.putIfAbsent(receipt.sessionId(), receipt);
        }
        return data;
    }

    @Nullable
    public WishPaymentReceipt get(UUID sessionId) { return receipts.get(sessionId); }

    public List<WishPaymentReceipt> pendingFor(UUID playerId) {
        return receipts.values().stream()
                .filter(receipt -> receipt.playerId().equals(playerId) && receipt.pending())
                .sorted(Comparator.comparing(WishPaymentReceipt::sessionId))
                .toList();
    }

    void put(WishPaymentReceipt receipt) {
        receipts.put(receipt.sessionId(), receipt);
        setDirty();
    }

    void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag receiptsTag = new ListTag();
        receipts.values().stream()
                .sorted(Comparator.comparing(WishPaymentReceipt::sessionId))
                .map(WishPaymentReceipt::save)
                .forEach(receiptsTag::add);
        tag.put(RECEIPTS, receiptsTag);
        return tag;
    }
}
