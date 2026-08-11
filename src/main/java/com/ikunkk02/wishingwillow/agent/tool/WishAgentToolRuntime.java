package com.ikunkk02.wishingwillow.agent.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import com.ikunkk02.wishingwillow.agent.core.WishFinalizationState;
import com.ikunkk02.wishingwillow.agent.core.WishVerificationState;
import com.ikunkk02.wishingwillow.agent.platform.StatusEffectCategory;
import com.ikunkk02.wishingwillow.agent.skill.WishAgentSkillLoader;
import com.ikunkk02.wishingwillow.agent.skill.WishAgentSkillManager;
import com.ikunkk02.wishingwillow.agent.tool.search.LangChain4jToolSearchAdapter;
import com.ikunkk02.wishingwillow.agent.tool.search.ToolSearchQuery;
import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.contract.WishContractValidationState;
import com.ikunkk02.wishingwillow.contract.WishContractValidator;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.KnowledgeLevel;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The complete loader-neutral tool catalog. Tools only mutate a draft session. */
public final class WishAgentToolRuntime {
    private static final Set<String> ALWAYS = Set.of("activate_skill", "search_minecraft_tools",
            "verify_wish_contract", "validate_draft_plan", "finalize_wish_plan");
    private final WishToolRegistry registry = new WishToolRegistry();
    private final WishAgentSkillManager skillManager = new WishAgentSkillManager(new WishAgentSkillLoader());
    private final LangChain4jToolSearchAdapter search;
    private final java.util.function.BiPredicate<com.ikunkk02.wishingwillow.ai.WishInterpretation, WishPlanDraft> semanticReviewer;

    public WishAgentToolRuntime() {
        this((interpretation, draft) -> false);
    }

    public WishAgentToolRuntime(java.util.function.BiPredicate<com.ikunkk02.wishingwillow.ai.WishInterpretation, WishPlanDraft> semanticReviewer) {
        this.semanticReviewer = java.util.Objects.requireNonNull(semanticReviewer);
        registerCore();
        registerDiscovery();
        registerPlanning();
        search = new LangChain4jToolSearchAdapter(registry);
    }

    public WishToolRegistry registry() { return registry; }
    public LangChain4jToolSearchAdapter search() { return search; }

    private void registerCore() {
        add("activate_skill", "Activate fulfill-minecraft-wish-with-tools before discovery or planning.",
                WishToolCategory.CONTROL, true, false, (s, a) -> {
                    String content = skillManager.activate(s, str(a, "name", WishAgentSkillLoader.WISH_SKILL));
                    JsonObject data = new JsonObject(); data.addProperty("skill", content);
                    return ToolResult.success("SKILL_ACTIVATED", "Minecraft wish tool skill activated.", 1,
                            List.of(WishAgentSkillLoader.WISH_SKILL), data, "");
                });
        add("search_minecraft_tools", "Search the gated Minecraft discovery and planning tool catalog.",
                WishToolCategory.DISCOVERY, true, true, (s, a) -> {
                    if (!s.skillActivated()) return ToolResult.invalid("SKILL_NOT_ACTIVE", "Activate the skill first.", "Call activate_skill.");
                    var result = search.search(s, new ToolSearchQuery(str(a, "query", ""), integer(a, "limit", 8)));
                    JsonArray tools = new JsonArray();
                    result.tools().forEach(tool -> tools.add(tool.name()));
                    JsonObject data = new JsonObject(); data.add("tools", tools);
                    return ToolResult.success("TOOLS_FOUND", "Matching tools are now visible.", tools.size(),
                            result.tools().stream().map(WishToolDescriptor::name).toList(), data, "");
                });
        add("verify_wish_contract", "Verify the current draft against the immutable wish contract.",
                WishToolCategory.VERIFICATION, true, false, this::verifyContract);
        add("validate_draft_plan", "Validate registry resources, parameters, policy and budgets without executing.",
                WishToolCategory.VERIFICATION, true, false, this::validateDraft);
        add("finalize_wish_plan", "Finalize only the same draft revision already fulfilled and validated.",
                WishToolCategory.VERIFICATION, true, false, (s, a) -> {
                    if (!s.canFinalize()) {
                        s.markFinalization(WishFinalizationState.REJECTED);
                        return ToolResult.invalid("PLAN_NOT_VERIFIED", "Current revision is not both fulfilled and valid.",
                                "Call verify_wish_contract and validate_draft_plan after the last edit.");
                    }
                    s.markFinalization(WishFinalizationState.SUCCESS);
                    return ok("PLAN_FINALIZED", "Draft plan finalized for authoritative server submission.", s.steps().size(), List.of());
                });
    }

