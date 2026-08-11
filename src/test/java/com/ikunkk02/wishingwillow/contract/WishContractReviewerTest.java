package com.ikunkk02.wishingwillow.contract;

import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.planning.WishEstimatedDuration;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class WishContractReviewerTest {
    @Test void reviewIsBoundToBothCanonicalHashes() {
        String contract = "a".repeat(64), plan = "b".repeat(64);
        String valid = "{\"contract_hash\":\"" + contract + "\",\"plan_hash\":\"" + plan
                + "\",\"verdict\":\"FULFILLED\",\"reason\":\"The semantic promise is directly implemented.\"}";
        assertTrue(WishContractReviewer.parse(valid, contract, plan).fulfilled());
        assertThrows(IllegalArgumentException.class,
                () -> WishContractReviewer.parse(valid, "c".repeat(64), plan));
        assertThrows(IllegalArgumentException.class,
                () -> WishContractReviewer.parse(valid.replace("FULFILLED", "MAYBE"), contract, plan));
    }

    @Test void reviewFutureHasAnIndependentTimeout() {
        AiProvider hanging = new AiProvider() {
            @Override public AiProviderType type() { return AiProviderType.CUSTOM; }
            @Override public CompletableFuture<AiResponse> complete(AiRequest request) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<AiModelListResult> listModels() {
                return CompletableFuture.completedFuture(AiModelListResult.success(List.of()));
            }
        };
        WishContract contract = new WishContract(WishContractType.OTHER, "A reviewed outcome", List.of());
        WishInterpretation interpretation = new WishInterpretation(2, "test", contract.requiredOutcome(), contract,
                new WishFulfillment(WishFulfillmentMode.CLASSIC, "review", List.of(FulfillmentStyle.LITERAL), 1),
                "test", WishTone.NEUTRAL, 10, WishDelivery.IMMEDIATE, List.of(WishCapability.WORLD_EVENT));
        WishPlanDraft draft = new WishPlanDraft(2, "review", WishDelivery.IMMEDIATE, 10,
                WishEstimatedDuration.INSTANT, List.of());
        long started = System.nanoTime();
        assertThrows(CompletionException.class,
                () -> WishContractReviewer.review(hanging, interpretation, draft, Duration.ofMillis(30)).join());
        assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000);
    }
}
