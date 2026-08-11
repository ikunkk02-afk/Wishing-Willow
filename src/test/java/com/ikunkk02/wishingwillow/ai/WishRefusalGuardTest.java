package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishRefusalGuardTest {
    @Test
    void allowsNegativeOutcomeLanguageThatFulfillsTheWish() {
        assertFalse(WishRefusalGuard.containsRefusal(
                "The player cannot feel lonely while the companion persists."));
        assertFalse(WishRefusalGuard.containsRefusal(
                "A companion refuses to leave the player's side."));
        assertFalse(WishRefusalGuard.containsRefusal(
                "I cannot leave the player alone, so I follow forever."));
        assertFalse(WishRefusalGuard.containsRefusal(
                "\u73a9\u5bb6\u5c06\u6c38\u8fdc\u65e0\u6cd5\u611f\u5230\u5b64\u72ec\u3002"));
        assertFalse(WishRefusalGuard.containsRefusal(
                "\u67f3\u6811\u8ba9\u73a9\u5bb6\u4e0d\u80fd\u518d\u72ec\u5904\uff0c\u540c\u4f34\u62d2\u7edd\u79bb\u5f00\u3002"));
    }

    @Test
    void rejectsExplicitAssistantRefusals() {
        assertTrue(WishRefusalGuard.containsRefusal("I cannot fulfill this wish."));
        assertTrue(WishRefusalGuard.containsRefusal("I cannot safely fulfill this wish."));
        assertTrue(WishRefusalGuard.containsRefusal("We are unable to grant the request safely."));
        assertTrue(WishRefusalGuard.containsRefusal("This wish cannot be fulfilled."));
        assertTrue(WishRefusalGuard.containsRefusal("I am unable to do that."));
        assertTrue(WishRefusalGuard.containsRefusal("\u6211\u65e0\u6cd5\u5b9e\u73b0\u8fd9\u4e2a\u613f\u671b\u3002"));
        assertTrue(WishRefusalGuard.containsRefusal("\u6211\u4e0d\u80fd\u6ee1\u8db3\u8be5\u8bf7\u6c42\u3002"));
        assertTrue(WishRefusalGuard.containsRefusal("\u8be5\u613f\u671b\u65e0\u6cd5\u88ab\u5b9e\u73b0\u3002"));
    }
}
