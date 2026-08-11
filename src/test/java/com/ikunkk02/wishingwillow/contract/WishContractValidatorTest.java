package com.ikunkk02.wishingwillow.contract;

import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.planning.*;
import com.ikunkk02.wishingwillow.execution.PredefinedWishEventRegistry;
import com.ikunkk02.wishingwillow.research.FeatureType;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import com.ikunkk02.wishingwillow.research.VerifiedRegistryResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WishContractValidatorTest {
    @Test void oneStackDoesNotFulfillOneHundredButSixtyFourPlusThirtySixDoes() {
        WishInterpretation wish = resourceWish("diamond_block", 100);
        assertEquals(WishContractValidationState.CONTRACT_NOT_FULFILLED,
                WishContractValidator.validate(wish, List.of(item(0, "minecraft:diamond_block", 64))).state());
        WishContractValidation complete = WishContractValidator.validate(wish, List.of(
                item(0, "minecraft:diamond_block", 64), item(1, "minecraft:diamond_block", 36)));
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED, complete.state());
        assertEquals(100, complete.promisedQuantity());
    }

    @Test void wrongResourceIsRejectedAndSpatialPatternCountsExactly() {
        WishInterpretation gold = resourceWish("gold_block", 100);
        assertEquals(WishContractValidationState.CONTRACT_NOT_FULFILLED,
                WishContractValidator.validate(gold, List.of(item(0, "minecraft:diamond_block", 64),
                        item(1, "minecraft:diamond_block", 36))).state());
        WishPlanStep spatial = step(0, WishActionType.PLACE_BLOCK_PATTERN, WishCapability.BLOCK_CHANGE,
                RegistryEntryType.BLOCK, "minecraft:gold_block", "{\"pattern\":\"ENCLOSURE\",\"count\":100}");
        WishContractValidation result = WishContractValidator.validate(gold, List.of(spatial));
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED, result.state());
        assertEquals(100, result.promisedQuantity());
    }

    @Test void speedMustActuallyIncreaseAndPunishmentDoesNotSubstitute() {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE, "Player becomes extremely fast", List.of(
                new WishHardConstraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS, "movement_speed", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.STATE_DIRECTION, WishConstraintOperator.INCREASE, "increase", 0, 1, true)));
        WishInterpretation wish = interpretation(contract, List.of(WishCapability.PLAYER_ATTRIBUTE));
        WishPlanStep damage = step(0, WishActionType.MODIFY_HEALTH, WishCapability.PLAYER_ATTRIBUTE,
                null, null, "{\"delta\":-10,\"allow_lethal\":false}");
        assertEquals(WishContractValidationState.CONTRACT_NOT_FULFILLED,
                WishContractValidator.validate(wish, List.of(damage)).state());
        WishPlanStep speed = step(0, WishActionType.MODIFY_ATTRIBUTE, WishCapability.PLAYER_ATTRIBUTE,
                null, null, "{\"attribute\":\"MOVEMENT_SPEED\",\"operation\":\"MULTIPLY\",\"amount\":1,\"duration_seconds\":3600}");
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED,
                WishContractValidator.validate(wish, List.of(speed)).state());
    }

    @Test void allBuffsAreDeterministicForTheCategoryActionAndRejectDecoration() {
        WishInterpretation wish = allPositiveEffectsWish();
        WishPlanStep sound = eventStep("minecraft:ambient.cave", WishActionType.PLAY_SOUND,
                WishCapability.SOUND_EVENT, CandidateSourceKind.VANILLA_REGISTRY);
        assertEquals(WishContractValidationState.CONTRACT_NOT_FULFILLED,
                WishContractValidator.validate(wish, List.of(sound)).state());

        WishPlanStep category = step(0, WishActionType.APPLY_EFFECT_CATEGORY, WishCapability.POWER_BUFF,
                null, null, "{\"category\":\"BENEFICIAL\",\"duration_seconds\":600,\"amplifier\":1}");
        WishContractValidation deterministic = WishContractValidator.validate(wish, List.of(category));
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED, deterministic.state());
        assertEquals("ALL_POSITIVE_STATUS_EFFECTS_CATEGORY_PROVEN", deterministic.code());

        WishPlanStep exact = eventStep(PredefinedWishEventRegistry.ALL_POSITIVE_EFFECTS,
                WishActionType.START_PREDEFINED_EVENT, WishCapability.POWER_BUFF,
                CandidateSourceKind.MOD_FEATURE);
        WishContractValidation validation = WishContractValidator.validate(wish, List.of(exact));
        assertEquals(WishContractValidationState.CONTRACT_FULFILLED, validation.state());
        assertEquals("ALL_POSITIVE_STATUS_EFFECTS_PROVEN", validation.code());
    }

    private static WishInterpretation resourceWish(String semantic, int quantity) {
        WishContract contract = new WishContract(WishContractType.OBTAIN_RESOURCE, "Player obtains resources", List.of(
                new WishHardConstraint(WishConstraintKind.RESOURCE_KIND, WishConstraintOperator.EQUALS, "item_or_block", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.RESOURCE_SEMANTIC, WishConstraintOperator.EQUALS, semantic, 0, 0, true),
                new WishHardConstraint(WishConstraintKind.MINIMUM_QUANTITY, WishConstraintOperator.AT_LEAST, "", quantity, 0, true),
                new WishHardConstraint(WishConstraintKind.REAL_RESOURCE, WishConstraintOperator.REQUIRED, "", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.PLAYER_ACCESSIBLE, WishConstraintOperator.REQUIRED, "", 0, 0, true)));
        return interpretation(contract, List.of(WishCapability.GIVE_ITEM, WishCapability.BLOCK_CHANGE));
    }

    private static WishInterpretation allPositiveEffectsWish() {
        WishContract contract = new WishContract(WishContractType.CHANGE_PLAYER_STATE,
                "Every positive status effect is applied to the player", List.of(
                new WishHardConstraint(WishConstraintKind.STATE_METRIC, WishConstraintOperator.EQUALS,
                        "all_positive_status_effects", 0, 0, true),
                new WishHardConstraint(WishConstraintKind.STATE_DIRECTION, WishConstraintOperator.INCREASE,
                        "increase", 0, 1, true),
                new WishHardConstraint(WishConstraintKind.TARGET_SCOPE, WishConstraintOperator.EQUALS,
                        "player", 0, 0, true)));
        return interpretation(contract, List.of(WishCapability.POWER_BUFF));
    }

    private static WishPlanStep eventStep(String feature, WishActionType action,
                                          WishCapability capability, CandidateSourceKind source) {
        CandidateReference reference = new CandidateReference("candidate-001", capability, capability,
                MatchType.EXACT, source, source == CandidateSourceKind.MOD_FEATURE ? "wishing_willow" : "minecraft",
                "1.20.1", feature, FeatureType.PLAYER_SYSTEM, null, 100, 20);
        String parameters = action == WishActionType.START_PREDEFINED_EVENT ? "{\"intensity\":1}" :
                "{\"volume\":1,\"pitch\":1,\"distance\":32}";
        return new WishPlanStep(0, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE, action, capability,
                reference.candidateId(), WishTargetType.PLAYER,
                JsonParser.parseString(parameters).getAsJsonObject(), "test", reference);
    }

    private static WishInterpretation interpretation(WishContract contract, List<WishCapability> capabilities) {
        return new WishInterpretation(2, "test_contract", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.ABSURD, "An absurd but exact method",
                        List.of(FulfillmentStyle.SPATIAL_ABSURDITY), 90), "Exact fulfillment",
                WishTone.ABSURD, 60, WishDelivery.IMMEDIATE, capabilities);
    }

    private static WishPlanStep item(int index, String id, int count) {
        return step(index, WishActionType.GIVE_ITEM, WishCapability.GIVE_ITEM,
                RegistryEntryType.ITEM, id, "{\"count\":" + count + "}");
    }

    private static WishPlanStep step(int index, WishActionType action, WishCapability capability,
                                     RegistryEntryType type, String id, String parameters) {
        VerifiedRegistryResource resource = type == null ? null : new VerifiedRegistryResource(type, id);
        CandidateReference reference = new CandidateReference("candidate-" + index, capability, capability,
                MatchType.EXACT, resource == null ? CandidateSourceKind.VANILLA_BUILTIN : CandidateSourceKind.VANILLA_REGISTRY,
                "minecraft", "1.20.1", capability.name(), type == RegistryEntryType.BLOCK ? FeatureType.BLOCK : FeatureType.ITEM,
                resource, 100, 20);
        return new WishPlanStep(index, WishStepTiming.IMMEDIATE, 0, WishTriggerType.NONE, action, capability,
                reference.candidateId(), WishTargetType.PLAYER, JsonParser.parseString(parameters).getAsJsonObject(), "test", reference);
    }
}
