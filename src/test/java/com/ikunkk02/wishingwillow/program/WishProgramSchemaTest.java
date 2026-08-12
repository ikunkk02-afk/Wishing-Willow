package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The AI-facing JSON Schema is generated from the action registry as a discriminated oneOf with
 * strict per-action parameter bounds; server-side validation enforces the same bounds so the
 * client can never smuggle out-of-range or mistyped parameters past the AI schema.
 */
class WishProgramSchemaTest {

    @Test
    void schemaIsDiscriminatedOneOfPerAction() {
        JsonObject schema = WishProgramJson.jsonSchema();
        JsonArray oneOf = schema.getAsJsonObject("properties").getAsJsonObject("core_actions")
                .getAsJsonObject("items").getAsJsonArray("oneOf");
        JsonObject giveItem = variant(oneOf, "give_item");
        assertNotNull(giveItem);
        assertEquals("give_item", giveItem.getAsJsonObject("properties")
                .getAsJsonObject("action").get("const").getAsString());
        JsonObject parameters = giveItem.getAsJsonObject("properties").getAsJsonObject("parameters");
        assertEquals(1, parameters.getAsJsonObject("properties").getAsJsonObject("count")
                .get("minimum").getAsDouble());
        assertEquals(4096, parameters.getAsJsonObject("properties").getAsJsonObject("count")
                .get("maximum").getAsDouble());
        assertTrue(requiredContains(parameters, "item"));
        assertTrue(requiredContains(parameters, "count"));
    }

    @Test
    void giveItemSchemaSupportsStructuredAdvancedItemMetadataWithoutBreakingLegacyCalls() {
        JsonArray oneOf = WishProgramJson.jsonSchema().getAsJsonObject("properties")
                .getAsJsonObject("core_actions").getAsJsonObject("items").getAsJsonArray("oneOf");
        JsonObject parameters = variant(oneOf, "give_item").getAsJsonObject("properties")
                .getAsJsonObject("parameters");
        JsonObject properties = parameters.getAsJsonObject("properties");

        assertTrue(requiredContains(parameters, "item"));
        assertTrue(requiredContains(parameters, "count"));
        assertFalse(requiredContains(parameters, "enchantments"));
        assertEquals("array", properties.getAsJsonObject("enchantments").get("type").getAsString());
        JsonObject enchantment = properties.getAsJsonObject("enchantments").getAsJsonObject("items");
        assertTrue(requiredContains(enchantment, "id"));
        assertTrue(requiredContains(enchantment, "level"));
        assertEquals(10, enchantment.getAsJsonObject("properties").getAsJsonObject("level")
                .get("maximum").getAsInt());
        assertEquals("array", properties.getAsJsonObject("attributes").get("type").getAsString());
        assertEquals("object", properties.getAsJsonObject("custom_data").get("type").getAsString());

        assertDoesNotThrow(() -> parse("give_item",
                "{\"item\":\"minecraft:diamond_sword\",\"count\":1}"));
        assertDoesNotThrow(() -> parse("give_item", """
                {"item":"minecraft:diamond_sword","count":1,
                 "enchantments":[{"id":"minecraft:sharpness","level":5}],
                 "custom_name":"弑神","damage":12,"unbreakable":true,
                 "allow_unsafe_enchantment_levels":false,
                 "allow_incompatible_enchantments":false,
                 "attributes":[{"id":"minecraft:generic.attack_damage","amount":3.0,
                  "operation":"addition","slot":"mainhand"}],
                 "custom_data":{"WishingWillow":{"quality":"MAXED"}}}
                """));
    }

    @Test
    void advancedItemNestedSchemaRejectsMalformedEnchantmentsAndUnsafeOverflow() {
        assertInvalidParameterType("give_item", """
                {"item":"minecraft:diamond_sword","count":1,
                 "enchantments":[{"id":"minecraft:sharpness","level":"V"}]}
                """, "level");
        IllegalArgumentException overflow = assertThrows(IllegalArgumentException.class,
                () -> parse("give_item", """
                        {"item":"minecraft:diamond_sword","count":1,
                         "enchantments":[{"id":"minecraft:sharpness","level":11}],
                         "allow_unsafe_enchantment_levels":true}
                        """));
        assertEquals("INVALID_WISH_PROGRAM:PARAMETER_MAX_level", overflow.getMessage());
    }

    @Test
    void fallingBlockSchemaBindsEveryParameter() {
        JsonObject schema = WishProgramJson.jsonSchema();
        JsonArray oneOf = schema.getAsJsonObject("properties").getAsJsonObject("core_actions")
                .getAsJsonObject("items").getAsJsonArray("oneOf");
        JsonObject falling = variant(oneOf, "spawn_falling_block");
        assertNotNull(falling);
        JsonObject properties = falling.getAsJsonObject("properties").getAsJsonObject("parameters")
                .getAsJsonObject("properties");
        assertEquals(64, properties.getAsJsonObject("height").get("maximum").getAsDouble());
        assertEquals(8, properties.getAsJsonObject("height").get("minimum").getAsDouble());
        assertEquals(32, properties.getAsJsonObject("horizontal_radius").get("maximum").getAsDouble());
        assertEquals(256, properties.getAsJsonObject("count").get("maximum").getAsDouble());
        assertTrue(properties.getAsJsonObject("landing").getAsJsonArray("enum").toString()
                .contains("deliver_to_player"));
        assertTrue(properties.getAsJsonObject("target").getAsJsonArray("enum").toString()
                .contains("self"));
    }

