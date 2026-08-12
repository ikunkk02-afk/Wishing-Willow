package com.ikunkk02.wishingwillow.advancement;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.contract.WishContract;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.execution.WishStepExecutionState;
import com.ikunkk02.wishingwillow.program.ProgramAction;
import com.ikunkk02.wishingwillow.program.WishProgram;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WishOutcomeClassifierTest {
    @Test
    void countsOnlyActuallySucceededActions() {
        WishExecutionRecord record = record(12, 12);
        for (int i = 0; i < 9; i++) record.step(i).transition(WishStepExecutionState.SUCCEEDED, 1);
        for (int i = 9; i < 12; i++) record.step(i).transition(WishStepExecutionState.FAILED, 1);
        List<ProgramAction> actions = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new ProgramAction("play_sound", new JsonObject(),
                        false, index, 0, null, null, null, index))
                .toList();

        WishOutcomeSummary outcome = WishOutcomeClassifier.classify(record,
                program("", actions), interpretation(20, WishTone.NEUTRAL));

        assertEquals(9, outcome.successfulActionCount());
    }

    @Test
    void absurdRequiresExecutedProgramSkillNotSelectedSkillMetadataElsewhere() {
        WishExecutionRecord record = record(1, 1);
        record.step(0).transition(WishStepExecutionState.SUCCEEDED, 1);

        assertTrue(WishOutcomeClassifier.classify(record,
                program("absurd_wish_realization", List.of()), interpretation(40, WishTone.ABSURD)).absurd());
        assertFalse(WishOutcomeClassifier.classify(record,
                program("", List.of()), interpretation(40, WishTone.ABSURD)).absurd());
    }

    @Test
    void recognizesSuccessfulPersistentRegistrationFromActionMetadata() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("permanent", true);
        ProgramAction action = new ProgramAction("entity_attraction_aura", parameters,
                false, 0, 0, null, null, null, 0);
        WishExecutionRecord record = record(1, 1);
        record.step(0).transition(WishStepExecutionState.SUCCEEDED, 1);

        WishOutcomeSummary outcome = WishOutcomeClassifier.classify(record,
                program("absurd_wish_realization", List.of(action)), interpretation(60, WishTone.ABSURD));

        assertTrue(outcome.persistent());
    }

    @Test
    void derivesNegativeAndCatastrophicLocallyWithoutAnotherAiRequest() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("power", 8);
        parameters.addProperty("destroy_blocks", true);
        ProgramAction action = new ProgramAction("create_explosion", parameters,
                false, 0, 0, null, null, null, 0);
        WishExecutionRecord record = record(1, 1);
        record.step(0).transition(WishStepExecutionState.SUCCEEDED, 1);

        WishOutcomeSummary outcome = WishOutcomeClassifier.classify(record,
                program("absurd_wish_realization", List.of(action)), interpretation(92, WishTone.HORROR));

        assertTrue(outcome.negative());
        assertEquals(WishSeverity.CATASTROPHIC, outcome.severity());
        assertTrue(outcome.dangerous());
    }

    private static WishExecutionRecord record(int steps, int core) {
        return new WishExecutionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), steps, 0,
                com.ikunkk02.wishingwillow.execution.ExecutionSource.WISH_PROGRAM, core);
    }

    private static WishProgram program(String skill, List<ProgramAction> actions) {
        return WishOutcomeClassifier.testingProgram(skill, actions);
    }

    private static WishInterpretation interpretation(int severity, WishTone tone) {
        return new WishInterpretation(2, "test", "test", WishContract.legacy("test"),
                new WishFulfillment(WishFulfillmentMode.ABSURD, "test", List.of(FulfillmentStyle.IRONIC), severity),
                "test", tone, severity, WishDelivery.IMMEDIATE, List.of(WishCapability.WORLD_EVENT));
    }
}
