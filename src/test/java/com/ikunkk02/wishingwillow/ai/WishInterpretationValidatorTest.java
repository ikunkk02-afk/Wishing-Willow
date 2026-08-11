package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WishInterpretationValidatorTest {
    private static final String VALID = """
            {
              "schema_version":2,
              "intent":"companionship",
              "literal_goal":"The player wants company wherever they go",
              "contract":{"type":"SPAWN_COMPANION","required_outcome":"A real persistent companion must remain with the player","hard_constraints":[
                {"kind":"COMPANION_EXISTS","operator":"REQUIRED","semantic":"","quantity":0,"amount":0,"required":true},
                {"kind":"PERSISTENCE","operator":"REQUIRED","semantic":"persistent","quantity":0,"amount":0,"required":true}]},
              "fulfillment":{"mode":"ABSURD","method":"An unwelcome presence follows the player","styles":["HORROR","SOCIAL_ABSURDITY"],"absurdity":78},
              "reasoning_summary":"Companionship is granted through an unspecified follower",
              "tone":"HORROR",
              "severity":72,
              "delivery":"DELAYED",
              "required_capabilities":["STALKING_ENTITY","PERSISTENT_FOLLOWER"]
            }
            """;

    @Test
    void parsesStrictInterpretationAndMarkdownFence() {
        WishInterpretation direct = WishInterpretationValidator.parseAndValidate(VALID);
        WishInterpretation fenced = WishInterpretationValidator.parseAndValidate("```json\n" + VALID + "\n```");
        assertEquals(72, direct.severity());
        assertEquals(direct, fenced);
        assertEquals("^[a-z][a-z0-9_-]{0,63}$",
                WishInterpretationValidator.jsonSchema()
                        .getAsJsonObject("properties")
                        .getAsJsonObject("intent")
                        .get("pattern")
                        .getAsString());
    }

    @Test
    void rejectsOutOfRangeSeverityUnknownCapabilityAndTrailingText() {
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"severity\":72", "\"severity\":200")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("STALKING_ENTITY", "SPAWN_SUPER_HEROBRINE")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(VALID + " ignored"));
    }

    @Test
    void rejectsExtraFieldsDuplicateCapabilitiesAndFractionalIntegers() {
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"schema_version\":2,", "\"schema_version\":2,\"extra\":true,")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"STALKING_ENTITY\",\"PERSISTENT_FOLLOWER\"", "\"STALKING_ENTITY\",\"STALKING_ENTITY\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"severity\":72", "\"severity\":72.5")
        ));
    }

    @Test
    void providerBoundaryNormalizesOnlyTheIntentMachineLabel() {
        WishInterpretation normalized = WishInterpretationValidator.parseProviderResponse(
                VALID.replace("\"intent\":\"companionship\"", "\"intent\":\"Obtain Diamonds\"")
        );
        assertEquals("obtain_diamonds", normalized.intent());

        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseProviderResponse(
                VALID.replace("\"tone\":\"HORROR\"", "\"tone\":\"MYSTERY\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseProviderResponse(
                VALID.replace("\"intent\":\"companionship\"", "\"intent\":\"陪伴\"")
        ));
    }

    @Test
    void reportsAFieldLevelSchemaReasonWithoutEchoingTheCandidate() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishInterpretationValidator.parseAndValidate(
                        VALID.replace("\"tone\":\"HORROR\"", "\"tone\":\"MYSTERY\"")));

        assertEquals("MALFORMED_RESPONSE:ENUM_tone", error.getMessage());
    }
}
