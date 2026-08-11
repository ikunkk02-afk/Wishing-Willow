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
