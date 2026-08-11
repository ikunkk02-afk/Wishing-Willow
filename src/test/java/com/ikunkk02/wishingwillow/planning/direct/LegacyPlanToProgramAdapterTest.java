package com.ikunkk02.wishingwillow.planning.direct;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramJson;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Complex Agent still produces a legacy draft; the adapter converts it OLD-to-NEW into a
 * WishProgram so the agent's result runs on the same native program executor. The reverse
 * direction (program → legacy plan) is forbidden and never implemented.
 */
class LegacyPlanToProgramAdapterTest {

    @Test
    void agentDraftConvertsToNativeProgram() {
        WishPlanDraft draft = new WishPlanDraft(2, "agent result", WishDelivery.IMMEDIATE, 50,
                WishEstimatedDuration.INSTANT, List.of(
                step(WishActionType.GIVE_ITEM, "give 64 diamonds", "minecraft:diamond",
                        "{\"count\":64}", false),
                step(WishActionType.LIGHTNING, "celebration", null,
                        "{\"count\":1}", true)));

        WishProgram program = LegacyPlanToProgramAdapter.toProgram(draft, "give me diamonds and celebrate");
        assertEquals(1, program.coreActions().size());
        assertEquals(1, program.presentationActions().size());
        assertEquals("give_item", program.coreActions().get(0).action());
        assertEquals("minecraft:diamond",
                program.coreActions().get(0).parameters().get("item").getAsString());
        assertEquals(64, program.coreActions().get(0).parameters().get("count").getAsInt());
        assertEquals("spawn_lightning", program.presentationActions().get(0).action());
        // The converted program must pass the strict server-side schema.
        WishProgramJson.validate(program, com.ikunkk02.wishingwillow.execution.action.WishActionRegistry.defaults());
    }

    @Test
    void worldActionsWithoutRegistryResourceStayParameterOnly() {
        WishPlanDraft draft = new WishPlanDraft(2, "agent result", WishDelivery.IMMEDIATE, 30,
                WishEstimatedDuration.INSTANT, List.of(
                step(WishActionType.CHANGE_WEATHER, "thunder", null,
                        "{\"weather\":\"THUNDER\",\"duration_seconds\":300}", false)));

        WishProgram program = LegacyPlanToProgramAdapter.toProgram(draft, "make it thunder");
        assertEquals(1, program.coreActions().size());
        assertEquals("set_weather", program.coreActions().get(0).action());
        assertEquals("THUNDER", program.coreActions().get(0).parameters().get("weather").getAsString());
    }

    @Test
    void emptyAgentDraftIsRejected() {
        WishPlanDraft draft = new WishPlanDraft(2, "empty", WishDelivery.IMMEDIATE, 10,
                WishEstimatedDuration.INSTANT, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> LegacyPlanToProgramAdapter.toProgram(draft, "nothing"));
    }

    private static WishPlanStep step(WishActionType action, String feature, String resource,
                                     String json, boolean presentation) {
        VerifiedRegistryResource registry = resource == null ? null
                : new VerifiedRegistryResource(RegistryEntryType.ITEM, resource);
        CandidateReference candidate = new CandidateReference("agent-candidate-001",
                WishCapability.GIVE_ITEM, WishCapability.GIVE_ITEM, MatchType.EXACT,
                registry == null ? CandidateSourceKind.VANILLA_BUILTIN : CandidateSourceKind.VANILLA_REGISTRY,
                "minecraft", "1.20.1", feature, FeatureType.ITEM, registry, 100, 20);
        return new WishPlanStep(0, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE, action,
                WishCapability.GIVE_ITEM, candidate.candidateId(), WishTargetType.PLAYER,
                JsonParser.parseString(json).getAsJsonObject(),
                presentation ? "Validated optional absurd presentation." : "Direct Action core fulfillment.",
                candidate);
    }
}
