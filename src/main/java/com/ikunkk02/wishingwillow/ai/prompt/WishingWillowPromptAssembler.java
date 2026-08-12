package com.ikunkk02.wishingwillow.ai.prompt;

import com.google.gson.Gson;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** Orders independently-owned prompt sections and keeps player text in the user message. */
public final class WishingWillowPromptAssembler {
    private static final Gson GSON = new Gson();
    private static final int MAX_WORLD = 4_096;
    private static final int MAX_ACTIONS = 24_000;
    private static final int MAX_SKILLS = 8_000;
    private static final int MAX_CONSTRAINTS = 4_096;
    private static final int MAX_CONTRACT = 24_000;

    public enum RequestKind { INTERPRETATION, PLANNING, COMPLEX_AGENT, CONTRACT_REVIEW, REPAIR }

    public record AssembledPrompt(String systemMessage, String userMessage, List<String> sections,
                                  int characters, int tokensEstimate) { }

    private WishingWillowPromptAssembler() { }

    public static AssembledPrompt assemble(RequestKind kind, WishingWillowRuntimeContext context,
                                           String actions, String skills, String constraints,
                                           String outputContract, String playerWish,
                                           @Nullable String untrustedPayload) {
        String core = kind == RequestKind.REPAIR
                ? WishingWillowCorePrompt.TEXT + "\nRepair only the invalid contract fields; preserve wish semantics."
                : WishingWillowCorePrompt.TEXT;
        String system = "[CORE]\n" + core
                + "\n\n[WORLD CONTEXT]\n" + bounded(context == null ? "{}" : context.compactJson(), MAX_WORLD)
                + "\n\n[CAPABILITIES]\n" + bounded(actions, MAX_ACTIONS)
                + "\n\n[SKILLS]\n" + bounded(skills, MAX_SKILLS)
                + "\n\n[EXECUTION CONSTRAINTS]\n" + bounded(constraints, MAX_CONSTRAINTS)
                + "\n\n[OUTPUT CONTRACT]\n" + bounded(outputContract, MAX_CONTRACT);
        String user = "<UNTRUSTED_PLAYER_WISH_JSON>\n"
                + GSON.toJson(Map.of("wish", playerWish == null ? "" : playerWish))
                + "\n</UNTRUSTED_PLAYER_WISH_JSON>";
        if (untrustedPayload != null && !untrustedPayload.isBlank()) user += "\n" + untrustedPayload;
        List<String> sections = List.of("CORE", "WORLD", "ACTIONS", "SKILLS", "WISH", "SCHEMA");
        return new AssembledPrompt(system, user, sections, system.length() + user.length(),
                Math.max(1, (system.length() + user.length() + 3) / 4));
    }

    private static String bounded(String value, int max) {
        String safe = value == null || value.isBlank() ? "none" : value;
        return safe.length() <= max ? safe : safe.substring(0, max) + "\n[TRUNCATED_TO_PROMPT_BUDGET]";
    }
}
