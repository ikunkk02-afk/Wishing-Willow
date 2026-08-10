package com.ikunkk02.wishingwillow.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WishInterpretationValidatorTest {
    private static final String VALID = """
            {
              "schema_version":1,
              "intent":"companionship",
              "literal_goal":"The player wants company wherever they go",
              "loophole":"The player did not specify who or whether they are friendly",
              "twisted_outcome":"An unwelcome presence follows the player",
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
                VALID.replace("\"schema_version\":1,", "\"schema_version\":1,\"extra\":true,")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"STALKING_ENTITY\",\"PERSISTENT_FOLLOWER\"", "\"STALKING_ENTITY\",\"STALKING_ENTITY\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> WishInterpretationValidator.parseAndValidate(
                VALID.replace("\"severity\":72", "\"severity\":72.5")
        ));
    }
}
