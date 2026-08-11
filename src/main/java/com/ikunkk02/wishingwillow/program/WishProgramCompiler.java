package com.ikunkk02.wishingwillow.program;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.execution.action.WishActionDefinition;
import com.ikunkk02.wishingwillow.execution.action.WishActionRegistry;
import com.ikunkk02.wishingwillow.planning.CapabilityCatalog;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishEstimatedDuration;
import com.ikunkk02.wishingwillow.planning.WishExecutionRoute;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.planning.WishStepTiming;
import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import com.ikunkk02.wishingwillow.planning.direct.CompiledDirectActionPlan;
import com.ikunkk02.wishingwillow.planning.direct.DirectActionPlan;
import com.ikunkk02.wishingwillow.planning.direct.DirectActionPlanCompiler;
import com.ikunkk02.wishingwillow.planning.direct.DirectWishAction;
import com.ikunkk02.wishingwillow.planning.direct.DirectWishTarget;
import com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityProfile;
import com.ikunkk02.wishingwillow.planning.direct.WishAbsurdityStyle;
import com.ikunkk02.wishingwillow.program.skill.WishSkillRegistry;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministically lowers a validated Wish Program to the existing persisted execution envelope. */
public final class WishProgramCompiler {
    private final WishActionRegistry actions;
    private final DirectActionPlanCompiler legacyCompiler;

    public WishProgramCompiler() {
        this(WishActionRegistry.defaults(), new DirectActionPlanCompiler());
    }

    WishProgramCompiler(WishActionRegistry actions, DirectActionPlanCompiler legacyCompiler) {
        this.actions = actions; this.legacyCompiler = legacyCompiler;
    }

    public CompiledWishProgram compile(WishProgram program, WishInterpretation interpretation,
                                       CapabilityCatalog catalog, RegistrySnapshot registry,
                                       ExecutionSettingsSnapshot settings) {
        WishProgramJson.validate(program, actions);
        WishSkillRegistry.defaults().validateSelection(program);
        if (program.requiresAgent()) throw new IllegalArgumentException("UNKNOWN_CAPABILITY");

        List<Expanded> core = expand(program.coreActions(), false, 0, 0, registry);
        List<Expanded> presentation = expand(program.presentationActions(), true, core.size(), 0, registry);
        List<DirectWishAction> coreActions = core.stream().map(Expanded::action).toList();
        List<DirectWishAction> presentationActions = presentation.stream().map(Expanded::action).toList();
        DirectActionPlan direct = new DirectActionPlan(WishExecutionRoute.DIRECT_ACTION, program.goal(),
                coreActions, new WishAbsurdityProfile(WishAbsurdityStyle.NONE, 0, presentationActions));
        CompiledDirectActionPlan compiled = legacyCompiler.compile(direct, interpretation, catalog, registry, settings);
        WishPlanDraft marked = markProgramSteps(compiled.draft(), core, presentation);
        return new CompiledWishProgram(program, marked, compiled.catalog(),
                program.coreActions().stream().map(WishProgramAction::action).toList(),
                program.presentationActions().stream().map(WishProgramAction::action).toList(),
                program.usesSkill(), false);
    }

    private WishPlanDraft markProgramSteps(WishPlanDraft draft, List<Expanded> core,
                                           List<Expanded> presentationExpanded) {
        List<StepMetadata> coreMetadata = metadata(core, false);
        List<StepMetadata> presentationMetadata = metadata(presentationExpanded, true);
        int coreIndex = 0, presentationIndex = 0;
        List<WishPlanStep> marked = new ArrayList<>();
        for (WishPlanStep step : draft.steps()) {
            boolean presentation = step.selectionReason().contains("optional absurd presentation");
            List<StepMetadata> source = presentation ? presentationMetadata : coreMetadata;
            int index = presentation ? presentationIndex++ : coreIndex++;
            StepMetadata metadata = index < source.size() ? source.get(index)
                    : new StepMetadata(presentation, step.stepIndex(), 0);
            WishStepTiming timing = metadata.delayTicks() > 0 ? WishStepTiming.DELAYED : step.timing();
            int delaySeconds = metadata.delayTicks() > 0 ? (metadata.delayTicks() + 19) / 20 : step.delaySeconds();
            WishTriggerType trigger = metadata.delayTicks() > 0 ? WishTriggerType.AFTER_DELAY : step.trigger();
            String batch = (presentation ? "wp:presentation:g" : "wp:core:g") + metadata.group();
            marked.add(new WishPlanStep(step.stepIndex(), timing, delaySeconds, trigger,
                    step.action(), step.capability(), step.candidateId(), step.target(), step.parameters(),
                    step.selectionReason(), step.candidateReference(), batch));
        }
        return new WishPlanDraft(draft.schemaVersion(), "WishProgram: " + draft.summary(), draft.delivery(),
                draft.severity(), WishEstimatedDuration.INSTANT, marked);
    }

