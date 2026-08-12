package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.program.ValidatedWishProgram;
import com.ikunkk02.wishingwillow.program.WishProgram;
import com.ikunkk02.wishingwillow.program.WishProgramAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WishDecisionPolicyTest {
    private final WishDecisionPolicy policy = new WishDecisionPolicy();

    @Test
    void acceptsValidatedGiveItemProgram() {
        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.ACCEPT, null,
                validated(action("give_item", "count", 64)));

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_ACCEPTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.ACCEPTED, result.code());
    }

    @Test
    void acceptsValidatedFollowerProgram() {
        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.ACCEPT, null,
                validated(action("follow_player", "max_entities", 8)));

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_ACCEPTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.ACCEPTED, result.code());
    }

    @Test
    void acceptsValidatedEntitySuppressionProgram() {
        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.ACCEPT, null,
                validated(action("entity_suppression", "max_count", 16)));

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_ACCEPTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.ACCEPTED, result.code());
    }

    @Test
    void preservesAiRejectionAsARejectedFinalDecision() {
        WishRejection rejection = new WishRejection(WishRejectionCode.EXTERNAL_SYSTEM_ACCESS,
                "That wish cannot be granted.", "Requires an external system.");

        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.REJECT, rejection, null);

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_REJECTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.AI_REJECTED, result.code());
        assertEquals(WishRejectionCode.EXTERNAL_SYSTEM_ACCESS, result.rejectionCode());
    }

    @Test
    void rejectsResourceAbusiveAcceptedSpawnProgram() {
        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.ACCEPT, null,
                validated(action("spawn_entity", "count", 65)));

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_REJECTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.RESOURCE_ABUSE, result.code());
        assertEquals(WishRejectionCode.RESOURCE_ABUSE, result.rejectionCode());
    }

    @Test
    void rejectsResourceAbusiveAcceptedFollowerProgram() {
        WishDecisionPolicy.Result result = policy.evaluate(WishDecision.ACCEPT, null,
                validated(action("follow_player", "duration_seconds", Integer.MAX_VALUE)));

        assertEquals(WishDecisionPolicy.FinalDecision.FINAL_REJECTED, result.decision());
        assertEquals(WishDecisionPolicy.Code.RESOURCE_ABUSE, result.code());
    }

    private static WishProgramAction action(String id, String parameter, int value) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty(parameter, value);
        return new WishProgramAction(id, parameters);
    }

    private static ValidatedWishProgram validated(WishProgramAction action) {
        WishProgram program = new WishProgram(WishProgram.CURRENT_SCHEMA_VERSION, "test wish",
                List.of(action), List.of(), "", "");
        return new ValidatedWishProgram(program, List.of(), List.of());
    }
}
