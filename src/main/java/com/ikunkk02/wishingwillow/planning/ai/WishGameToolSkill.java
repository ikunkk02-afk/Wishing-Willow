package com.ikunkk02.wishingwillow.planning.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the bundled planner skill that teaches the model how to call validated game actions. */
public final class WishGameToolSkill {
    static final String RESOURCE = "/data/wishing_willow/ai_skills/use-game-tools/SKILL.md";
    public static final String TEXT = load();

    private WishGameToolSkill() {}

    private static String load() {
        try (InputStream stream = WishGameToolSkill.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("MISSING_WISH_GAME_TOOL_SKILL");
            String file = new String(stream.readNBytes(32_769), StandardCharsets.UTF_8);
            if (file.length() > 32_768) throw new IllegalStateException("WISH_GAME_TOOL_SKILL_TOO_LARGE");
            return stripFrontmatter(file).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("FAILED_TO_LOAD_WISH_GAME_TOOL_SKILL", exception);
        }
    }

    private static String stripFrontmatter(String file) {
        if (!file.startsWith("---")) return file;
        int end = file.indexOf("\n---", 3);
        return end < 0 ? file : file.substring(end + 4);
    }
}
