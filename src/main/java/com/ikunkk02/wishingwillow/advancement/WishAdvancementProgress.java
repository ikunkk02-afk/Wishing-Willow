package com.ikunkk02.wishingwillow.advancement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class WishAdvancementProgress {
    private int totalWishesSubmitted;
    private int successfulWishes;
    private int absurdWishes;
    private int persistentWishes;
    private int largestSuccessfulActionCount;
    private int dangerousWishes;
    private int negativeWishes;
    private int catastrophicWishes;
    private final Set<UUID> submittedSessions = new LinkedHashSet<>();
    private final Set<UUID> successfulSessions = new LinkedHashSet<>();

    public boolean recordSubmitted(UUID sessionId) {
        if (sessionId == null || !submittedSessions.add(sessionId)) return false;
        totalWishesSubmitted++;
        return true;
    }

    public boolean recordSuccess(UUID sessionId, WishOutcomeSummary outcome) {
        if (sessionId == null || outcome == null || outcome.successfulActionCount() < 1
                || !successfulSessions.add(sessionId)) return false;
        successfulWishes++;
        if (outcome.absurd()) absurdWishes++;
        if (outcome.persistent()) persistentWishes++;
        if (outcome.dangerous()) dangerousWishes++;
        if (outcome.negative()) negativeWishes++;
        if (outcome.severity() == WishSeverity.CATASTROPHIC) catastrophicWishes++;
        largestSuccessfulActionCount = Math.max(largestSuccessfulActionCount,
                outcome.successfulActionCount());
        return true;
    }

    public int totalWishesSubmitted() { return totalWishesSubmitted; }
    public int successfulWishes() { return successfulWishes; }
    public int absurdWishes() { return absurdWishes; }
    public int persistentWishes() { return persistentWishes; }
    public int largestSuccessfulActionCount() { return largestSuccessfulActionCount; }
    public int dangerousWishes() { return dangerousWishes; }
    public int negativeWishes() { return negativeWishes; }
    public int catastrophicWishes() { return catastrophicWishes; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TotalWishesSubmitted", totalWishesSubmitted);
        tag.putInt("SuccessfulWishes", successfulWishes);
        tag.putInt("AbsurdWishes", absurdWishes);
        tag.putInt("PersistentWishes", persistentWishes);
        tag.putInt("LargestSuccessfulActionCount", largestSuccessfulActionCount);
        tag.putInt("DangerousWishes", dangerousWishes);
        tag.putInt("NegativeWishes", negativeWishes);
        tag.putInt("CatastrophicWishes", catastrophicWishes);
        tag.put("SubmittedSessions", saveSessions(submittedSessions));
        tag.put("SuccessfulSessions", saveSessions(successfulSessions));
        return tag;
    }

    public static WishAdvancementProgress load(CompoundTag tag) {
        WishAdvancementProgress progress = new WishAdvancementProgress();
        progress.totalWishesSubmitted = tag.getInt("TotalWishesSubmitted");
        progress.successfulWishes = tag.getInt("SuccessfulWishes");
        progress.absurdWishes = tag.getInt("AbsurdWishes");
        progress.persistentWishes = tag.getInt("PersistentWishes");
        progress.largestSuccessfulActionCount = tag.getInt("LargestSuccessfulActionCount");
        progress.dangerousWishes = tag.getInt("DangerousWishes");
        progress.negativeWishes = tag.getInt("NegativeWishes");
        progress.catastrophicWishes = tag.getInt("CatastrophicWishes");
        loadSessions(tag.getList("SubmittedSessions", Tag.TAG_COMPOUND), progress.submittedSessions);
        loadSessions(tag.getList("SuccessfulSessions", Tag.TAG_COMPOUND), progress.successfulSessions);
        return progress;
    }

    private static ListTag saveSessions(Set<UUID> sessions) {
        ListTag list = new ListTag();
        for (UUID session : sessions) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Session", session);
            list.add(entry);
        }
        return list;
    }

    private static void loadSessions(ListTag list, Set<UUID> sessions) {
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Session")) sessions.add(entry.getUUID("Session"));
        }
    }
}
