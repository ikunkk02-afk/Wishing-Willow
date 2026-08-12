package com.ikunkk02.wishingwillow.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WishAdvancementResourcesTest {
    private static final List<String> IDS = List.of("root", "first_wish", "wish_come_true",
            "dreamer", "greedy", "wish_veteran", "absurd_success", "world_heard",
            "out_of_control", "backfire", "careful_what_you_wish_for");

    @Test
    void definesCompleteVanillaAdvancementTreeWithRealModItem() {
        for (String id : IDS) {
            JsonObject root = resource("data/wishing_willow/advancements/" + id + ".json");
            JsonObject display = root.getAsJsonObject("display");
            assertEquals("wishing_willow:wishing_willow",
                    display.getAsJsonObject("icon").get("item").getAsString());
            assertEquals("minecraft:impossible",
                    root.getAsJsonObject("criteria").getAsJsonObject("server_granted")
                            .get("trigger").getAsString());
        }
        assertEquals("wishing_willow:dreamer", resource("data/wishing_willow/advancements/greedy.json")
                .get("parent").getAsString());
        JsonObject hidden = resource("data/wishing_willow/advancements/careful_what_you_wish_for.json")
                .getAsJsonObject("display");
        assertTrue(hidden.get("hidden").getAsBoolean());
        assertEquals("challenge", hidden.get("frame").getAsString());
        assertEquals("challenge", resource("data/wishing_willow/advancements/wish_veteran.json")
                .getAsJsonObject("display").get("frame").getAsString());
    }

    @Test
    void bothLanguagesContainEveryAdvancementTitleAndDescription() {
        JsonObject zh = resource("assets/wishing_willow/lang/zh_cn.json");
        JsonObject en = resource("assets/wishing_willow/lang/en_us.json");
        for (String key : List.of("root", "first_wish", "wish_come_true", "dreamer", "greedy",
                "wish_veteran", "absurd_success", "world_heard", "out_of_control", "backfire")) {
            assertTrue(zh.has("advancement.wishing_willow." + key + ".title"));
            assertTrue(en.has("advancement.wishing_willow." + key + ".description"));
        }
        assertEquals("小心你许下的愿望", zh.get("advancement.wishing_willow.careful.title").getAsString());
        assertEquals("Careful What You Wish For", en.get("advancement.wishing_willow.careful.title").getAsString());
    }

    private static JsonObject resource(String path) {
        var stream = WishAdvancementResourcesTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
