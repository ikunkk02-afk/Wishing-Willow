package com.ikunkk02.wishingwillow.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NeverAloneRuleTest {
    @Test
    void persistentRuleRoundTripsItsBoundedCompanionSettings() {
        UUID owner = UUID.randomUUID();
        NeverAloneSavedData.NeverAloneRule rule = new NeverAloneSavedData.NeverAloneRule(
                owner, "session", 24, 12, 24, 128, 40, 100, 1.5, 6, true, 42);

        NeverAloneSavedData.NeverAloneRule loaded =
                NeverAloneSavedData.NeverAloneRule.fromNbt(rule.toNbt());

        assertNotNull(loaded);
        assertEquals(owner, loaded.ownerId());
        assertEquals(12, loaded.minimumCompanions());
        assertEquals(24, loaded.targetCompanions());
        assertEquals(40, loaded.teleportDistance());
        assertTrue(loaded.permanent());
    }
}