    private static List<StepMetadata> metadata(List<Expanded> expanded, boolean presentation) {
        List<StepMetadata> result = new ArrayList<>();
        for (Expanded value : expanded) {
            int emitted = switch (value.action().type()) {
                case GIVE_ITEM, REMOVE_ITEM -> Math.max(1,
                        (value.action().parameters().get("count").getAsInt() + 63) / 64);
                default -> 1;
            };
            for (int count = 0; count < emitted; count++) {
                result.add(new StepMetadata(presentation, value.group(), count == 0 ? value.delayTicks() : 0));
            }
        }
        return result;
    }

    private List<Expanded> expand(List<WishProgramAction> values, boolean presentation, int group, int depth,
                                  RegistrySnapshot registry) {
        if (depth > 4) throw new IllegalArgumentException("FLOW_DEPTH");
        List<Expanded> result = new ArrayList<>();
        int currentGroup = group;
        int pendingDelay = 0;
        for (WishProgramAction value : values) {
            switch (value.action()) {
                case "delay" -> pendingDelay += value.parameters().get("ticks").getAsInt();
                case "sequence", "parallel", "repeat" -> {
                    List<WishProgramAction> children = children(value.parameters());
                    int repeats = value.action().equals("repeat") ? value.parameters().get("count").getAsInt() : 1;
                    for (int iteration = 0; iteration < repeats; iteration++) {
                        List<Expanded> expanded = expand(children, presentation, currentGroup, depth + 1, registry);
                        if (value.action().equals("parallel")) {
                            int parallelGroup = currentGroup;
                            expanded = expanded.stream().map(child -> new Expanded(child.action(), presentation,
                                    parallelGroup, child.delayTicks())).toList();
                        }
                        if (pendingDelay > 0 && !expanded.isEmpty()) {
                            Expanded first = expanded.get(0);
                            List<Expanded> delayed = new ArrayList<>(expanded);
                            delayed.set(0, new Expanded(first.action(), first.presentation(), first.group(),
                                    first.delayTicks() + pendingDelay));
                            expanded = delayed; pendingDelay = 0;
                        }
                        result.addAll(expanded);
                        currentGroup += value.action().equals("parallel") ? 1 : Math.max(1,
                                expanded.stream().mapToInt(Expanded::group).max().orElse(currentGroup) - currentGroup + 1);
                    }
                }
                default -> {
                    result.add(new Expanded(toDirect(value, registry), presentation, currentGroup, pendingDelay));
                    pendingDelay = 0;
                    currentGroup++;
                }
            }
        }
        return result;
    }

    private DirectWishAction toDirect(WishProgramAction invocation, RegistrySnapshot registry) {
        WishActionDefinition definition = actions.find(invocation.action());
        if (definition == null || definition.legacyType() == null) throw new IllegalArgumentException("UNKNOWN_ACTION");
        JsonObject parameters = invocation.parameters();
        String resource = resource(parameters, definition.legacyType());
        resource = resolveResource(registry, definition.legacyType(), resource);
        DirectWishTarget target = target(parameters, definition.legacyType());
        normalize(parameters, definition.legacyType());
        return new DirectWishAction(definition.legacyType(), target, resource, parameters);
    }

