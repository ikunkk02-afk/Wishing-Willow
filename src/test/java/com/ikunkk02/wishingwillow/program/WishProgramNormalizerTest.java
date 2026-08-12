package com.ikunkk02.wishingwillow.program;

import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishProgramNormalizerTest {
    @Test
    void treatsSkillMetadataAsNonBlockingAcrossCommonLlmShapes() {
        WishProgramNormalizationResult stringSkill = programWithMetadata(
                "\"skill\":\"absurd_wish_realization\"");
        assertEquals("absurd_wish_realization", stringSkill.requireProgram().skill());

        WishProgramNormalizationResult objectSkill = programWithMetadata(
                "\"skill\":{\"id\":\"absurd_wish_realization\",\"description\":\"metadata\"}");
        assertEquals(WishProgramValidationStatus.REPAIRABLE, objectSkill.status());
        assertEquals("absurd_wish_realization", objectSkill.requireProgram().skill());

        WishProgramNormalizationResult arraySkills = programWithMetadata(
                "\"skills\":[\"absurd_wish_realization\"]");
        assertEquals("absurd_wish_realization", arraySkills.requireProgram().skill());

        WishProgramNormalizationResult missingSkill = programWithMetadata("");
        assertEquals("", missingSkill.requireProgram().skill());
        strict(stringSkill);
        strict(objectSkill);
        strict(arraySkills);
        strict(missingSkill);
    }

    @Test
    void acceptsRootActionsAsOneObjectAndIgnoresBrokenMetadata() {
        WishProgramNormalizationResult result = WishProgramNormalizer.normalize("""
                {"schema_version":"1","goal":"never alone",
                 "actions":{"action":"entity_attraction_aura","parameters":{"permanent":"true"}},
                 "skill":42,"reasoning":{"copied":"by model"},"summary":["extra"]}
                """);

        assertEquals(WishProgramValidationStatus.REPAIRABLE, result.status());
        assertEquals(1, result.requireProgram().coreActions().size());
        assertEquals("entity_attraction_aura", first(result).action());
        assertTrue(first(result).parameters().get("permanent").getAsBoolean());
        assertEquals("", result.requireProgram().skill());
        strict(result);
    }

    @Test
    void actionsRemainExecutableWhenSkillMetadataIsUnknownOrIncompatible() {
        WishProgramNormalizationResult unknown = programWithMetadata("\"skill\":\"invented_skill\"");
        assertEquals("", unknown.requireProgram().skill());

        WishProgramNormalizationResult incompatible = programWithMetadata("\"skill\":\"block_rain\"");
        assertEquals("", incompatible.requireProgram().skill());
        assertEquals("entity_attraction_aura", first(incompatible).action());
        strict(unknown);
        strict(incompatible);
    }

    @Test
    void clampsFollowPlayerMaxEntitiesBeforeStrictValidation() {
        WishProgramNormalizationResult result = normalize("""
                {"action":"follow_player","parameters":{"max_entities":100}}
                """);

        assertEquals(WishProgramValidationStatus.REPAIRABLE, result.status());
        assertEquals(32, first(result).parameters().get("max_entities").getAsInt());
        assertEquals(16, first(result).parameters().get("radius").getAsInt());
        assertEquals(600, first(result).parameters().get("duration_seconds").getAsInt());
        assertTrue(result.changes().stream().anyMatch(change ->
                change.parameter().equals("max_entities")
                        && change.reason() == WishNormalizationReason.MAX_CLAMP));
        strict(result);
    }

    @Test
    void coercesNumericAndBooleanStringsWithoutAiRepair() {
        WishProgramNormalizationResult count = normalize("""
                {"action":"follow_player","parameters":{"max_entities":"10"}}
                """);
        assertEquals(10, first(count).parameters().get("max_entities").getAsInt());
        assertTrue(first(count).parameters().get("max_entities").getAsJsonPrimitive().isNumber());

        WishProgramNormalizationResult number = normalize("""
                {"action":"modify_health","parameters":{"delta":"1.5","allow_lethal":"false"}}
                """);
        assertEquals(1.5, first(number).parameters().get("delta").getAsDouble());
        assertEquals(false, first(number).parameters().get("allow_lethal").getAsBoolean());
        strict(count);
        strict(number);
    }

    @Test
    void normalizesActionNamesAndEnumCase() {
        WishProgramNormalizationResult action = normalize("""
                {"action":"FOLLOW-PLAYER","parameters":{}}
                """);
        assertEquals("follow_player", first(action).action());

        WishProgramNormalizationResult enumeration = normalize("""
                {"action":"set_weather","parameters":{"weather":"Thunder"}}
                """);
        assertEquals("thunder", first(enumeration).parameters().get("weather").getAsString());
        strict(action);
        strict(enumeration);
    }

    @Test
    void ignoresHarmlessUnknownFieldsAndSupportsFlatLlmActionShape() {
        WishProgramNormalizationResult result = normalize("""
                {"action":"follow player","max_entities":5,
                 "reason":"玩家希望附近的怪物跟随自己"}
                """);

        assertEquals("follow_player", first(result).action());
        assertEquals(5, first(result).parameters().get("max_entities").getAsInt());
        assertTrue(!first(result).parameters().has("reason"));
        assertTrue(result.changes().stream().anyMatch(change ->
                change.parameter().equals("reason")
                        && change.reason() == WishNormalizationReason.UNKNOWN_FIELD_IGNORED));
        strict(result);
    }

    @Test
    void fillsSchemaDefaultsForMissingOptionalParameters() {
        WishProgramNormalizationResult result = normalize("""
                {"action":"avoid_player","parameters":{}}
                """);

        assertEquals(8, first(result).parameters().get("max_entities").getAsInt());
        assertEquals(16, first(result).parameters().get("radius").getAsInt());
        assertEquals(600, first(result).parameters().get("duration_seconds").getAsInt());
        assertTrue(result.changes().stream().filter(change ->
                change.reason() == WishNormalizationReason.DEFAULT_APPLIED).count() >= 3);
        strict(result);
    }

    @Test
    void dropsOneIndependentUnknownActionAndKeepsTheOtherFour() {
        WishProgramNormalizationResult result = WishProgramNormalizer.normalize("""
                {"schema_version":1,"goal":"five independent actions","core_actions":[
                 {"action":"set_time","parameters":{"value":"day"}},
                 {"action":"set_weather","parameters":{"weather":"clear"}},
                 {"action":"play_sound","parameters":{"sound":"minecraft:ambient.cave"}},
                 {"action":"totally_unknown_action","parameters":{}},
                 {"action":"spawn_particle","parameters":{"particle":"minecraft:cloud"}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """);

        assertEquals(WishProgramValidationStatus.REPAIRABLE, result.status());
        assertEquals(1, result.droppedActions());
        assertEquals(4, result.requireProgram().coreActions().size());
        strict(result);
    }

    @Test
    void unknownActionInsideSequenceIsRejectedForPlanningRepair() {
        WishProgramNormalizationResult result = normalize("""
                {"action":"sequence","parameters":{"actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":1}},
                 {"action":"unknown_required_precondition","parameters":{}}]}}
                """);

        assertEquals(WishProgramValidationStatus.REJECT, result.status());
        assertTrue(result.issue().validationError().contains("UNKNOWN_ACTION"));
    }

    @Test
    void recoversMarkdownNaturalLanguageBomAndTrailingCommas() {
        WishProgramNormalizationResult result = WishProgramNormalizer.normalize("\uFEFFHere is the program:\n```json\n" +
                "{\"schema_version\":1,\"goal\":\"follow\",\"core_actions\":[" +
                "{\"action\":\"follow_player\",\"parameters\":{},}]," +
                "\"presentation_actions\":[],\"skill\":\"\",\"unknown_capability\":\"\",}\n```\nExplanation");

        assertEquals(WishProgramValidationStatus.REPAIRABLE, result.status());
        assertEquals("follow_player", first(result).action());
        assertTrue(result.changes().stream().anyMatch(change ->
                change.reason() == WishNormalizationReason.JSON_RECOVERY));
        strict(result);
    }

    @Test
    void completelyUnparseableResponseIsRejectedForAiRepair() {
        WishProgramNormalizationResult result = WishProgramNormalizer.normalize("not JSON at all");

        assertEquals(WishProgramValidationStatus.REJECT, result.status());
        assertEquals("INVALID_WISH_PROGRAM:JSON_SYNTAX", result.issue().validationError());
    }

    @Test
    void forbiddenExecutableFieldsRemainHardRejects() {
        WishProgramNormalizationResult result = normalize("""
                {"action":"follow_player","parameters":{"command":"/op @s"}}
                """);

        assertEquals(WishProgramValidationStatus.REJECT, result.status());
        assertTrue(result.issue().safetyCritical());
        assertEquals("INVALID_WISH_PROGRAM:FORBIDDEN_PARAMETER", result.issue().validationError());
    }

    @Test
    void dynamicServerBudgetStillRejectsAfterTolerantNormalization() {
        WishProgramNormalizationResult normalized = normalize("""
                {"action":"spawn_item_rain","parameters":{
                 "item":"minecraft:wooden_sword","count":4096}}
                """);
        strict(normalized);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishProgramValidator.validate(normalized.requireProgram(), new FakeResolver()));
        assertEquals("BUDGET_EXCEEDED:item_entities=4096", error.getMessage());
    }

    private static WishProgramNormalizationResult normalize(String action) {
        return WishProgramNormalizer.normalize("""
                {"schema_version":1,"goal":"normalization test","core_actions":[%s],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """.formatted(action));
    }

    private static WishProgramNormalizationResult programWithMetadata(String metadata) {
        String suffix = metadata.isBlank() ? "" : "," + metadata;
        return WishProgramNormalizer.normalize("""
                {"schema_version":1,"goal":"never alone",
                 "core_actions":[{"action":"entity_attraction_aura","parameters":{"permanent":true}}],
                 "presentation_actions":[],"unknown_capability":""%s}
                """.formatted(suffix));
    }

    private static WishProgramAction first(WishProgramNormalizationResult result) {
        WishProgram program = result.requireProgram();
        assertNotNull(program);
        return program.coreActions().get(0);
    }

    private static void strict(WishProgramNormalizationResult result) {
        WishProgramJson.validate(result.requireProgram(),
                com.ikunkk02.wishingwillow.execution.action.WishActionRegistry.defaults());
    }

    private static final class FakeResolver implements WishProgramResourceResolver {
        private final Map<RegistryEntryType, Set<String>> entries = Map.of(
                RegistryEntryType.ITEM, Set.of("minecraft:wooden_sword"));

        @Override
        public String resolve(RegistryEntryType type, String id) {
            return entries.getOrDefault(type, Set.of()).contains(id) ? id : null;
        }

        @Override public String resolveDimension(String id) { return null; }
        @Override public int maxStackSize(RegistryEntryType type, String id) { return 1; }
        @Override public boolean containsPredefinedEvent(String event) { return false; }
    }
}
