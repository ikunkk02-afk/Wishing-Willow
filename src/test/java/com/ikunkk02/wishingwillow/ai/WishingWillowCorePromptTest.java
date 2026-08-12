package com.ikunkk02.wishingwillow.ai;

import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowCorePrompt;
import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowPromptAssembler;
import com.ikunkk02.wishingwillow.ai.prompt.WishingWillowRuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WishingWillowCorePromptTest {
    @Test
    void coreIdentityDefinesRuleChangingSemanticFulfillment() {
        String core = WishingWillowCorePrompt.TEXT;
        assertEquals(1, WishingWillowCorePrompt.CORE_PROMPT_VERSION);
        assertTrue(core.contains("You are the Wishing Willow"));
        assertTrue(core.contains("not a general-purpose chatbot"));
        assertTrue(core.contains("DO NOT default to minimum technically-valid fulfillment"));
        assertTrue(core.contains("Absurdity is semantic escalation, not randomness"));
        assertTrue(core.contains("Explicit player constraints override creative escalation"));
        assertTrue(core.contains("permanent=true"));
        assertTrue(core.contains("Never invent unsupported action IDs"));
        assertTrue(core.contains("Gameplay consequences outrank presentation"));
        assertTrue(core.contains("RESEARCH_DEPENDENT"));
    }

    @Test
    void assemblerKeepsWishOutOfSystemAndOrdersBoundedSections() {
        WishingWillowRuntimeContext context = new WishingWillowRuntimeContext(
                "1.20.1", "Forge 47.4.22", "minecraft:overworld", "player-uuid",
                "10,64,10", "unknown", "normal", List.of("create", "cavedweller"),
                "items=10,entities=5", true, "server policy and entity caps");
        var assembled = WishingWillowPromptAssembler.assemble(
                WishingWillowPromptAssembler.RequestKind.INTERPRETATION, context,
                "give_item: inventory reward", "absurd_wish_realization: semantic escalation",
                "validator and server policy remain authoritative", "strict JSON schema",
                "secret player wish", null);

        String system = assembled.systemMessage();
        assertFalse(system.contains("secret player wish"));
        assertTrue(assembled.userMessage().contains("secret player wish"));
        assertTrue(system.indexOf("[CORE]") < system.indexOf("[WORLD CONTEXT]"));
        assertTrue(system.indexOf("[WORLD CONTEXT]") < system.indexOf("[CAPABILITIES]"));
        assertTrue(system.indexOf("[CAPABILITIES]") < system.indexOf("[SKILLS]"));
        assertTrue(system.indexOf("[SKILLS]") < system.indexOf("[EXECUTION CONSTRAINTS]"));
        assertTrue(system.indexOf("[EXECUTION CONSTRAINTS]") < system.indexOf("[OUTPUT CONTRACT]"));
        assertEquals(List.of("CORE", "WORLD", "ACTIONS", "SKILLS", "WISH", "SCHEMA"),
                assembled.sections());
    }

    @Test
    void behavioralGuidanceSeparatesSimplePermanentAndExplicitlyConstrainedWishes() {
        String core = WishingWillowCorePrompt.TEXT;
        assertTrue(core.contains("Give me one diamond"));
        assertTrue(core.contains("simple OBJECT"));
        assertTrue(core.contains("single companion"));
        assertTrue(core.contains("entity_attraction_aura"));
        assertTrue(core.contains("luckiest person"));
        assertTrue(core.contains("systemic"));
        assertTrue(core.contains("only give me one diamond"));
    }
}