    private void registerDiscovery() {
        addRegistryList("list_items", RegistryEntryType.ITEM);
        addRegistryList("list_blocks", RegistryEntryType.BLOCK);
        addRegistryList("list_entities", RegistryEntryType.ENTITY);
        addRegistryList("list_dimensions", RegistryEntryType.DIMENSION);
        addRegistryList("list_sounds", RegistryEntryType.SOUND);
        add("list_status_effects", "List actual status effects by BENEFICIAL, HARMFUL or NEUTRAL category with paging.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> {
                    try { return s.platform().listStatusEffects(StatusEffectCategory.valueOf(str(a, "category", "BENEFICIAL")),
                            limit(a), str(a, "cursor", "")); }
                    catch (IllegalArgumentException exception) { return ToolResult.invalid("INVALID_EFFECT_CATEGORY", exception.getMessage(), "Use BENEFICIAL, HARMFUL, or NEUTRAL."); }
                });
        add("query_registry", "Search an immutable Minecraft registry snapshot.", WishToolCategory.DISCOVERY,
                false, true, (s, a) -> {
                    try { return s.platform().queryRegistry(RegistryEntryType.valueOf(required(a, "registry")),
                            str(a, "query", ""), str(a, "namespace", ""), limit(a), str(a, "cursor", "")); }
                    catch (IllegalArgumentException exception) { return ToolResult.invalid("INVALID_REGISTRY_QUERY", exception.getMessage(), "Use a supported registry name."); }
                });
        add("get_player_safe_state", "Read a redacted player safety summary without coordinates or NBT.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> s.platform().getPlayerState());
        add("get_player_effects", "Read the frozen player status-effect summary.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> s.platform().getPlayerEffects());
        add("get_player_inventory_summary", "Read a frozen inventory summary without NBT.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> s.platform().getPlayerInventorySummary());
        add("find_capability_candidates", "Find verified knowledge/registry candidates for a capability semantic.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> {
                    List<String> ids = new ArrayList<>();
                    for (CapabilityCandidate candidate : s.platform().findCapabilityCandidates(str(a, "semantic", ""), s.interpretation())) {
                        ids.add(s.addCandidate(candidate));
                    }
                    return ok("CANDIDATES_FOUND", "Candidates added to the dynamic catalog.", ids.size(), ids);
                });
        add("inspect_mod_feature", "Inspect frozen mod knowledge for one feature.", WishToolCategory.DISCOVERY,
                false, true, (s, a) -> s.platform().inspectModFeature(required(a, "mod_id"), required(a, "feature")));
    }

    private void registerPlanning() {
        add("plan_give_items", "Plan a verified item grant; counts are split into legal stacks (for example 100 => 64 + 36).",
                WishToolCategory.PLANNING, false, false, this::planGiveItems);
        add("plan_remove_items", "Plan removal of a verified item.", WishToolCategory.PLANNING, false, false,
                (s, a) -> planRegistry(s, a, RegistryEntryType.ITEM, WishActionType.REMOVE_ITEM,
                        capability(a, WishCapability.REMOVE_ITEM), WishTargetType.PLAYER, countParameters(a, 1, 4096)));
        add("plan_apply_status_effects", "Apply a complete list of verified effects as one logical batch and one step per effect.",
                WishToolCategory.PLANNING, false, false, this::planEffects);
        add("plan_remove_status_effects", "Plan removal of one verified effect.", WishToolCategory.PLANNING, false, false,
                (s, a) -> planRegistry(s, a, RegistryEntryType.EFFECT, WishActionType.REMOVE_EFFECT,
                        capability(a, WishCapability.POWER_DEBUFF), WishTargetType.PLAYER, new JsonObject()));
        planResource("plan_spawn_entities", RegistryEntryType.ENTITY, WishActionType.SPAWN_ENTITY, WishCapability.SPAWN_ENTITY, WishTargetType.AREA,
                a -> entityParameters(a));
        planResource("plan_despawn_entities", RegistryEntryType.ENTITY, WishActionType.DESPAWN_ENTITY, WishCapability.SPAWN_ENTITY, WishTargetType.AREA,
                a -> values(a, "radius", 16, "max_count", 8));
        planBuiltin("plan_modify_attribute", WishActionType.MODIFY_ATTRIBUTE, WishCapability.PLAYER_ATTRIBUTE, WishTargetType.PLAYER,
                a -> attributeParameters(a));
        add("plan_teleport", "Plan a safe teleport; CANDIDATE_DIMENSION requires a verified dimension resource_id.",
                WishToolCategory.PLANNING, false, false, (s, a) -> {
                    String mode = str(a, "mode", "NEARBY_SAFE");
                    JsonObject parameters = new JsonObject(); parameters.addProperty("mode", mode);
                    if ("CANDIDATE_DIMENSION".equals(mode)) {
                        return planRegistry(s, a, RegistryEntryType.DIMENSION, WishActionType.TELEPORT,
                                capability(a, WishCapability.DIMENSION_TRAVEL), WishTargetType.PLAYER, parameters);
                    }
                    parameters.addProperty("distance_min", integer(a, "distance_min", 8));
                    parameters.addProperty("distance_max", integer(a, "distance_max", 32));
                    return planBuiltinStep(s, a, WishActionType.TELEPORT, WishCapability.TELEPORT,
                            WishTargetType.PLAYER, parameters);
                });
        planBuiltin("plan_change_time", WishActionType.CHANGE_TIME, WishCapability.CHANGE_TIME, WishTargetType.WORLD,
                a -> stringValue(a, "value", "DAY"));
        planBuiltin("plan_change_weather", WishActionType.CHANGE_WEATHER, WishCapability.CHANGE_WEATHER, WishTargetType.WORLD,
                a -> weatherParameters(a));
        planResource("plan_play_sound", RegistryEntryType.SOUND, WishActionType.PLAY_SOUND, WishCapability.SOUND_EVENT, WishTargetType.PLAYER,
                a -> soundParameters(a));
        planResource("plan_spawn_particles", RegistryEntryType.PARTICLE, WishActionType.SPAWN_PARTICLE, WishCapability.VISUAL_EVENT, WishTargetType.AREA,
                a -> countRadiusParameters(a));
        planBuiltin("plan_lightning", WishActionType.LIGHTNING, WishCapability.LIGHTNING, WishTargetType.AREA,
                a -> distanceCountParameters(a));
        planBuiltin("plan_explosion", WishActionType.EXPLOSION, WishCapability.EXPLOSION, WishTargetType.AREA,
                a -> explosionParameters(a));
        planResource("plan_place_blocks", RegistryEntryType.BLOCK, WishActionType.PLACE_BLOCK_PATTERN, WishCapability.BLOCK_CHANGE, WishTargetType.AREA,
                a -> patternParameters(a));
        planResource("plan_replace_blocks", RegistryEntryType.BLOCK, WishActionType.REPLACE_BLOCK_AREA, WishCapability.BLOCK_CHANGE, WishTargetType.AREA,
                a -> values(a, "radius", 4, "max_blocks", 128));
        planBuiltin("plan_create_structure", WishActionType.CREATE_STRUCTURE, WishCapability.STRUCTURE, WishTargetType.AREA,
                a -> stringValue(a, "template", "SIMPLE_HOUSE"));
        planBehavior("plan_follow_player", WishActionType.FOLLOW_PLAYER, WishCapability.PERSISTENT_FOLLOWER);
        planBehavior("plan_avoid_player", WishActionType.AVOID_PLAYER, WishCapability.MOB_BEHAVIOR);
        planResource("plan_target_player", RegistryEntryType.ENTITY, WishActionType.CHANGE_MOB_TARGET, WishCapability.MOB_BEHAVIOR, WishTargetType.NEARBY_ENTITIES,
                a -> targetParameters(a));
        planBuiltin("plan_change_reputation", WishActionType.CHANGE_REPUTATION, WishCapability.REPUTATION, WishTargetType.NEARBY_ENTITIES,
                a -> values(a, "delta", 10, "radius", 16));
        add("plan_predefined_event", "Plan a compatibility event only from the server whitelist.",
                WishToolCategory.PLANNING, false, false, (s, a) -> {
                    WishCapability cap = capability(a, WishCapability.WORLD_EVENT);
                    String featureName = required(a, "feature");
                    CapabilityCandidate candidate = eventCandidate(featureName, cap);
                    String id = s.addCandidate(candidate);
                    s.addStep(step(s, WishActionType.START_PREDEFINED_EVENT, cap, id, candidate.reference(),
                            WishTargetType.PLAYER, oneInt(a, "intensity", 1), ""));
                    return ok("STEP_PLANNED", "Whitelisted event draft step added.", 1, List.of(featureName));
                });
    }

    private ToolResult planGiveItems(WishAgentSession session, JsonObject args) {
        String id = required(args, "resource_id");
        int count = integer(args, "count", 1);
        if (count < 1 || count > 4096) return ToolResult.invalid("INVALID_COUNT", "Count must be 1..4096.", "Use a smaller positive count.");
        if (!session.platform().contains(RegistryEntryType.ITEM, id)) return stale(id);
        WishCapability capability = capability(args, WishCapability.GIVE_ITEM);
        CapabilityCandidate candidate = candidate(session, RegistryEntryType.ITEM, id, capability);
        String candidateId = session.addCandidate(candidate);
        String batch = "items-" + UUID.randomUUID();
        int remaining = count;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            JsonObject parameters = new JsonObject(); parameters.addProperty("count", stack);
            session.addStep(step(session, WishActionType.GIVE_ITEM, capability, candidateId,
                    candidate.reference(), WishTargetType.PLAYER, parameters, batch));
            remaining -= stack;
        }
        return ok("ITEMS_PLANNED", "Verified item grant split into legal stack steps.", count, List.of(id));
    }

