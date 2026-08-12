package com.ikunkk02.wishingwillow.ai;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishDecisionProtocolTest {
    private static final String INTERPRETATION = """
            {"schema_version":2,"intent":"wealth","literal_goal":"Give ten diamonds",
             "contract":{"type":"OBTAIN_RESOURCE","required_outcome":"The player obtains ten diamonds","hard_constraints":[
               {"kind":"RESOURCE_KIND","operator":"EQUALS","semantic":"item","quantity":0,"amount":0,"required":true},
               {"kind":"MINIMUM_QUANTITY","operator":"AT_LEAST","semantic":"","quantity":10,"amount":0,"required":true}]},
             "fulfillment":{"mode":"CLASSIC","method":"Give ten diamonds","styles":["LITERAL"],"absurdity":0},
             "reasoning_summary":"Supported by give_item","tone":"NEUTRAL","severity":0,
             "delivery":"IMMEDIATE","required_capabilities":["GIVE_ITEM"]}
            """;

    @Test
    void acceptsACompleteProgramDecision() {
        WishUnderstandingJson.Understanding result = WishUnderstandingJson.parse("""
                {"decision":"ACCEPT","rejection_code":"NONE","player_message":"","reason":"",
                 "interpretation":%s,
                 "program":{"schema_version":1,"goal":"Give ten diamonds","core_actions":[
                   {"action":"give_item","parameters":{"item":"minecraft:diamond","count":10}}],
                   "presentation_actions":[],"skill":"","unknown_capability":""}}
                """.formatted(INTERPRETATION));

        assertEquals(WishDecision.ACCEPT, result.decision());
        assertTrue(result.accepted());
        assertNull(result.rejection());
        assertEquals("give_item", result.program().coreActions().get(0).action());
    }

    @Test
    void rejectIsAValidTerminalSemanticResultWithoutProgram() {
        WishUnderstandingJson.Understanding result = WishUnderstandingJson.parse("""
                {"decision":"REJECT","rejection_code":"EXTERNAL_SYSTEM_ACCESS",
                 "player_message":"This wish reaches beyond the world.",
                 "reason":"The request asks the game to run cmd.",
                 "interpretation":null,"program":null}
                """);

        assertEquals(WishDecision.REJECT, result.decision());
        assertFalse(result.accepted());
        assertEquals(WishRejectionCode.EXTERNAL_SYSTEM_ACCESS, result.rejection().code());
        assertEquals("This wish reaches beyond the world.", result.rejection().playerMessage());
        assertNull(result.interpretation());
        assertNull(result.program());
    }

    @Test
    void rejectCannotSmuggleActionsAndUnknownCodesRemainInvalid() {
        assertThrows(IllegalArgumentException.class, () -> WishUnderstandingJson.parse("""
                {"decision":"REJECT","rejection_code":"MADE_UP","player_message":"No","reason":"No",
                 "interpretation":null,"program":null}
                """));
        assertThrows(IllegalArgumentException.class, () -> WishUnderstandingJson.parse("""
                {"decision":"REJECT","rejection_code":"RESOURCE_ABUSE","player_message":"No","reason":"No",
                 "interpretation":%s,
                 "program":{"schema_version":1,"goal":"bad","core_actions":[],"presentation_actions":[],"skill":"","unknown_capability":""}}
                """.formatted(INTERPRETATION)));
    }

    @Test
    void schemaDynamicallyListsDecisionAndRejectionEnums() {
        JsonObject schema = WishUnderstandingJson.jsonSchema();
        String json = schema.toString();
        assertTrue(json.contains("\"ACCEPT\""));
        assertTrue(json.contains("\"REJECT\""));
        assertTrue(json.contains("\"RESOURCE_ABUSE\""));
        assertTrue(json.contains("\"UNSUPPORTED_CAPABILITY\""));
    }

    @Test
    void playerMessageIsSanitizedAndBounded() {
        String raw = "internal JSON API HTTP Validator exception ".repeat(20);
        WishRejection rejection = WishRejection.sanitized(
                WishRejectionCode.UNSUPPORTED_CAPABILITY, raw, "debug reason");
        assertTrue(rejection.playerMessage().length() <= WishRejection.MAX_PLAYER_MESSAGE_LENGTH);
        assertFalse(rejection.playerMessage().toLowerCase().contains("json"));
        assertFalse(rejection.playerMessage().toLowerCase().contains("api"));
    }
}
