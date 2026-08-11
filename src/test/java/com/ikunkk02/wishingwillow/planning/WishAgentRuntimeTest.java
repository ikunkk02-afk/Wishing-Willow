package com.ikunkk02.wishingwillow.planning;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ikunkk02.wishingwillow.agent.core.WishAgentSession;
import com.ikunkk02.wishingwillow.agent.core.WishAgentLoop;
import com.ikunkk02.wishingwillow.agent.core.WishFinalizationState;
import com.ikunkk02.wishingwillow.agent.core.WishAgentFallbackReason;
import com.ikunkk02.wishingwillow.agent.ai.WishingWillowChatModelAdapter;
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
        assertEquals(24, runtime.registry().all().stream().filter(tool -> tool.descriptor().category() == WishToolCategory.PLANNING).count());
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
        ToolResult missing = runtime.registry().find("verify_wish_contract").executor()
                .execute(incomplete, new JsonObject());
        assertEquals(ToolStatus.POLICY_REJECTED, missing.status());
        assertTrue(missing.data().getAsJsonArray("missing_requirements").size() > 0);
        assertTrue(missing.data().has("repair_hint"));

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
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("verify_wish_contract").executor().execute(session, new JsonObject()).status());
        assertEquals(ToolStatus.SUCCESS, runtime.registry().find("validate_draft_plan").executor().execute(session, new JsonObject()).status());
    }

    @Test void categoryEffectToolAvoidsRegistryEnumeration() {
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        WishAgentSession session = session(buffInterpretation(), new FakePlatform());
        JsonObject args = new JsonObject(); args.addProperty("category", "BENEFICIAL");
        args.addProperty("duration_seconds", 600); args.addProperty("amplifier", 1);
        ToolResult planned = runtime.registry().find("plan_apply_effect_category").executor().execute(session, args);
        assertEquals(ToolStatus.SUCCESS, planned.status());
        assertEquals(1, session.steps().size());
        assertEquals(WishActionType.APPLY_EFFECT_CATEGORY, session.steps().get(0).action());
        assertEquals(ToolStatus.SUCCESS,
                runtime.registry().find("verify_wish_contract").executor().execute(session, new JsonObject()).status());
    }

    @Test void duplicateSearchIsBlockedAndPlanEditForcesVerificationBeforeDiscovery() {
        java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel model = model(request -> {
            ToolExecutionRequest call = switch (turn.getAndIncrement()) {
                case 0 -> call("activate_skill", "{\"why\":\"load SOP\"}");
                case 1 -> call("search_minecraft_tools", "{\"query\":\"give items registry\",\"limit\":12,\"why\":\"find item planner\"}");
                case 2 -> call("search_minecraft_tools", "{\"query\":\"give items registry\",\"limit\":4,\"why\":\"repeat semantic\"}");
                case 3 -> call("plan_give_items", "{\"resource_id\":\"minecraft:diamond_block\",\"count\":100,\"why\":\"fulfill quantity\"}");
                case 4 -> call("query_registry", "{\"registry\":\"ITEM\",\"query\":\"diamond\",\"why\":\"unnecessary requery\"}");
                case 5 -> call("verify_wish_contract", "{\"why\":\"verify edited draft\"}");
                case 6 -> call("validate_draft_plan", "{\"why\":\"validate final revision\"}");
                default -> call("finalize_wish_plan", "{\"why\":\"finalize now\"}");
            };
            return ChatResponse.builder().aiMessage(AiMessage.from("", List.of(call))).build();
        });
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNotNull(run.result().draft());
        assertTrue(session.history().stream().anyMatch(entry -> entry.code().equals("DUPLICATE_TOOL_CALL")));
        assertTrue(session.history().stream().anyMatch(entry -> entry.code().equals("PLAN_EDIT_REQUIRES_VERIFICATION")));
        assertEquals("fulfill quantity", session.history().stream()
                .filter(entry -> entry.toolName().equals("plan_give_items")).findFirst().orElseThrow().why());
    }

    @Test void identicalRegistryQueryCannotLoopIndefinitely() {
        java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel model = model(request -> {
            ToolExecutionRequest call = switch (turn.getAndIncrement()) {
                case 0 -> call("activate_skill", "{}");
                case 1 -> call("search_minecraft_tools", "{\"query\":\"query registry\",\"limit\":12}");
                default -> call("query_registry", "{\"registry\":\"ITEM\",\"query\":\"diamond\",\"namespace\":\"minecraft\",\"limit\":20,\"cursor\":\"\"}");
            };
            return ChatResponse.builder().aiMessage(AiMessage.from("", List.of(call))).build();
        });
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertEquals(WishAgentFallbackReason.DUPLICATE_TOOL_LOOP, run.debug().fallbackReason());
        assertTrue(session.iterations() < WishAgentSession.MAX_ITERATIONS);
        assertEquals(1, session.history().stream()
                .filter(entry -> entry.toolName().equals("query_registry"))
                .filter(entry -> !entry.code().equals("DUPLICATE_TOOL_CALL")).count());
    }

    @Test void toolResultEnforcesTheSixtyFourKibLimit() {
        JsonObject data = new JsonObject(); data.addProperty("payload", "x".repeat(70_000));
        ToolResult result = ToolResult.success("BIG", "big", 1, List.of(), data, "");
        String serialized = result.toJson();
        assertTrue(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= ToolResult.MAX_SERIALIZED_BYTES);
        assertTrue(serialized.contains("RESULT_TOO_LARGE"));
    }

    @Test void repeatedUnknownToolsFailFastWithinBudgets() {
        ChatModel model = model(request -> ChatResponse.builder().aiMessage(AiMessage.from("",
                List.of(ToolExecutionRequest.builder().id("same").name("invented_shell_tool").arguments("{\"x\":1}").build()))).build());
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertTrue(session.history().stream().anyMatch(entry -> entry.code().equals("UNKNOWN_TOOL")));
        assertEquals(WishAgentFallbackReason.UNKNOWN_TOOL_LOOP, run.debug().fallbackReason());
        assertTrue(session.iterations() < WishAgentSession.MAX_ITERATIONS);
        assertTrue(session.toolCallCount() <= WishAgentSession.MAX_TOTAL_TOOL_CALLS);
    }

    @Test void supportedAgentFinalizesWithToolsWithoutFallback() {
        java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel model = model(request -> {
            int value = turn.getAndIncrement();
            ToolExecutionRequest call = switch (value) {
                case 0 -> call("activate_skill", "{}");
                case 1 -> call("search_minecraft_tools", "{\"query\":\"plan give items\",\"limit\":12}");
                case 2 -> call("plan_give_items", "{\"resource_id\":\"minecraft:diamond_block\",\"count\":100}");
                case 3 -> call("verify_wish_contract", "{}");
                case 4 -> call("validate_draft_plan", "{}");
                default -> call("finalize_wish_plan", "{}");
            };
            return ChatResponse.builder().aiMessage(AiMessage.from("", List.of(call))).build();
        });
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNotNull(run.result().draft());
        assertEquals(WishFinalizationState.SUCCESS, run.debug().finalizationState());
        assertEquals(WishAgentFallbackReason.NONE, run.debug().fallbackReason());
        assertEquals(6, session.iterations());
    }

    @Test void twoProseOnlyResponsesTriggerJsonFallbackSignal() {
        ChatModel model = model(request -> ChatResponse.builder().aiMessage(AiMessage.from("I am planning it.")).build());
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertEquals(2, session.iterations());
        assertEquals(WishAgentFallbackReason.MODEL_RETURNED_NO_TOOL_CALL, run.debug().fallbackReason());
    }

    @Test void repeatedMalformedToolArgumentsDoNotCrashAndTriggerFallback() {
        ChatModel model = model(request -> ChatResponse.builder().aiMessage(AiMessage.from("", List.of(
                call("plan_give_items", "{not-json")))).build());
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertEquals(WishAgentFallbackReason.INVALID_TOOL_ARGUMENTS, run.debug().fallbackReason());
        assertEquals(2, session.history().size());
    }

    @Test void agentModelRequestHasItsOwnTimeout() {
        AiProvider hanging = new AiProvider() {
            @Override public AiProviderType type() { return AiProviderType.CUSTOM; }
            @Override public java.util.concurrent.CompletableFuture<AiResponse> complete(AiRequest request) {
                return new java.util.concurrent.CompletableFuture<>();
            }
            @Override public java.util.concurrent.CompletableFuture<AiToolResponse> completeTools(AiToolRequest request) {
                return new java.util.concurrent.CompletableFuture<>();
            }
            @Override public java.util.concurrent.CompletableFuture<AiModelListResult> listModels() {
                return java.util.concurrent.CompletableFuture.completedFuture(AiModelListResult.success(List.of()));
            }
        };
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        ChatModel model = new WishingWillowChatModelAdapter(hanging, 256, java.time.Duration.ofMillis(30),
                () -> session.remainingDuration().toMillis(), session::cancelled);
        long started = System.nanoTime();
        var run = new WishAgentLoop(model, new WishAgentToolRuntime()).run(session);
        assertNull(run.result().draft());
        assertEquals(WishAgentFallbackReason.AI_REQUEST_TIMEOUT, run.debug().fallbackReason());
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000);
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

    private static ToolExecutionRequest call(String name, String arguments) {
        return ToolExecutionRequest.builder().id(UUID.randomUUID().toString()).name(name).arguments(arguments).build();
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

    @Test void planningToolsExposePhysicalDeliverySemanticsInsteadOfNameGuessing() {
        WishAgentToolRuntime runtime = new WishAgentToolRuntime();
        WishToolDescriptor place = runtime.registry().find("plan_place_blocks").descriptor();
        WishToolDescriptor falling = runtime.registry().find("plan_falling_block_shower").descriptor();
        assertTrue(place.unsupportedSemantics().contains("physical_fall"));
        assertTrue(place.supportsSemantics().contains("static_place"));
        assertTrue(falling.supportsSemantics().containsAll(Set.of(
                "fall_from_above", "physical_block_fall", "block_rain", "gravity_delivery")));
        WishAgentSession session = session(resourceInterpretation(), new FakePlatform());
        runtime.registry().find("activate_skill").executor().execute(session, new JsonObject());
        assertEquals("plan_falling_block_shower",
                runtime.search().search(session, new ToolSearchQuery("gravity_delivery", 4)).tools().get(0).name());
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