    @Test
    void itemRainSchemaBindsItemUnitsAndPhysicalDelivery() {
        JsonArray oneOf = WishProgramJson.jsonSchema().getAsJsonObject("properties")
                .getAsJsonObject("core_actions").getAsJsonObject("items").getAsJsonArray("oneOf");
        JsonObject itemRain = variant(oneOf, "spawn_item_rain");
        assertNotNull(itemRain);
        JsonObject parameters = itemRain.getAsJsonObject("properties").getAsJsonObject("parameters");
        JsonObject properties = parameters.getAsJsonObject("properties");
        assertTrue(requiredContains(parameters, "item"));
        assertTrue(requiredContains(parameters, "count"));
        assertEquals(4096, properties.getAsJsonObject("count").get("maximum").getAsInt());
        assertEquals("world_items", properties.getAsJsonObject("delivery")
                .getAsJsonArray("enum").get(0).getAsString());
        assertEquals(64, properties.getAsJsonObject("height").get("maximum").getAsInt());
        assertEquals(32, properties.getAsJsonObject("horizontal_radius").get("maximum").getAsInt());
        assertEquals(20, properties.getAsJsonObject("interval_ticks").get("maximum").getAsInt());
    }

    @Test
    void flowActionsAreBoundedInTheSchema() {
        JsonObject schema = WishProgramJson.jsonSchema();
        JsonArray oneOf = schema.getAsJsonObject("properties").getAsJsonObject("core_actions")
                .getAsJsonObject("items").getAsJsonArray("oneOf");
        JsonObject delay = variant(oneOf, "delay");
        assertEquals(1200, delay.getAsJsonObject("properties").getAsJsonObject("parameters")
                .getAsJsonObject("properties").getAsJsonObject("ticks").get("maximum").getAsDouble());
        JsonObject repeat = variant(oneOf, "repeat");
        assertEquals(16, repeat.getAsJsonObject("properties").getAsJsonObject("parameters")
                .getAsJsonObject("properties").getAsJsonObject("count").get("maximum").getAsDouble());
    }

    @Test
    void stringCountIsRejectedByServerValidation() {
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"diamonds","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":"一百"}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
    }

    @Test
    void mathematicallyIntegralJsonNumberSatisfiesIntegerSchema() {
        assertDoesNotThrow(() -> parse("give_item",
                "{\"item\":\"minecraft:diamond\",\"count\":64.0}"));
    }

    @Test
    void outOfRangeCountIsRejectedByServerValidation() {
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"diamonds","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":5000}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"rain","core_actions":[
                 {"action":"spawn_falling_block","parameters":{"block":"minecraft:diamond_block","count":257}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"item rain","core_actions":[
                 {"action":"spawn_item_rain","parameters":{"item":"minecraft:diamond","count":4097}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
    }

    @Test
    void wrongEnumIsRejectedByServerValidation() {
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"weather","core_actions":[
                 {"action":"set_weather","parameters":{"weather":"tornado"}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"rain","core_actions":[
                 {"action":"spawn_falling_block","parameters":{"block":"minecraft:diamond_block",
                  "count":10,"landing":"launch_to_moon"}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
    }

    @Test
    void undeclaredParameterIsRejectedByServerValidation() {
        assertThrows(IllegalArgumentException.class, () -> WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"diamonds","core_actions":[
                 {"action":"give_item","parameters":{"item":"minecraft:diamond","count":1,"soul":true}}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """));
    }

    @Test
    void missingRequiredParameterIsRejectedWithTheParameterName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WishProgramJson.parseAndValidate("""
                        {"schema_version":1,"goal":"item rain","core_actions":[
                         {"action":"spawn_item_rain","parameters":{"count":64}}],
                         "presentation_actions":[],"skill":"","unknown_capability":""}
                        """));
        assertEquals("INVALID_WISH_PROGRAM:MISSING_REQUIRED_PARAMETER_item", error.getMessage());
    }

    @Test
    void everyJsonSchemaPrimitiveTypeIsStrictlyEnforced() {
        assertInvalidParameterType("give_item", "{\"item\":42,\"count\":1}", "item");
        assertInvalidParameterType("give_item", "{\"item\":\"minecraft:diamond\",\"count\":true}", "count");
        assertInvalidParameterType("modify_health", "{\"delta\":1,\"allow_lethal\":\"false\"}", "allow_lethal");
        assertInvalidParameterType("repeat", "{\"count\":1,\"actions\":{}}", "actions");
        assertInvalidParameterType("give_item", "{\"item\":null,\"count\":1}", "item");
    }

    @Test
    void aiFacingEnumsAreCaseSensitiveAndLowercase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parse("apply_effect_group", "{\"group\":\"BENEFICIAL\"}"));
        assertEquals("INVALID_WISH_PROGRAM:PARAMETER_ENUM_group", error.getMessage());
        assertDoesNotThrow(() -> parse("apply_effect_group", "{\"group\":\"beneficial\"}"));
    }

    private static void assertInvalidParameterType(String action, String parameters, String key) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parse(action, parameters));
        assertEquals("INVALID_WISH_PROGRAM:PARAMETER_TYPE_" + key, error.getMessage());
    }

    private static WishProgram parse(String action, String parameters) {
        return WishProgramJson.parseAndValidate("""
                {"schema_version":1,"goal":"schema validation","core_actions":[
                 {"action":"%s","parameters":%s}],
                 "presentation_actions":[],"skill":"","unknown_capability":""}
                """.formatted(action, parameters));
    }

    private static JsonObject variant(JsonArray oneOf, String actionId) {
        for (var element : oneOf) {
            JsonObject variant = element.getAsJsonObject();
            if (actionId.equals(variant.getAsJsonObject("properties")
                    .getAsJsonObject("action").get("const").getAsString())) {
                return variant;
            }
        }
        return null;
    }

    private static boolean requiredContains(JsonObject parameters, String key) {
        for (JsonElement element : parameters.getAsJsonArray("required")) {
            if (element.getAsString().equals(key)) return true;
        }
        return false;
    }
}
