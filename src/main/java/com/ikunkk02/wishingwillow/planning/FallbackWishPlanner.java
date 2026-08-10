package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.WishSafetyPolicy;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FallbackWishPlanner {
    private static final Pattern ARABIC_NUMBER = Pattern.compile("(?<![0-9])([0-9]{1,9})(?![0-9])");
    private static final Pattern CHINESE_NUMBER = Pattern.compile("[零〇一二两三四五六七八九十百千万亿]+");
    private static final Map<String, List<String>> VANILLA_ALIASES = Map.of(
            "minecraft:diamond", List.of("diamond", "diamonds", "钻石"),
            "minecraft:gold_ingot", List.of("gold", "gold ingot", "金锭", "黄金"),
            "minecraft:bread", List.of("bread", "面包"),
            "minecraft:diamond_sword", List.of("diamond sword", "钻石剑"),
            "minecraft:netherite_sword", List.of("netherite sword", "下界合金剑")
    );

    public WishPlanResult plan(String originalWish, WishInterpretation interpretation,
                               WishContextSnapshot context, CapabilityCatalog catalog,
                               PlanningEnvironment environment, ExecutionSettingsSnapshot settings) {
        if (interpretation.requiredCapabilities().isEmpty()) {
            return WishPlanResult.failed(WishPlanError.UNSATISFIED_CAPABILITIES);
        }
        WishCapability primary = interpretation.requiredCapabilities().get(0);
        String semanticText = (originalWish + " " + interpretation.literalGoal() + " "
                + interpretation.twistedOutcome()).toLowerCase(Locale.ROOT);
        List<CapabilityCandidate> candidates = catalog.candidates().stream()
                .filter(candidate -> candidate.requestedCapability() == primary)
                .filter(candidate -> candidate.matchType() == MatchType.EXACT
                        || candidate.matchType() == MatchType.COMPATIBLE)
                .filter(candidate -> WishSafetyPolicy.candidateAllowed(candidate.reference(),
                        interpretation.severity(), settings))
                .sorted(Comparator.comparingInt(CapabilityCandidate::matchScore).reversed())
                .toList();
        for (CapabilityCandidate candidate : candidates) {
            WishPlanDraft draft = createDraft(originalWish, semanticText, interpretation, candidate);
            if (draft == null) continue;
            try {
                WishPlanValidation validated = WishPlanValidator.parseAndValidate(
                        WishPlanJson.toAiJson(draft), interpretation, catalog, environment, settings);
                if (validated.state() == WishPlanState.READY) return WishPlanResult.success(validated.draft());
                if (!validated.unfulfilledCapabilities().contains(primary)) {
                    return WishPlanResult.partial(validated.draft());
                }
            } catch (IllegalArgumentException ignored) {
                // Try the next deterministic candidate. No untrusted text is logged here.
            }
        }
        return WishPlanResult.failed(WishPlanError.UNSATISFIED_CAPABILITIES);
    }

    private WishPlanDraft createDraft(String originalWish, String text, WishInterpretation interpretation,
                                      CapabilityCandidate candidate) {
        WishActionType action = action(candidate);
        if (action == null || !semanticMatch(text, candidate, action)) return null;
        Timing timing = timing(interpretation.delivery());
        if (timing == null) return null;
        List<WishPlanStep> steps = new ArrayList<>();
        if (action == WishActionType.GIVE_ITEM) {
            OptionalInt quantity = quantity(originalWish + " " + interpretation.literalGoal());
            if (quantity.isEmpty()) return null;
            int total = quantity.getAsInt();
            int max = 64 * WishPlanBudget.maxSteps(interpretation.severity());
            if (total < 1 || total > max) return null;
            int remaining = total;
            while (remaining > 0) {
                int count = Math.min(64, remaining);
                JsonObject parameters = new JsonObject(); parameters.addProperty("count", count);
                steps.add(step(steps.size(), timing, action, candidate, WishTargetType.PLAYER,
                        parameters));
                remaining -= count;
            }
        } else {
            JsonObject parameters = parameters(action, text, candidate);
            if (parameters == null) return null;
            WishTargetType target = action == WishActionType.CHANGE_TIME
                    || action == WishActionType.CHANGE_WEATHER ? WishTargetType.WORLD : WishTargetType.PLAYER;
            steps.add(step(0, timing, action, candidate, target, parameters));
        }
        return new WishPlanDraft(1, "Controlled vanilla fallback", interpretation.delivery(),
                interpretation.severity(), timing.delaySeconds == 0
                ? WishEstimatedDuration.INSTANT : WishEstimatedDuration.SHORT, steps);
    }

    private static WishPlanStep step(int index, Timing timing, WishActionType action,
                                     CapabilityCandidate candidate, WishTargetType target,
                                     JsonObject parameters) {
        return new WishPlanStep(index, timing.timing, timing.delaySeconds, timing.trigger, action,
                candidate.requestedCapability(), candidate.candidateId(), target, parameters,
                "Controlled fallback preserving the primary interpreted capability", candidate.reference());
    }

    private static WishActionType action(CapabilityCandidate candidate) {
        RegistryEntryType type = candidate.registryResource() == null ? null : candidate.registryResource().type();
        if (type == RegistryEntryType.ITEM && Set.of(WishCapability.GIVE_ITEM,
                WishCapability.STRONG_WEAPON, WishCapability.INVENTORY_CHANGE)
                .contains(candidate.providedCapability())) return WishActionType.GIVE_ITEM;
        if (type == RegistryEntryType.EFFECT) return WishActionType.APPLY_EFFECT;
        if (type == RegistryEntryType.ENTITY) return WishActionType.SPAWN_ENTITY;
        if (type == RegistryEntryType.SOUND) return WishActionType.PLAY_SOUND;
        if (type == RegistryEntryType.DIMENSION) return WishActionType.TELEPORT;
        if (candidate.sourceKind() == CandidateSourceKind.VANILLA_BUILTIN) {
            return switch (candidate.providedCapability()) {
                case CHANGE_TIME -> WishActionType.CHANGE_TIME;
                case CHANGE_WEATHER -> WishActionType.CHANGE_WEATHER;
                case TELEPORT -> WishActionType.TELEPORT;
                default -> null;
            };
        }
        return null;
    }

    private static JsonObject parameters(WishActionType action, String text, CapabilityCandidate candidate) {
        JsonObject p = new JsonObject();
        switch (action) {
            case APPLY_EFFECT -> { p.addProperty("duration_seconds", 60); p.addProperty("amplifier", 0); }
            case CHANGE_TIME -> {
                String value = contains(text, "night", "晚上", "夜晚", "黑夜") ? "NIGHT"
                        : contains(text, "dawn", "黎明") ? "DAWN"
                        : contains(text, "dusk", "黄昏") ? "DUSK"
                        : contains(text, "day", "白天", "早上") ? "DAY" : null;
                if (value == null) return null; p.addProperty("value", value);
            }
            case CHANGE_WEATHER -> {
                String weather = contains(text, "thunder", "storm", "雷暴", "雷雨") ? "THUNDER"
                        : contains(text, "rain", "下雨", "雨天") ? "RAIN"
                        : contains(text, "clear", "sunny", "晴天", "放晴") ? "CLEAR" : null;
                if (weather == null) return null; p.addProperty("weather", weather); p.addProperty("duration_seconds", 300);
            }
            case TELEPORT -> {
                if (candidate.registryResource() != null
                        && candidate.registryResource().type() == RegistryEntryType.DIMENSION) {
                    p.addProperty("mode", "CANDIDATE_DIMENSION");
                } else {
                    if (!contains(text, "teleport", "传送", "移动到", "带我去")) return null;
                    p.addProperty("mode", "NEARBY_SAFE"); p.addProperty("distance_min", 8); p.addProperty("distance_max", 32);
                }
            }
            case SPAWN_ENTITY -> { p.addProperty("count", quantity(text).orElse(1)); p.addProperty("distance_min", 12); p.addProperty("distance_max", 24); }
            case PLAY_SOUND -> { p.addProperty("volume", 1); p.addProperty("pitch", 1); p.addProperty("distance", 32); }
            default -> { return null; }
        }
        return p;
    }

    private static boolean semanticMatch(String text, CapabilityCandidate candidate, WishActionType action) {
        if (action == WishActionType.CHANGE_TIME || action == WishActionType.CHANGE_WEATHER
                || action == WishActionType.TELEPORT) return true;
        if (action == WishActionType.SPAWN_ENTITY && Set.of(WishCapability.STALKING_ENTITY,
                WishCapability.PERSISTENT_FOLLOWER, WishCapability.HOSTILE_ENTITY,
                WishCapability.FRIENDLY_ENTITY, WishCapability.SPAWN_ENTITY)
                .contains(candidate.requestedCapability())) return true;
        if (candidate.registryResource() == null) return false;
        String id = candidate.registryResource().id();
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        if (text.contains(path) || text.contains(candidate.featureName().toLowerCase(Locale.ROOT))) return true;
        return VANILLA_ALIASES.getOrDefault(id, List.of()).stream().anyMatch(text::contains);
    }

    private static OptionalInt quantity(String value) {
        Matcher arabic = ARABIC_NUMBER.matcher(value);
        if (arabic.find()) {
            try { return OptionalInt.of(Integer.parseInt(arabic.group(1))); }
            catch (NumberFormatException ignored) { return OptionalInt.empty(); }
        }
        Matcher chinese = CHINESE_NUMBER.matcher(value);
        if (chinese.find()) {
            long parsed = parseChinese(chinese.group());
            if (parsed > 0 && parsed <= Integer.MAX_VALUE) return OptionalInt.of((int) parsed);
        }
        return OptionalInt.empty();
    }

    private static long parseChinese(String value) {
        Map<Character, Integer> digits = Map.ofEntries(
                Map.entry('零',0), Map.entry('〇',0), Map.entry('一',1), Map.entry('二',2),
                Map.entry('两',2), Map.entry('三',3), Map.entry('四',4), Map.entry('五',5),
                Map.entry('六',6), Map.entry('七',7), Map.entry('八',8), Map.entry('九',9));
        Map<Character, Long> units = Map.of('十',10L,'百',100L,'千',1000L,'万',10000L,'亿',100000000L);
        long total=0, section=0, number=0;
        for(char c:value.toCharArray()) {
            if(digits.containsKey(c)) number=digits.get(c);
            else { long unit=units.getOrDefault(c,0L); if(unit==0)return -1; if(unit<10000){if(number==0)number=1;section+=number*unit;}else{section+=number;total+=section*unit;section=0;}number=0; }
        }
        return total+section+number;
    }

    private static Timing timing(WishDelivery delivery) {
        return switch (delivery) {
            case IMMEDIATE, HIDDEN -> new Timing(WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE);
            case DELAYED -> new Timing(WishStepTiming.DELAYED, 5, WishTriggerType.AFTER_DELAY);
            default -> null;
        };
    }
    private static boolean contains(String text, String... values) { for(String value:values) if(text.contains(value)) return true; return false; }
    private record Timing(WishStepTiming timing, int delaySeconds, WishTriggerType trigger) { }
}