    /** Exact IDs win; otherwise accept one unambiguous local Registry path match without another AI call. */
    private static String resolveResource(RegistrySnapshot registry, WishActionType action, String proposed) {
        RegistryEntryType type = switch (action) {
            case GIVE_ITEM, REMOVE_ITEM -> RegistryEntryType.ITEM;
            case APPLY_EFFECT, REMOVE_EFFECT -> RegistryEntryType.EFFECT;
            case SPAWN_ENTITY, DESPAWN_ENTITY -> RegistryEntryType.ENTITY;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER -> RegistryEntryType.BLOCK;
            case PLAY_SOUND -> RegistryEntryType.SOUND;
            case SPAWN_PARTICLE -> RegistryEntryType.PARTICLE;
            case TELEPORT -> RegistryEntryType.DIMENSION;
            default -> null;
        };
        if (type == null || proposed.isBlank() || registry.contains(type, proposed)) return proposed;
        String wanted = normalizePath(proposed);
        List<String> matches = registry.entries().getOrDefault(type, List.of()).stream()
                .filter(id -> normalizePath(id).equals(wanted)).limit(2).toList();
        return matches.size() == 1 ? matches.get(0) : proposed;
    }

    private static String normalizePath(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String resource(JsonObject parameters, WishActionType type) {
        String key = switch (type) {
            case GIVE_ITEM, REMOVE_ITEM -> "item";
            case APPLY_EFFECT, REMOVE_EFFECT -> "effect";
            case SPAWN_ENTITY, DESPAWN_ENTITY -> "entity";
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, FALLING_BLOCK_SHOWER -> "block";
            case PLAY_SOUND -> "sound";
            case SPAWN_PARTICLE -> "particle";
            case TELEPORT -> parameters.has("dimension") ? "dimension" : "";
            case START_PREDEFINED_EVENT -> "event";
            default -> "";
        };
        if (key.isEmpty() || !parameters.has(key)) return "";
        String value = parameters.remove(key).getAsString();
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static DirectWishTarget target(JsonObject parameters, WishActionType type) {
        if (parameters.has("target")) {
            String value = parameters.remove("target").getAsString().toUpperCase(Locale.ROOT);
            if (Set.of("WORLD", "AREA", "NEARBY_ENTITIES").contains(value)) return DirectWishTarget.valueOf(value);
        }
        return switch (type) {
            case CHANGE_TIME, CHANGE_WEATHER -> DirectWishTarget.WORLD;
            case CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN -> DirectWishTarget.AREA;
            case DESPAWN_ENTITY, CHANGE_MOB_TARGET, FOLLOW_PLAYER, AVOID_PLAYER -> DirectWishTarget.NEARBY_ENTITIES;
            default -> DirectWishTarget.SELF;
        };
    }

    private static void normalize(JsonObject parameters, WishActionType type) {
        rename(parameters, "group", "category");
        switch (type) {
            case APPLY_EFFECT -> { defaultInt(parameters, "duration_seconds", 600); defaultInt(parameters, "amplifier", 0); }
            case APPLY_EFFECT_CATEGORY -> { defaultInt(parameters, "duration_seconds", 600); defaultInt(parameters, "amplifier", 0); }
            case SPAWN_ENTITY -> { defaultInt(parameters, "count", 1); defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case DESPAWN_ENTITY -> { defaultInt(parameters, "max_count", 16); defaultInt(parameters, "radius", 16); }
            case CHANGE_WEATHER -> { upper(parameters, "weather"); defaultInt(parameters, "duration_seconds", 300); }
            case CHANGE_TIME -> { upper(parameters, "value"); if (parameters.has("value")) { String value=parameters.get("value").getAsString(); parameters.addProperty("value", switch(value){case "MIDNIGHT"->"NIGHT";case "NOON"->"DUSK";default->value;}); } }
            case PLAY_SOUND -> { defaultNumber(parameters, "volume", 1.0); defaultNumber(parameters, "pitch", 1.0); defaultInt(parameters, "distance", 32); }
            case SPAWN_PARTICLE -> { defaultInt(parameters, "count", 64); defaultNumber(parameters, "radius", 2.0); }
            case LIGHTNING -> { defaultInt(parameters, "count", 1); defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case EXPLOSION -> { defaultNumber(parameters, "power", 2.0); defaultBoolean(parameters, "destroy_blocks", false); defaultInt(parameters, "distance_min", 8); defaultInt(parameters, "distance_max", 16); }
            case CHANGE_BLOCK -> { defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 8); }
            case REPLACE_BLOCK_AREA -> { defaultInt(parameters, "radius", 3); defaultInt(parameters, "max_blocks", 64); }
            case PLACE_BLOCK_PATTERN -> { upper(parameters, "pattern"); defaultString(parameters, "pattern", "ENCLOSURE"); defaultInt(parameters, "count", 16); }
            case CREATE_STRUCTURE -> { rename(parameters, "structure", "template"); upper(parameters, "template"); parameters.remove("radius"); defaultString(parameters, "template", "SIMPLE_HOUSE"); }
            case MODIFY_HEALTH -> { defaultNumber(parameters, "delta", 0.0); defaultBoolean(parameters, "allow_lethal", false); }
            case MODIFY_HUNGER -> defaultInt(parameters, "delta", 0);
            case MODIFY_ATTRIBUTE -> { upper(parameters, "attribute"); upper(parameters, "operation"); defaultString(parameters, "operation", "ADD"); defaultNumber(parameters, "amount", 1.0); defaultInt(parameters, "duration_seconds", 600); }
            case CHANGE_MOB_TARGET -> { upper(parameters, "disposition"); defaultString(parameters, "disposition", "PLAYER"); defaultInt(parameters, "max_entities", 8); defaultInt(parameters, "radius", 16); }
            case FOLLOW_PLAYER, AVOID_PLAYER -> { defaultInt(parameters, "max_entities", 8); defaultInt(parameters, "radius", 16); defaultInt(parameters, "duration_seconds", 600); }
            case CHANGE_REPUTATION -> { defaultInt(parameters, "delta", 10); defaultInt(parameters, "radius", 16); }
            case START_PREDEFINED_EVENT -> { parameters.remove("duration_seconds"); defaultInt(parameters, "intensity", 3); }
            default -> { }
        }
        if (type == WishActionType.FALLING_BLOCK_SHOWER) {
            rename(parameters, "height", "spawn_height");
            rename(parameters, "horizontal_radius", "radius");
            rename(parameters, "landing", "landing_mode");
            if (!parameters.has("spread")) parameters.addProperty("spread", "RANDOM");
            if (parameters.has("landing_mode")) {
                String landing = parameters.get("landing_mode").getAsString().toUpperCase(Locale.ROOT);
                parameters.addProperty("landing_mode", switch (landing) {
                    case "PLACE_OR_DROP" -> "PLACE_OR_DROP";
                    case "DROP", "DROP_ITEM" -> "DROP_ITEM";
                    case "PLACE" -> "PLACE";
                    default -> "DELIVER_TO_PLAYER";
                });
            }
        }
        if (type == WishActionType.TELEPORT && parameters.has("dimension")) {
            parameters.remove("dimension"); parameters.addProperty("mode", "CANDIDATE_DIMENSION");
        } else if (type == WishActionType.TELEPORT) {
            upper(parameters, "mode"); defaultString(parameters, "mode", "NEARBY_SAFE");
            defaultInt(parameters, "distance_min", 2); defaultInt(parameters, "distance_max", 32);
        }
        if (type == WishActionType.APPLY_EFFECT_CATEGORY && parameters.has("category")) {
            parameters.addProperty("category", parameters.get("category").getAsString().toUpperCase(Locale.ROOT));
        }
    }

    private static void rename(JsonObject object, String from, String to) {
        if (object.has(from)) object.add(to, object.remove(from));
    }

    private static void upper(JsonObject object, String key) {
        if (object.has(key)) object.addProperty(key, object.get(key).getAsString().toUpperCase(Locale.ROOT));
    }
    private static void defaultInt(JsonObject object, String key, int value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultNumber(JsonObject object, String key, double value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultBoolean(JsonObject object, String key, boolean value) { if (!object.has(key)) object.addProperty(key, value); }
    private static void defaultString(JsonObject object, String key, String value) { if (!object.has(key)) object.addProperty(key, value); }

    private static List<WishProgramAction> children(JsonObject parameters) {
        JsonArray array = parameters.getAsJsonArray("actions");
        List<WishProgramAction> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            result.add(new WishProgramAction(object.get("action").getAsString(), object.getAsJsonObject("parameters")));
        }
        return result;
    }

    private record Expanded(DirectWishAction action, boolean presentation, int group, int delayTicks) { }
    private record StepMetadata(boolean presentation, int group, int delayTicks) { }
}