    private ToolResult planEffects(WishAgentSession session, JsonObject args) {
        JsonArray values = args.getAsJsonArray("effect_ids");
        if (values == null || values.isEmpty()) return ToolResult.invalid("EMPTY_EFFECT_SET", "effect_ids must be non-empty.", "Enumerate effects first.");
        if (values.size() > WishPlanValidator.MAX_RAW_STEPS - session.steps().size()) return ToolResult.invalid("TOO_MANY_EFFECTS", "Draft step limit exceeded.", "Narrow the explicit set.");
        List<String> ids = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return ToolResult.invalid("INVALID_EFFECT_ID", "Every effect ID must be a string.", "Use registry IDs.");
            String id = value.getAsString();
            if (!session.platform().contains(RegistryEntryType.EFFECT, id)) return stale(id);
            if (!ids.add(id)) return ToolResult.invalid("DUPLICATE_EFFECT", "Effect list contains a duplicate.", "Provide each effect exactly once.");
        }
        WishCapability capability = capability(args, WishCapability.POWER_BUFF);
        int duration = integer(args, "duration_seconds", 600);
        int amplifier = integer(args, "amplifier", 0);
        String batch = "effects-" + UUID.randomUUID();
        for (String id : ids) {
            CapabilityCandidate candidate = candidate(session, RegistryEntryType.EFFECT, id, capability);
            String candidateId = session.addCandidate(candidate);
            JsonObject parameters = new JsonObject();
            parameters.addProperty("duration_seconds", duration); parameters.addProperty("amplifier", amplifier);
            session.addStep(step(session, WishActionType.APPLY_EFFECT, capability, candidateId,
                    candidate.reference(), WishTargetType.PLAYER, parameters, batch));
        }
        return ok("EFFECTS_PLANNED", "Every verified effect was expanded into an individual step.", ids.size(), ids);
    }

    private ToolResult verifyContract(WishAgentSession session, JsonObject args) {
        var validation = WishContractValidator.validate(session.interpretation(), session.draft(), environment(session));
        if (validation.state() == WishContractValidationState.AI_REVIEW_REQUIRED
                && semanticReviewer.test(session.interpretation(), session.draft())) {
            session.markVerification(WishVerificationState.CONTRACT_FULFILLED);
            return ok("HASH_BOUND_SEMANTIC_REVIEW_FULFILLED",
                    "Independent reviewer accepted the exact contract and draft hashes.", 1, List.of());
        }
        WishVerificationState state = switch (validation.state()) {
            case CONTRACT_FULFILLED -> WishVerificationState.CONTRACT_FULFILLED;
            case CONTRACT_NOT_FULFILLED -> WishVerificationState.NOT_FULFILLED;
            case AI_REVIEW_REQUIRED -> WishVerificationState.REVIEW_REQUIRED;
        };
        session.markVerification(state);
        ToolStatus status = validation.state() == WishContractValidationState.CONTRACT_FULFILLED
                ? ToolStatus.SUCCESS : ToolStatus.POLICY_REJECTED;
        return new ToolResult(status, validation.code(), validation.code(), validation.promisedQuantity(),
                List.of(), List.of(), "Repair the draft and verify the new revision.", new JsonObject(), "");
    }

    private ToolResult validateDraft(WishAgentSession session, JsonObject args) {
        try {
            WishPlanValidator.parseAndValidate(WishPlanJson.toAiJson(session.draft()), session.interpretation(),
                    session.catalog(), environment(session), session.executionSettingsSnapshot());
            session.markValid(true);
            return ok("DRAFT_VALID", "Draft is valid against the frozen environment and safety settings.", session.steps().size(), List.of());
        } catch (IllegalArgumentException exception) {
            session.markValid(false);
            return new ToolResult(ToolStatus.POLICY_REJECTED, exception.getMessage(), "Draft validation failed.", 0,
                    List.of(), List.of(), "Repair the named validation failure and validate again.", new JsonObject(), "");
        }
    }

    private PlanningEnvironment environment(WishAgentSession session) {
        return new PlanningEnvironment() {
            public boolean contains(RegistryEntryType type, String id) { return session.platform().contains(type, id); }
            public boolean modLoaded(String modId, String version) { return true; }
            public Set<String> beneficialStatusEffectIds() { return Set.copyOf(session.platform().statusEffectIds(StatusEffectCategory.BENEFICIAL)); }
        };
    }

    private ToolResult planRegistry(WishAgentSession session, JsonObject args, RegistryEntryType type,
                                    WishActionType action, WishCapability capability, WishTargetType target,
                                    JsonObject parameters) {
        String id = required(args, "resource_id");
        if (!session.platform().contains(type, id)) return stale(id);
        CapabilityCandidate candidate = candidate(session, type, id, capability);
        String candidateId = session.addCandidate(candidate);
        session.addStep(step(session, action, capability, candidateId, candidate.reference(), target, parameters, ""));
        return ok("STEP_PLANNED", "Verified draft step added.", 1, List.of(id));
    }

    private void planResource(String name, RegistryEntryType type, WishActionType action,
                              WishCapability fallback, WishTargetType target, ArgsMapper mapper) {
        add(name, "Plan " + action + " using a verified registry resource.", WishToolCategory.PLANNING,
                false, false, (s, a) -> planRegistry(s, a, type, action, capability(a, fallback), target, mapper.map(a)));
    }

    private void planBuiltin(String name, WishActionType action, WishCapability fallback,
                             WishTargetType target, ArgsMapper mapper) {
        add(name, "Plan " + action + " using a built-in server-authoritative action.", WishToolCategory.PLANNING,
                false, false, (s, a) -> planBuiltinStep(s, a, action, fallback, target, mapper.map(a)));
    }

    private ToolResult planBuiltinStep(WishAgentSession s, JsonObject a, WishActionType action,
                                       WishCapability fallback, WishTargetType target, JsonObject parameters) {
        WishCapability cap = capability(a, fallback);
        CapabilityCandidate candidate = builtinCandidate(s, requiredOr(a, "feature", action.name()), cap);
        String id = s.addCandidate(candidate);
        s.addStep(step(s, action, cap, id, candidate.reference(), target, parameters, ""));
        return ok("STEP_PLANNED", "Built-in draft step added.", 1, List.of(action.name()));
    }

    private void planBehavior(String name, WishActionType action, WishCapability fallback) {
        planResource(name, RegistryEntryType.ENTITY, action, fallback, WishTargetType.NEARBY_ENTITIES,
                a -> behaviorParameters(a));
    }

    private void addRegistryList(String name, RegistryEntryType type) {
        add(name, "List verified " + type.name().toLowerCase() + " registry IDs with cursor paging.",
                WishToolCategory.DISCOVERY, false, true, (s, a) -> s.platform().listRegistry(type,
                        str(a, "semantic", ""), str(a, "namespace", ""), limit(a), str(a, "cursor", "")));
    }

    private void add(String name, String description, WishToolCategory category, boolean always,
                     boolean readOnly, WishTool executor) {
        JsonObject schema = schemaFor(name);
        schema.addProperty("additionalProperties", true);
        registry.register(new RegisteredWishTool(new WishToolDescriptor(name, description, schema, category,
                always || ALWAYS.contains(name), readOnly, EnumSet.noneOf(WishCapability.class), Set.of(), Set.of()), executor));
    }

    private static WishPlanStep step(WishAgentSession session, WishActionType action, WishCapability capability,
                                     String candidateId, CandidateReference reference, WishTargetType target,
                                     JsonObject parameters, String batch) {
        return new WishPlanStep(session.nextStepIndex(), WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE,
                action, capability, candidateId, target, parameters, "Agent selected verified capability.", reference, batch);
    }

    private static CapabilityCandidate candidate(WishAgentSession session, RegistryEntryType type, String resource,
                                                 WishCapability capability) {
        String namespace = resource.contains(":") ? resource.substring(0, resource.indexOf(':')) : "minecraft";
        CandidateSourceKind kind = namespace.equals("minecraft") ? CandidateSourceKind.VANILLA_REGISTRY : CandidateSourceKind.MOD_FEATURE;
        return new CapabilityCandidate("agent-" + UUID.randomUUID(), capability, capability, MatchType.EXACT, kind,
                namespace, namespace, namespace.equals("minecraft") ? "1.20.1" : "", resource, feature(type),
                new VerifiedRegistryResource(type, resource), "Verified frozen registry resource", KnowledgeLevel.VERIFIED,
                1, 1, 0, 100, CapabilityMatcher.risk(capability), 100);
    }

    private static CapabilityCandidate builtinCandidate(WishAgentSession session, String feature, WishCapability capability) {
        return new CapabilityCandidate("agent-" + UUID.randomUUID(), capability, capability, MatchType.EXACT,
                CandidateSourceKind.VANILLA_BUILTIN, "minecraft", "Minecraft", "1.20.1", capability.name(),
                FeatureType.WORLD_SYSTEM, null, "Server-authoritative built-in action", KnowledgeLevel.VERIFIED,
                1, 1, 0, 100, CapabilityMatcher.risk(capability), 100);
    }

    private static CapabilityCandidate eventCandidate(String feature, WishCapability capability) {
        return new CapabilityCandidate("agent-" + UUID.randomUUID(), capability, capability, MatchType.EXACT,
                CandidateSourceKind.MOD_FEATURE, "wishing_willow", "Wishing Willow", "1", feature,
                FeatureType.WORLD_SYSTEM, null, "Server-whitelisted compatibility event", KnowledgeLevel.VERIFIED,
                1, 1, 0, 100, CapabilityMatcher.risk(capability), 100);
    }

    private static FeatureType feature(RegistryEntryType type) {
        return switch (type) {
            case ITEM -> FeatureType.ITEM; case BLOCK -> FeatureType.BLOCK; case ENTITY -> FeatureType.ENTITY;
            case EFFECT -> FeatureType.EFFECT; case DIMENSION -> FeatureType.DIMENSION; case STRUCTURE -> FeatureType.STRUCTURE;
            case SOUND -> FeatureType.SOUND; default -> FeatureType.UNKNOWN;
        };
    }

    private static ToolResult stale(String id) {
        return new ToolResult(ToolStatus.STALE_RESOURCE, "STALE_RESOURCE", "Registry ID is not in the frozen snapshot: " + id,
                0, List.of(), List.of(), "Enumerate or query the registry and use an exact returned ID.", new JsonObject(), "");
    }
    private static ToolResult ok(String code, String message, int affected, List<String> accepted) {
        return ToolResult.success(code, message, affected, accepted, new JsonObject(), "");
    }
    private static int limit(JsonObject a) { return Math.max(1, Math.min(200, integer(a, "limit", 50))); }
    private static WishCapability capability(JsonObject a, WishCapability fallback) {
        String value = str(a, "capability", fallback.name());
        try { return WishCapability.valueOf(value); } catch (IllegalArgumentException ignored) { return fallback; }
    }
    private static JsonObject countParameters(JsonObject a, int min, int max) {
        JsonObject value = new JsonObject(); value.addProperty("count", Math.max(min, Math.min(max, integer(a, "count", min)))); return value;
    }
    private static JsonObject entityParameters(JsonObject a) {
        JsonObject value = new JsonObject(); value.addProperty("count", integer(a, "count", 1));
        value.addProperty("distance_min", integer(a, "distance_min", 8)); value.addProperty("distance_max", integer(a, "distance_max", 16)); return value;
    }
    private static JsonObject radiusParameters(JsonObject a) {
        JsonObject value = new JsonObject(); value.addProperty("radius", integer(a, "radius", 16));
        value.addProperty("max_entities", integer(a, "max_entities", 8)); return value;
    }
    private static JsonObject values(JsonObject a, String first, int firstDefault, String second, int secondDefault) {
        JsonObject value = new JsonObject(); value.addProperty(first, integer(a, first, firstDefault));
        value.addProperty(second, integer(a, second, secondDefault)); return value;
    }
    private static JsonObject oneInt(JsonObject a, String name, int fallback) { JsonObject value = new JsonObject(); value.addProperty(name, integer(a, name, fallback)); return value; }
    private static JsonObject stringValue(JsonObject a, String name, String fallback) { JsonObject value = new JsonObject(); value.addProperty(name, str(a, name, fallback)); return value; }
    private static JsonObject attributeParameters(JsonObject a) { JsonObject v = new JsonObject(); v.addProperty("attribute", str(a,"attribute","MOVEMENT_SPEED")); v.addProperty("operation",str(a,"operation","ADD")); v.addProperty("amount",a.has("amount")?a.get("amount").getAsDouble():0.1); v.addProperty("duration_seconds",integer(a,"duration_seconds",600)); return v; }
    private static JsonObject weatherParameters(JsonObject a) { JsonObject v=stringValue(a,"weather","CLEAR"); v.addProperty("duration_seconds",integer(a,"duration_seconds",300)); return v; }
    private static JsonObject soundParameters(JsonObject a) { JsonObject v=new JsonObject(); v.addProperty("volume",a.has("volume")?a.get("volume").getAsDouble():1); v.addProperty("pitch",a.has("pitch")?a.get("pitch").getAsDouble():1); v.addProperty("distance",integer(a,"distance",16)); return v; }
    private static JsonObject countRadiusParameters(JsonObject a) { JsonObject v=new JsonObject(); v.addProperty("count",integer(a,"count",64)); v.addProperty("radius",a.has("radius")?a.get("radius").getAsDouble():3); return v; }
    private static JsonObject distanceCountParameters(JsonObject a) { JsonObject v=new JsonObject(); v.addProperty("count",integer(a,"count",1)); v.addProperty("distance_min",integer(a,"distance_min",8)); v.addProperty("distance_max",integer(a,"distance_max",24)); return v; }
    private static JsonObject explosionParameters(JsonObject a) { JsonObject v=distanceCountParameters(a); v.remove("count"); v.addProperty("power",a.has("power")?a.get("power").getAsDouble():2); v.addProperty("destroy_blocks",a.has("destroy_blocks")&&a.get("destroy_blocks").getAsBoolean()); return v; }
    private static JsonObject patternParameters(JsonObject a) { JsonObject v=stringValue(a,"pattern","ENCLOSURE"); v.addProperty("count",integer(a,"count",64)); return v; }
    private static JsonObject behaviorParameters(JsonObject a) { JsonObject v=values(a,"radius",16,"max_entities",4); v.addProperty("duration_seconds",integer(a,"duration_seconds",600)); return v; }
    private static JsonObject targetParameters(JsonObject a) { JsonObject v=values(a,"radius",16,"max_entities",4); v.addProperty("disposition",str(a,"disposition","PLAYER")); return v; }
    private static JsonObject copy(JsonObject source, String... names) {
        JsonObject result = new JsonObject();
        for (String name : names) if (source.has(name)) result.add(name, source.get(name).deepCopy());
        return result;
    }
    private static int integer(JsonObject object, String name, int fallback) {
        try { return object.has(name) ? object.get(name).getAsInt() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String str(JsonObject object, String name, String fallback) {
        try { return object.has(name) ? object.get(name).getAsString().strip() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String required(JsonObject object, String name) {
        String value = str(object, name, ""); if (value.isBlank()) throw new IllegalArgumentException("MISSING_" + name.toUpperCase()); return value;
    }
    private static String requiredOr(JsonObject object, String name, String fallback) {
        String value = str(object, name, fallback); return value.isBlank() ? fallback : value;
    }

    private static JsonObject schemaFor(String name) {
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
        JsonObject properties = new JsonObject(); schema.add("properties", properties);
        switch (name) {
            case "activate_skill" -> stringProperty(properties, "name", "fulfill-minecraft-wish-with-tools");
            case "search_minecraft_tools" -> { stringProperty(properties, "query", "items, effects, entities, teleport, policy"); intProperty(properties, "limit"); }
            case "list_status_effects" -> { stringProperty(properties, "category", "BENEFICIAL|HARMFUL|NEUTRAL"); intProperty(properties, "limit"); stringProperty(properties, "cursor", "nextCursor"); }
            case "list_items", "list_blocks", "list_entities", "list_dimensions", "list_sounds" -> {
                stringProperty(properties, "semantic", "search text"); stringProperty(properties, "namespace", "minecraft or mod namespace");
                intProperty(properties, "limit"); stringProperty(properties, "cursor", "nextCursor");
            }
            case "query_registry" -> { stringProperty(properties, "registry", "ITEM|BLOCK|ENTITY|EFFECT|SOUND|PARTICLE|BIOME|STRUCTURE|DIMENSION"); stringProperty(properties, "query", "search text"); stringProperty(properties, "namespace", "optional namespace"); intProperty(properties, "limit"); stringProperty(properties, "cursor", "nextCursor"); }
            case "find_capability_candidates" -> stringProperty(properties, "semantic", "capability or feature");
            case "inspect_mod_feature" -> { stringProperty(properties, "mod_id", "mod id"); stringProperty(properties, "feature", "feature name"); }
            case "plan_give_items", "plan_remove_items" -> { stringProperty(properties, "resource_id", "verified item ID"); intProperty(properties, "count"); stringProperty(properties, "capability", "WishCapability enum"); }
            case "plan_apply_status_effects" -> { arrayProperty(properties, "effect_ids"); intProperty(properties, "duration_seconds"); intProperty(properties, "amplifier"); stringProperty(properties, "capability", "POWER_BUFF or another allowed capability"); }
            case "plan_remove_status_effects" -> stringProperty(properties, "resource_id", "verified effect ID");
            case "plan_spawn_entities", "plan_despawn_entities", "plan_play_sound", "plan_spawn_particles",
                    "plan_place_blocks", "plan_replace_blocks", "plan_target_player", "plan_follow_player", "plan_avoid_player" -> {
                stringProperty(properties, "resource_id", "verified registry ID"); stringProperty(properties, "capability", "WishCapability enum");
            }
            default -> stringProperty(properties, "capability", "WishCapability enum when contract-derived override is needed");
        }
        if (name.startsWith("plan_")) {
            for (String field : List.of("mode", "dimension", "value", "weather", "attribute", "operation",
                    "pattern", "template", "disposition", "feature")) {
                if (!properties.has(field)) stringProperty(properties, field, "action parameter");
            }
            for (String field : List.of("count", "duration_seconds", "amplifier", "distance_min", "distance_max",
                    "radius", "max_entities", "max_blocks", "intensity", "delta")) {
                if (!properties.has(field)) intProperty(properties, field);
            }
            for (String field : List.of("amount", "volume", "pitch", "power")) {
                if (!properties.has(field)) numberProperty(properties, field);
            }
            JsonObject bool = new JsonObject(); bool.addProperty("type", "boolean");
            if (!properties.has("destroy_blocks")) properties.add("destroy_blocks", bool);
        }
        return schema;
    }

    private static void stringProperty(JsonObject properties, String name, String description) {
        JsonObject value = new JsonObject(); value.addProperty("type", "string"); value.addProperty("description", description); properties.add(name, value);
    }
    private static void intProperty(JsonObject properties, String name) {
        JsonObject value = new JsonObject(); value.addProperty("type", "integer"); properties.add(name, value);
    }
    private static void numberProperty(JsonObject properties, String name) {
        JsonObject value = new JsonObject(); value.addProperty("type", "number"); properties.add(name, value);
    }
    private static void arrayProperty(JsonObject properties, String name) {
        JsonObject value = new JsonObject(); value.addProperty("type", "array"); JsonObject item = new JsonObject(); item.addProperty("type", "string"); value.add("items", item); properties.add(name, value);
    }

    @FunctionalInterface private interface ArgsMapper { JsonObject map(JsonObject arguments); }
}
