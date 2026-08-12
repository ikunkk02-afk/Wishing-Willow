package com.ikunkk02.wishingwillow.ai.prompt;

import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import net.minecraftforge.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Caches only stable mod/action/skill metadata, never prior AI conversation state. */
public final class WishAiContextCache {
    private static volatile WishAiContextSnapshot snapshot;

    private WishAiContextCache() { }

    public static WishAiContextSnapshot initialize() {
        WishAiContextSnapshot current = snapshot;
        if (current != null) return current;
        synchronized (WishAiContextCache.class) {
            if (snapshot != null) return snapshot;
            List<String> mods = ModList.get().getMods().stream()
                    .map(info -> info.getModId() + "@" + info.getVersion()).sorted().limit(32).toList();
            String actions = WishActionRegistry.defaults().summaryPrompt();
            String skills = WishSkillRegistry.defaults().candidatePrompt(
                    "forever permanent friend lonely block rain dramatic reward luckiest");
            String forgeVersion = ModList.get().getModContainerById("forge")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
            snapshot = new WishAiContextSnapshot("1.20.1", "Forge " + forgeVersion, mods,
                    digest(String.join("\n", mods)), digest(actions), digest(skills));
            return snapshot;
        }
    }

    public static void invalidate() { snapshot = null; }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}