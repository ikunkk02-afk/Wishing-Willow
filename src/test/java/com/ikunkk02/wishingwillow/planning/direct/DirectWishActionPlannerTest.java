package com.ikunkk02.wishingwillow.planning.direct;

import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.contract.*;
import com.ikunkk02.wishingwillow.execution.ExecutionSettingsSnapshot;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.registry.RegistrySnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DirectWishActionPlannerTest {
    private static final ExecutionSettingsSnapshot SETTINGS = ExecutionSettingsSnapshot.permissive();

    @Test void sixtyFourDiamondsUseDirectDslAndKeepExactQuantity() {
        QueueProvider provider = new QueueProvider(json("""
                {"type":"GIVE_ITEM","target":"SELF","resource":"minecraft:diamond","parameters":{"count":64}}
                """, "CINEMATIC", 85, ""));
        DirectActionPlanningResult result = plan(provider, resourceWish("diamond", 64));

        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        assertEquals(1, provider.calls);
        assertEquals(64, result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.GIVE_ITEM)
                .mapToInt(step -> step.parameters().get("count").getAsInt()).sum());
        assertTrue(result.compiled().directActions().contains("CORE:GIVE_ITEM"));
    }

    @Test void fallingBlockShowerCanonicalizesProviderTuningInsteadOfRejectingTheWish() {
        QueueProvider provider = new QueueProvider(json("""
                {"type":"FALLING_BLOCK_SHOWER","target":"SELF","resource":"minecraft:diamond_block",
                 "parameters":{"count":64,"spawn_height":100,"radius":10.0,"interval_ticks":0,
                 "landing_mode":"deliver_to_player","spread":"random"}}
                """, "CINEMATIC", 85, ""));

        DirectActionPlanningResult result = plan(provider, resourceWish("diamond_block", 64));

        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        assertEquals(1, provider.calls, "safe presentation tuning must not consume the repair attempt");
        WishPlanStep shower = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.FALLING_BLOCK_SHOWER)
                .findFirst().orElseThrow();
        assertEquals(64, shower.parameters().get("count").getAsInt());
        assertEquals(64, shower.parameters().get("spawn_height").getAsInt());
        assertEquals(10, shower.parameters().get("radius").getAsInt());
        assertEquals(1, shower.parameters().get("interval_ticks").getAsInt());
        assertEquals("DELIVER_TO_PLAYER", shower.parameters().get("landing_mode").getAsString());
        assertEquals("RANDOM", shower.parameters().get("spread").getAsString());
    }

    @Test void fallingBlockShowerFillsSafeDefaultsFromTheStructuredContract() {
        QueueProvider provider = new QueueProvider(json("""
                {"type":"FALLING_BLOCK_SHOWER","target":"SELF","resource":"minecraft:diamond_block",
                 "parameters":{}}
                """, "CINEMATIC", 85, ""));

        DirectActionPlanningResult result = plan(provider, resourceWish("diamond_block", 64));

        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        WishPlanStep shower = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.FALLING_BLOCK_SHOWER)
                .findFirst().orElseThrow();
        assertEquals(64, shower.parameters().get("count").getAsInt());
        assertEquals(28, shower.parameters().get("spawn_height").getAsInt());
        assertEquals(10, shower.parameters().get("radius").getAsInt());
        assertEquals(2, shower.parameters().get("interval_ticks").getAsInt());
        assertEquals("DELIVER_TO_PLAYER", shower.parameters().get("landing_mode").getAsString());
    }

    @Test void allPositiveEffectsAreOneRegistryExpandedAction() {
        QueueProvider provider = new QueueProvider(json("""
                {"type":"APPLY_EFFECT_CATEGORY","target":"SELF","resource":"","parameters":{"category":"BENEFICIAL","duration_seconds":600,"amplifier":1}}
                """, "OVERWHELMING", 90, ""));
        DirectActionPlanningResult result = plan(provider, allPositiveEffectsWish());

        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        assertEquals(0, result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.APPLY_EFFECT).count());
        var category = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.APPLY_EFFECT_CATEGORY).findFirst().orElseThrow();
        assertEquals("BENEFICIAL", category.parameters().get("category").getAsString());
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED,
                WishContractValidator.validate(allPositiveEffectsWish(), result.compiled().draft()).state());
    }

    @Test void speedFiveUsesZeroBasedAmplifierFour() {
        QueueProvider provider = new QueueProvider(json("""
                {"type":"APPLY_EFFECT","target":"SELF","resource":"minecraft:speed","parameters":{"duration_seconds":600,"amplifier":4}}
                """, "EXAGGERATED", 80, ""));
        DirectActionPlanningResult result = plan(provider, playerStateWish("movement_speed", WishCapability.POWER_BUFF));
        var speed = result.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.APPLY_EFFECT).findFirst().orElseThrow();
        assertEquals(4, speed.parameters().get("amplifier").getAsInt());
    }

    @Test void weatherAndZombieAreDirectActions() {
        DirectActionPlanningResult weather = plan(new QueueProvider(json("""
                {"type":"CHANGE_WEATHER","target":"WORLD","resource":"","parameters":{"weather":"THUNDER","duration_seconds":600}}
                """, "OMINOUS", 80, "")), worldWish(WishCapability.CHANGE_WEATHER));
        assertTrue(weather.compiled().draft().steps().stream()
                .anyMatch(step -> step.action() == WishActionType.CHANGE_WEATHER));

        DirectActionPlanningResult zombie = plan(new QueueProvider(json("""
                {"type":"SPAWN_ENTITY","target":"AREA","resource":"minecraft:zombie","parameters":{"count":10,"distance_min":8,"distance_max":16}}
                """, "CHAOTIC", 85, "")), worldWish(WishCapability.HOSTILE_ENTITY));
        var spawn = zombie.compiled().draft().steps().stream()
                .filter(step -> step.action() == WishActionType.SPAWN_ENTITY).findFirst().orElseThrow();
        assertEquals(10, spawn.parameters().get("count").getAsInt());
    }

    @Test void malformedJsonGetsExactlyOneRepairAndNeverEscalates() {
        QueueProvider provider = new QueueProvider("{", json("""
                {"type":"GIVE_ITEM","target":"SELF","resource":"minecraft:diamond","parameters":{"count":64}}
                """, "CINEMATIC", 80, ""));
        DirectActionPlanningResult result = plan(provider, resourceWish("diamond", 64));
        assertEquals(DirectActionPlanningResult.State.SUCCESS, result.state());
        assertEquals(2, result.result().attemptsUsed());
        assertEquals(2, provider.calls);

        QueueProvider alwaysInvalid = new QueueProvider("{", "{");
        DirectActionPlanningResult failed = plan(alwaysInvalid, resourceWish("diamond", 64));
        assertEquals(DirectActionPlanningResult.State.FAILED, failed.state());
        assertEquals(WishPlanError.INVALID_JSON, failed.result().error());
        assertEquals(2, alwaysInvalid.calls);
    }

    @Test void unknownRegistryIdAndCommandInjectionFailClosed() {
        String unknown = json("""
                {"type":"GIVE_ITEM","target":"SELF","resource":"evil:not_registered","parameters":{"count":1}}
                """, "CINEMATIC", 80, "");
        QueueProvider unknownProvider = new QueueProvider(unknown, unknown);
        DirectActionPlanningResult unknownResult = plan(unknownProvider, resourceWish("diamond", 1));
        assertEquals(DirectActionPlanningResult.State.FAILED, unknownResult.state());
        assertEquals(WishPlanError.INVALID_REGISTRY, unknownResult.result().error());

        String injected = """
                {"route":"DIRECT_ACTION","summary":"inject","actions":[{"type":"GIVE_ITEM","target":"SELF",
                "resource":"minecraft:diamond","parameters":{"count":1},"command":"/op"}],
                "absurdity":{"style":"NONE","intensity":0,"modifiers":[]}}
                """;
        QueueProvider injectionProvider = new QueueProvider(injected, injected.replace("/op", "/stop"));
        DirectActionPlanningResult injection = plan(injectionProvider, resourceWish("diamond", 1));
        assertEquals(DirectActionPlanningResult.State.FAILED, injection.state());
        assertEquals(WishPlanError.INVALID_JSON, injection.result().error());
    }

    @Test void missingOrIllegalAbsurdityNeverRemovesCoreFulfillment() {
        DirectActionPlanningResult missing = plan(new QueueProvider(json("""
                {"type":"GIVE_ITEM","target":"SELF","resource":"minecraft:diamond","parameters":{"count":1}}
                """, "NONE", 0, "")), resourceWish("diamond", 1));
        assertTrue(missing.compiled().draft().steps().stream()
                .anyMatch(step -> step.action() == WishActionType.GIVE_ITEM));
        assertEquals(WishAbsurdityStyle.CINEMATIC, missing.compiled().absurdity().style());
        assertTrue(missing.compiled().absurdity().intensity() >= 75);
        assertFalse(missing.compiled().absurdity().modifiers().isEmpty());

        String illegalExplosion = """
                {"type":"EXPLOSION","target":"AREA","resource":"","parameters":{"power":8,"destroy_blocks":true,"distance_min":2,"distance_max":4}}
                """;
        ExecutionSettingsSnapshot restrictive = new ExecutionSettingsSnapshot(true, true, true,
                false, false, true, false, 100, false);
        QueueProvider provider = new QueueProvider(json("""
                {"type":"GIVE_ITEM","target":"SELF","resource":"minecraft:diamond","parameters":{"count":1}}
                """, "CHAOTIC", 100, illegalExplosion));
        DirectActionPlanningResult guarded = new DirectWishActionPlanner().plan(provider,
                "give diamond", resourceWish("diamond", 1), emptyCatalog(), registry(), restrictive).join();
        assertEquals(DirectActionPlanningResult.State.SUCCESS, guarded.state());
        assertTrue(guarded.compiled().draft().steps().stream()
                .anyMatch(step -> step.action() == WishActionType.GIVE_ITEM));
        assertFalse(guarded.compiled().draft().steps().stream()
                .anyMatch(step -> step.action() == WishActionType.EXPLOSION));
        assertTrue(guarded.compiled().droppedModifiers() >= 1);
    }

    @Test void routerKeepsUnknownModBehaviorOnComplexAgent() {
        WishInterpretation complex = playerStateWish("special_tracking", WishCapability.STALKING_ENTITY);
        WishRouteDecision decision = new WishActionRouter().select(
                "\u8ba9\u6d1e\u7a74\u5c45\u4f4f\u8005\u4e00\u76f4\u8ddf\u8e2a\u6211\u5e76\u8c03\u7528\u6a21\u7ec4\u7279\u6b8a\u884c\u4e3a", complex);
        assertEquals(WishExecutionRoute.COMPLEX_AGENT, decision.route());

        WishRouteDecision explicitMod = new WishActionRouter().select(
                "\u8c03\u7528\u672a\u77e5\u6a21\u7ec4\u7279\u6b8a\u884c\u4e3a",
                playerStateWish("movement_speed", WishCapability.PLAYER_ATTRIBUTE));
        assertEquals(WishExecutionRoute.COMPLEX_AGENT, explicitMod.route());

        WishRouteDecision simple = new WishActionRouter().select(
                "\u7ed9\u621164\u9897\u94bb\u77f3", resourceWish("diamond", 64));
        assertEquals(WishExecutionRoute.DIRECT_ACTION, simple.route());
    }

    private static DirectActionPlanningResult plan(QueueProvider provider, WishInterpretation interpretation) {
        return new DirectWishActionPlanner().plan(UUID.randomUUID(), provider, "test wish", interpretation,
                emptyCatalog(), registry(), SETTINGS).join();
    }

    private static CapabilityCatalog emptyCatalog() {
        return CapabilityCatalog.create(List.of(), List.of(), "READY", "", registry().digest());
    }

    private static RegistrySnapshot registry() {
        return new RegistrySnapshot(Map.of(
                RegistryEntryType.ITEM, List.of("minecraft:diamond"),
                RegistryEntryType.BLOCK, List.of("minecraft:diamond_block"),
                RegistryEntryType.EFFECT, List.of("minecraft:speed", "minecraft:strength", "modded:blessing"),
                RegistryEntryType.ENTITY, List.of("minecraft:zombie", "minecraft:chicken"),
                RegistryEntryType.SOUND, List.of("minecraft:ui.toast.challenge_complete", "minecraft:entity.lightning_bolt.thunder"),
                RegistryEntryType.PARTICLE, List.of("minecraft:totem_of_undying", "minecraft:end_rod")
        ), Map.of("minecraft", "minecraft", "modded", "modded"), Set.of());
    }

    private static WishInterpretation resourceWish(String semantic, int quantity) {
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE, "Player obtains the item", List.of(
                constraint(WishConstraintKind.RESOURCE_SEMANTIC, WishConstraintOperator.EQUALS, semantic, 0),
                constraint(WishConstraintKind.MINIMUM_QUANTITY, WishConstraintOperator.AT_LEAST, "", quantity),
                constraint(WishConstraintKind.REAL_RESOURCE, WishConstraintOperator.REQUIRED, "", 0),
                constraint(WishConstraintKind.PLAYER_ACCESSIBLE, WishConstraintOperator.REQUIRED, "", 0)));
        return interpretation(contract, List.of(WishCapability.GIVE_ITEM));
    }

    private static WishInterpretation allPositiveEffectsWish() {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE,
                "Player has every beneficial effect", List.of(
                constraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS,
                        "all_positive_status_effects", 0),
                constraint(WishConstraintKind.TARGET_SCOPE, WishConstraintOperator.EQUALS, "player", 0)));
        return interpretation(contract, List.of(WishCapability.POWER_BUFF));
    }

    private static WishInterpretation playerStateWish(String metric, WishCapability capability) {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE,
                "Player state changes", List.of(
                constraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS, metric, 0),
                constraint(WishConstraintKind.STATE_DIRECTION, WishConstraintOperator.INCREASE, "increase", 0)));
        return interpretation(contract, List.of(capability));
    }

    private static WishInterpretation worldWish(WishCapability capability) {
        WishContract contract = new WishContract(WishContractType.CHANGE_WORLD_STATE,
                "World changes as requested", List.of(
                constraint(WishConstraintKind.TARGET_SCOPE, WishConstraintOperator.EQUALS, "world", 0)));
        return interpretation(contract, List.of(capability));
    }

    private static WishHardConstraint constraint(WishConstraintKind kind, WishConstraintOperator operator,
                                                 String semantic, int quantity) {
        return new WishHardConstraint(kind, operator, semantic, quantity, 0, true);
    }

    private static WishInterpretation interpretation(WishContract contract, List<WishCapability> capabilities) {
        return new WishInterpretation(2, "direct_test", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD, "Fulfill exactly, then make it cinematic",
                        List.of(FulfillmentStyle.PHYSICAL_ABSURDITY), 85), "Direct action test",
                WishTone.ABSURD, 100, WishDelivery.IMMEDIATE, capabilities);
    }

    private static String json(String coreAction, String style, int intensity, String modifier) {
        String modifiers = modifier == null || modifier.isBlank() ? "" : modifier.strip();
        return """
                {"route":"DIRECT_ACTION","summary":"Fulfill the exact core wish first","actions":[%s],
                "absurdity":{"style":"%s","intensity":%d,"modifiers":[%s]}}
                """.formatted(coreAction.strip(), style, intensity, modifiers);
    }

    private static final class QueueProvider implements AiProvider {
        private final Queue<String> responses = new ArrayDeque<>();
        private int calls;
        private QueueProvider(String... values) { responses.addAll(List.of(values)); }
        @Override public AiProviderType type() { return AiProviderType.CUSTOM; }
        @Override public CompletableFuture<AiResponse> complete(AiRequest request) {
            calls++;
            return CompletableFuture.completedFuture(new AiResponse(responses.remove(), 200, AiOutputMode.JSON_SCHEMA));
        }
        @Override public CompletableFuture<AiModelListResult> listModels() {
            return CompletableFuture.completedFuture(AiModelListResult.success(List.of("test")));
        }
    }
}
