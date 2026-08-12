package com.ikunkk02.wishingwillow.program.skill;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.program.WishProgram;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded tag/example retrieval; no Agent search loop is involved. */
public final class WishSkillRegistry {
    private static final Gson GSON = new Gson();
    private static final WishSkillRegistry DEFAULT = defaultsInternal();
    private final Map<String, WishSkillDefinition> skills;

    private WishSkillRegistry(List<WishSkillDefinition> definitions) {
        Map<String, WishSkillDefinition> values = new LinkedHashMap<>();
        definitions.forEach(skill -> values.put(skill.id(), skill));
        this.skills = Map.copyOf(values);
    }

    public static WishSkillRegistry defaults() { return DEFAULT; }
    @Nullable public WishSkillDefinition find(String id) { return skills.get(id); }

    public List<WishSkillDefinition> retrieve(String intent, int limit) {
        String normalized = normalize(intent);
        return skills.values().stream().map(skill -> new Scored(skill, score(skill, normalized)))
                .filter(value -> value.score > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(value -> value.skill.id()))
                .limit(Math.max(0, Math.min(5, limit))).map(Scored::skill).toList();
    }

    public void validateSelection(WishProgram program) {
        if (!program.usesSkill()) return;
        WishSkillDefinition skill = find(program.skill());
        if (skill == null) throw new IllegalArgumentException("UNKNOWN_SKILL");
        Set<String> used = new java.util.HashSet<>();
        program.coreActions().forEach(action -> used.add(action.action()));
        program.presentationActions().forEach(action -> used.add(action.action()));
        if (!used.containsAll(skill.requiredActions())) throw new IllegalArgumentException("SKILL_REQUIRED_ACTIONS_MISSING");
    }

    public String candidatePrompt(String intent) {
        JsonArray result = new JsonArray();
        retrieve(intent, 3).forEach(skill -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", skill.id()); value.addProperty("description", skill.description());
            JsonArray triggers = new JsonArray(); skill.triggers().forEach(triggers::add); value.add("triggers", triggers);
            JsonArray required = new JsonArray(); skill.requiredActions().forEach(required::add); value.add("required_actions", required);
            value.addProperty("parameter_template", skill.parameterTemplate());
            JsonArray examples = new JsonArray(); skill.examples().forEach(examples::add); value.add("examples", examples);
            result.add(value);
        });
        return GSON.toJson(result);
    }

    private static int score(WishSkillDefinition skill, String intent) {
        int score = 0;
        for (String trigger : skill.triggers()) if (intent.contains(normalize(trigger))) score += 8;
        for (String example : skill.examples()) {
            for (String token : normalize(example).split("_")) if (token.length() >= 2 && intent.contains(token)) score++;
        }
        return score;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_");
    }

    private static WishSkillRegistry defaultsInternal() {
        List<WishSkillDefinition> values = new ArrayList<>();
        values.add(new WishSkillDefinition("block_rain",
                "Physical rain made only from real registered blocks around a target.",
                Set.of("block rain", "falling blocks", "blocks from above", "\u65b9\u5757\u96e8"),
                Set.of("spawn_falling_block"),
                "Resolve block id; clamp count; call spawn_falling_block with gravity, spread and bounded interval.",
                List.of("\u8ba9100\u4e2a\u94bb\u77f3\u5757\u4ece\u5929\u800c\u964d",
                        "\u8ba9\u91d1\u5757\u50cf\u96e8\u4e00\u6837\u6389\u4e0b\u6765", "sand falls from the sky"),
                Duration.ofSeconds(45)));
        values.add(new WishSkillDefinition("dramatic_item_reward",
                "Give a real item reward and add optional celebratory presentation.",
                Set.of("dramatic reward with sound and particles", "cinematic item reward",
                        "\u58f0\u97f3\u548c\u7c92\u5b50\u7279\u6548\u5956\u52b1"),
                Set.of("give_item", "play_sound", "spawn_particle"),
                "give_item is core; sound/particle/lightning are presentation actions.",
                List.of("\u7ed9\u621164\u9897\u94bb\u77f3\u7136\u540e\u5e86\u795d",
                        "reward me with emeralds and effects"), Duration.ofSeconds(30)));
        values.add(new WishSkillDefinition("absurd_wish_realization",
                "Realize abstract, emotional, exaggerated, or extreme wishes through narrative escalation, absurd literalization, and global rules instead of minimal single-entity satisfaction. For wishes containing words like \"never\", \"forever\", \"always\", \"everyone\", \"the whole world\", \"all living things\", \"never be alone\", \"everybody loves me\", \"become the luckiest\", etc.",
                Set.of("forever", "never", "always", "everlasting", "eternal", "permanent",
                        "\u6c38\u8fdc", "\u6c38\u4e45", "\u4e00\u76f4", "\u6c38\u4e0d\u505c\u6b62",
                        "\u6c38\u8fdc\u4e0d", "\u4ece\u6b64\u4ee5\u540e", "\u60f3\u8981\u4e00\u4e2a\u670b\u53cb",
                        "\u5b64\u5355", "\u4e0d\u5b64\u5355", "\u966a\u4f34",
                        "everyone loves", "the whole world", "all creatures",
                        "never be lonely", "never alone", "never lonely",
                        "always with me", "stay with me forever",
                        "absurd realization", "extreme consequence",
                        "narrative escalation", "literal wish",
                        "\u5168\u4e16\u754c", "\u6240\u6709\u751f\u7269", "\u6240\u6709\u4eba",
                        "become the luckiest", "world peace", "everybody",
                        "all animals", "all monsters", "attract everything",
                        "\u5438\u5f15\u6240\u6709", "\u5168\u90e8\u9760\u8fd1"),
                Set.of("entity_attraction_aura", "follow_player", "spawn_entity", "play_sound"),
                "Use entity_attraction_aura with permanent=true for wishes about \"never being alone\" or \"all creatures coming to me\". Use follow_player with permanent=true for a single dedicated follower. Always prefer global rules over single-entity spawns for abstract wishes. Set permanent=true and use large radius. Include ALL entity types (hostile, passive, villagers, modded). The absurdity is that the wish IS fulfilled literally: the player truly is NEVER alone, but the consequence is overwhelming.",
                List.of("I wish I would never be lonely",
                        "\u6211\u5e0c\u671b\u6c38\u8fdc\u4e0d\u5b64\u5355",
                        "\u8ba9\u6240\u6709\u751f\u7269\u90fd\u88ab\u6211\u5438\u5f15",
                        "make the whole world come to me",
                        "every living thing should follow me forever"),
                Duration.ofSeconds(120)));
        
        return new WishSkillRegistry(values);
    }

    private record Scored(WishSkillDefinition skill, int score) { }
}
