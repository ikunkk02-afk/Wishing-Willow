package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import com.ikunkk02.wishingwillow.agent.core.WishAgentLoop;
import com.ikunkk02.wishingwillow.agent.core.WishFinalizationState;
import com.ikunkk02.wishingwillow.agent.platform.MinecraftToolPlatform;
import com.ikunkk02.wishingwillow.agent.platform.StatusEffectCategory;
import com.ikunkk02.wishingwillow.agent.skill.WishAgentSkillLoader;
import com.ikunkk02.wishingwillow.agent.tool.ToolResult;
import com.ikunkk02.wishingwillow.agent.tool.ToolStatus;
import com.ikunkk02.wishingwillow.agent.tool.WishAgentToolRuntime;
import com.ikunkk02.wishingwillow.agent.tool.WishToolCategory;
import com.ikunkk02.wishingwillow.agent.tool.RegisteredWishTool;
import com.ikunkk02.wishingwillow.agent.tool.WishToolDescriptor;
import com.ikunkk02.wishingwillow.agent.tool.search.ToolSearchQuery;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.contract.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.research.*;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WishAgentRuntimeTest {
    @Test void skillLoadsFromTheSingleClasspathLocation() {
        var skills = new WishAgentSkillLoader().load();
        assertEquals(1, skills.size());
        assertEquals(WishAgentSkillLoader.WISH_SKILL, skills.get(0).name());
        assertTrue(skills.get(0).content().contains("finalize_wish_plan"));
    }

    @Test void searchIsActivationGatedRankedAndCapped() {
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        assertEquals(5, runtime.registry().visible(session).size());
        assertEquals(13, runtime.registry().all().stream().filter(tool -> tool.descriptor().category() == WishToolCategory.DISCOVERY).count());
        assertEquals(22, runtime.registry().all().stream().filter(tool -> tool.descriptor().category() == WishToolCategory.PLANNING).count());
        assertEquals(3, runtime.registry().all().stream().filter(tool -> tool.descriptor().category() == WishToolCategory.VERIFICATION).count());
        assertTrue(runtime.registry().searchable(session).isEmpty());
        runtime.registry().find("activate_skill").executor().execute(session, new JsonObject());
        var result = runtime.search().search(session, new ToolSearchQuery("give items registry", 99));
        assertFalse(result.tools().isEmpty());
        assertTrue(result.tools().size() <= 12);
        assertTrue(session.discoveredTools().contains(result.tools().get(0).name()));
    }

    @Test void oneHundredDiamondBlocksAreSplitAndValidatedAsOneLogicalBatch() {
        FakePlatform platform = new FakePlatform();
        WishAgentSession session = session(resourceInterpretation(), platform);
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        JsonObject args = new JsonObject(); args.addProperty("resource_id", "minecraft:diamond_block"); args.addProperty("count", 100);
        ToolResult planned = runtime.registry().find("plan_give_items").executor().execute(session, args);
        assertEquals(ToolStatus.SUCCESS, planned.status());
        assertEquals(List.of(64, 36), session.steps().stream().map(step -> step.parameters().get("count").getAsInt()).toList());
        assertEquals(1, WishPlanBudget.logicalSteps(session.steps()));
        assertEquals(1, session.steps().stream().map(WishPlanStep::batchId).distinct().count());
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("verify_wish_contract").executor().execute(session, new JsonObject()).status());
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("validate_draft_plan").executor().execute(session, new JsonObject()).status());
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("finalize_wish_plan").executor().execute(session, new JsonObject()).status());
    }

    @Test void allBuffsIncludesThirdPartyRegistryEffectsAndRejectsIncompleteCoverage() {
        FakePlatform platform = new FakePlatform();
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        WishAgentSession incomplete = session(buffInterpretation(), platform);
        JsonObject one = effectArgs("minecraft:speed");
        runtime.registry().find("plan_apply_status_effects").executor().execute(incomplete, one);
        assertEquals(ToolStatus.POLICY_REJECTED,
                runtime.registry().find("verify_wish_contract").executor().execute(incomplete, new JsonObject()).status());

        WishAgentSession complete = session(buffInterpretation(), platform);
        JsonObject all = effectArgs("minecraft:speed", "modded:moon_blessing");
        runtime.registry().find("plan_apply_status_effects").executor().execute(complete, all);
        assertEquals(2, complete.steps().size());
        assertTrue(complete.steps().stream().anyMatch(step -> step.candidateReference().registryResource().id().equals("modded:moon_blessing")));
        assertEquals(ToolStatus.SUCCESS,
                runtime.registry().find("verify_wish_contract").executor().execute(complete, new JsonObject()).status());
    }

    @Test void forgedRegistryIdFailsClosedWithoutChangingDraft() {
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        JsonObject args = new JsonObject(); args.addProperty("resource_id", "evil:operator_block"); args.addProperty("count", 1);
        ToolResult result = new WishAgentToolRuntime().registry().find("plan_give_items").executor().execute(session, args);
        assertEquals(ToolStatus.STALE_RESOURCE, result.status());
        assertTrue(session.steps().isEmpty());
    }

    @Test void vanillaBuiltinPlanningUsesATrustedCandidateShape() {
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        WishAgentSession session = session(worldInterpretation(), new FakePlatform());
        JsonObject args = new JsonObject(); args.addProperty("value", "NIGHT");
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("plan_change_time").executor().execute(session, args).status());
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("validate_draft_plan").executor().execute(session, new JsonObject()).status());
    }

    @Test void toolResultEnforcesTheSixtyFourKibLimit() {
        JsonObject data = new JsonObject(); data.addProperty("payload", "x".repeat(70_000));
        ToolResult result = ToolResult.success("BIG", "big", 1, List.of(), data, "");
        String serialized = result.toJson();
        assertTrue(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= ToolResult.MAX_SERIALIZED_BYTES);
        assertTrue(serialized.contains("RESULT_TOO_LARGE"));
    }

    @Test void unknownAndThirdDuplicateToolCallsFailClosedWithinBudgets() {
        ChatModel model = model(request -> ChatResponse.builder().aiMessage(AiMessage.from("",
                List.of(ToolExecutionRequest.builder().id("same").name("invented_shell_tool").arguments("{\"x\":1}").build()))).build());
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertTrue(session.history().stream().anyMatch(entry -> entry.code().equals("UNKNOWN_TOOL")));
        assertTrue(session.history().stream().anyMatch(entry -> entry.code().equals("DUPLICATE_TOOL_CALL")));
        assertEquals(WishAgentSession.MAX_ITERATIONS, session.iterations());
        assertTrue(session.toolCallCount() <= WishAgentSession.MAX_TOTAL_TOOL_CALLS);
    }

    @Test void cancellationStopsBeforeAnyModelOrToolCall() {
        java.util.concurrent.atomic.AtomicInteger modelCalls = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel model = model(request -> { modelCalls.incrementAndGet(); return ChatResponse.builder().aiMessage(AiMessage.from("ignored")).build(); });
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform(), () -> true);
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertEquals(0, modelCalls.get());
        assertEquals(0, session.toolCallCount());
        assertEquals(WishFinalizationState.CANCELLED, run.debug().finalizationState());
    }

    @Test void externalShellAndCodeToolsArePermanentlyRejected() {
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
        RegisteredWishTool shell = new RegisteredWishTool(new WishToolDescriptor("run_powershell",
                "execute code", schema, WishToolCategory.DISCOVERY, false, true, Set.of(), Set.of(), Set.of()),
                (session, args) -> ToolResult.success("BAD", "", 0, List.of(), new JsonObject(), ""));
        runtime.registry().registerExternal(() -> List.of(shell));
        assertNull(runtime.registry().find("run_powershell"));
    }

    private static JsonObject effectArgs(String... ids) {
        JsonObject args = new JsonObject(); JsonArray array = new JsonArray();
        Arrays.stream(ids).forEach(array::add); args.add("effect_ids", array);
        args.addProperty("duration_seconds", 600); args.addProperty("amplifier", 0); return args;
    }

    private static ChatModel model(java.util.function.Function<ChatRequest, ChatResponse> function) {
        return new ChatModel() { @Override public ChatResponse doChat(ChatRequest request) { return function.apply(request); } };
    }

    private static WishAgentSession session(WishInterpretation interpretation, MinecraftToolPlatform platform) {
        return session(interpretation, platform, () -> false);
    }

    private static WishAgentSession session(WishInterpretation interpretation, MinecraftToolPlatform platform,
                                            java.util.function.BooleanSupplier cancelled) {
        RegistrySnapshot registry = new RegistrySnapshot(Map.of(
                RegistryEntryType.ITEM, List.of("minecraft:diamond_block"),
                RegistryEntryType.EFFECT, List.of("minecraft:speed", "modded:moon_blessing")),
                Map.of("minecraft", "minecraft", "modded", "modded"), Set.of());
        CapabilityCatalog catalog = CapabilityCatalog.create(List.of(), List.of(), "READY", "", registry.digest());
        WishContextSnapshot context = new WishContextSnapshot("minecraft:overworld", 0, "DAY", "CLEAR",
                20, 20, 20, 0, "SURVIVAL", "minecraft:plains", 64, "OUTDOORS", "minecraft:air",
                List.of(), List.of(), 0, 0);
        return new WishAgentSession(UUID.randomUUID(), "untrusted wish", interpretation, context, registry,
                new KnowledgeBaseSnapshot(KnowledgeBaseState.READY, false, List.of()),
                ExecutionSettingsSnapshot.permissive(), catalog, platform, cancelled);
    }

    private static WishInterpretation resourceInterpretation() {
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE, "Obtain 100 diamond blocks", List.of(
                new WishHardConstraint(WishConstraintKind.RESOURCE_SEMANTIC, WishConstraintOperator.EQUALS, "diamond_block", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.MINIMUM_QUANTITY, WishConstraintOperator.AT_LEAST, "", 100, 0, true)));
        return interpretation(contract, WishCapability.GIVE_ITEM);
    }

    private static WishInterpretation buffInterpretation() {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE, "Apply all beneficial effects", List.of(
                new WishHardConstraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS,
                        "all_positive_status_effects", 0, 0, true)));
        return interpretation(contract, WishCapability.POWER_BUFF);
    }

    private static WishInterpretation worldInterpretation() {
        return interpretation(new WishContract(WishContractType.CHANGE_WORLD_STATE, "Make it night", List.of(
                new WishHardConstraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS,
                        "time", 0, 0, true))), WishCapability.CHANGE_TIME);
    }

    private static WishInterpretation interpretation(WishContract contract, WishCapability capability) {
        return new WishInterpretation(2, "test", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.CLASSIC, "Verified tools", List.of(FulfillmentStyle.LITERAL), 10),
                "test", WishTone.NEUTRAL, 50, WishDelivery.IMMEDIATE, List.of(capability));
    }

    private static final class FakePlatform implements MinecraftToolPlatform {
        private final Map<RegistryEntryType, List<String>> values = Map.of(
                RegistryEntryType.ITEM, List.of("minecraft:diamond_block"),
                RegistryEntryType.EFFECT, List.of("minecraft:speed", "modded:moon_blessing"));
        @Override public ToolResult listStatusEffects(StatusEffectCategory category, int limit, String cursor) {
            return ToolResult.success("EFFECTS", "complete", 2, statusEffectIds(category), new JsonObject(), "");
        }
        @Override public ToolResult listRegistry(RegistryEntryType type, String semantic, String namespace, int limit, String cursor) {
            return ToolResult.success("REGISTRY", "complete", values.getOrDefault(type, List.of()).size(), values.getOrDefault(type, List.of()), new JsonObject(), "");
        }
        @Override public ToolResult queryRegistry(RegistryEntryType type, String query, String namespace, int limit, String cursor) { return listRegistry(type, query, namespace, limit, cursor); }
        @Override public ToolResult getPlayerState() { return ToolResult.success("STATE", "", 0, List.of(), new JsonObject(), ""); }
        @Override public ToolResult getPlayerEffects() { return getPlayerState(); }
        @Override public ToolResult getPlayerInventorySummary() { return getPlayerState(); }
        @Override public ToolResult inspectModFeature(String modId, String feature) { return ToolResult.notFound("NOT_FOUND", "", ""); }
        @Override public List<CapabilityCandidate> findCapabilityCandidates(String semantic, WishInterpretation interpretation) { return List.of(); }
        @Override public boolean contains(RegistryEntryType type, String id) { return values.getOrDefault(type, List.of()).contains(id); }
        @Override public List<String> statusEffectIds(StatusEffectCategory category) {
            return category == StatusEffectCategory.BENEFICIAL ? values.get(RegistryEntryType.EFFECT) : List.of();
        }
    }
}
