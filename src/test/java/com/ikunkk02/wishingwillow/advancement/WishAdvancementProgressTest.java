package com.ikunkk02.wishingwillow.advancement;

import com.ikunkk02.wishingwillow.ai.WishTone;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WishAdvancementProgressTest {
    @Test
    void oneSessionCountsSubmissionAndSuccessOnlyOnceEvenAfterRepairRetries() {
        WishAdvancementProgress progress = new WishAdvancementProgress();
        UUID session = UUID.randomUUID();

        assertTrue(progress.recordSubmitted(session));
        assertFalse(progress.recordSubmitted(session));
        assertTrue(progress.recordSuccess(session, WishOutcomeSummary.normal(1)));
        assertFalse(progress.recordSuccess(session, WishOutcomeSummary.normal(12)));

        assertEquals(1, progress.totalWishesSubmitted());
        assertEquals(1, progress.successfulWishes());
        assertEquals(1, progress.largestSuccessfulActionCount());
    }

    @Test
    void countersRoundTripAcrossWorldReload() {
        WishAdvancementProgress progress = new WishAdvancementProgress();
        progress.recordSubmitted(UUID.randomUUID());
        progress.recordSuccess(UUID.randomUUID(), new WishOutcomeSummary(12, true, true, true,
                WishSeverity.CATASTROPHIC, WishTone.HORROR));

        WishAdvancementProgress loaded = WishAdvancementProgress.load(progress.save());

        assertEquals(1, loaded.totalWishesSubmitted());
        assertEquals(1, loaded.successfulWishes());
        assertEquals(1, loaded.absurdWishes());
        assertEquals(1, loaded.persistentWishes());
        assertEquals(12, loaded.largestSuccessfulActionCount());
        assertEquals(1, loaded.dangerousWishes());
    }

    @Test
    void differentPlayersAndSessionsRemainIndependent() {
        WishAdvancementSavedData data = new WishAdvancementSavedData();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), session = UUID.randomUUID();

        data.progress(a).recordSubmitted(session);
        data.progress(a).recordSuccess(session, WishOutcomeSummary.normal(1));
        data.progress(b).recordSubmitted(UUID.randomUUID());

        assertEquals(1, data.progress(a).successfulWishes());
        assertEquals(0, data.progress(b).successfulWishes());
    }

    @Test
    void savedDataRoundTripsPlayersAndSessionGuards() {
        WishAdvancementSavedData data = new WishAdvancementSavedData();
        UUID player = UUID.randomUUID(), session = UUID.randomUUID();
        data.progress(player).recordSubmitted(session);
        data.progress(player).recordSuccess(session, WishOutcomeSummary.normal(3));

        CompoundTag saved = data.save(new CompoundTag());
        WishAdvancementSavedData loaded = WishAdvancementSavedData.load(saved);

        assertFalse(loaded.progress(player).recordSubmitted(session));
        assertFalse(loaded.progress(player).recordSuccess(session, WishOutcomeSummary.normal(4)));
        assertEquals(3, loaded.progress(player).largestSuccessfulActionCount());
    }
}
