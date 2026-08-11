package com.ikunkk02.wishingwillow.contract;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ikunkk02.wishingwillow.ai.*;
import com.ikunkk02.wishingwillow.planning.WishPlanDraft;
import com.ikunkk02.wishingwillow.planning.WishPlanJson;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** A second, isolated semantic pass. Hash echoing prevents a review from being reused for another contract or plan. */
public final class WishContractReviewer {
    private static final Set<String> FIELDS = Set.of("contract_hash", "plan_hash", "verdict", "reason");
    private WishContractReviewer() {}

    public static CompletableFuture<WishContractReview> review(AiProvider provider,
                                                               WishInterpretation interpretation,
                                                               WishPlanDraft plan) {
        String contractHash = WishContractHasher.contractHash(interpretation);
        String planHash = WishContractHasher.planHash(plan);
        JsonObject input = new JsonObject();
        input.addProperty("contract_hash", contractHash); input.addProperty("plan_hash", planHash);
        input.add("wish_contract", JsonParser.parseString(WishInterpretationValidator.toJson(interpretation))
                .getAsJsonObject().get("contract"));
        input.add("plan", JsonParser.parseString(WishPlanJson.toAiJson(plan)));
        AiRequest request = new AiRequest("""
                You are the independent Wish Contract reviewer. Decide only whether the supplied plan necessarily
                makes required_outcome and every hard constraint true. Punishment, mood, danger, or partial progress
                never substitutes for fulfillment. Echo both hashes exactly. Return only the JSON object.
                """, "<UNTRUSTED_CONTRACT_REVIEW_JSON>\n" + input + "\n</UNTRUSTED_CONTRACT_REVIEW_JSON>",
                500, AiOutputMode.JSON_SCHEMA, schema());
        return provider.complete(request).thenApply(response -> parse(response.assistantContent(), contractHash, planHash));
    }

    public static WishContractReview parse(String raw, String contractHash, String planHash) {
        try {
            JsonObject value = JsonParser.parseString(raw).getAsJsonObject();
            if (!value.keySet().equals(FIELDS)) throw new IllegalArgumentException("INVALID_CONTRACT_REVIEW");
            WishContractReview review = new WishContractReview(value.get("contract_hash").getAsString(),
                    value.get("plan_hash").getAsString(), WishContractReviewVerdict.valueOf(value.get("verdict").getAsString()),
                    value.get("reason").getAsString());
            if (!review.contractHash().equals(contractHash) || !review.planHash().equals(planHash)
                    || review.reason().isBlank() || review.reason().length() > 512) throw new IllegalArgumentException("INVALID_CONTRACT_REVIEW");
            return review;
        } catch (RuntimeException e) { throw new IllegalArgumentException("INVALID_CONTRACT_REVIEW"); }
    }

    private static JsonObject schema() {
        return JsonParser.parseString("""
                {"type":"object","additionalProperties":false,"required":["contract_hash","plan_hash","verdict","reason"],
                 "properties":{"contract_hash":{"type":"string","pattern":"^[0-9a-f]{64}$"},
                 "plan_hash":{"type":"string","pattern":"^[0-9a-f]{64}$"},
                 "verdict":{"type":"string","enum":["FULFILLED","NOT_FULFILLED"]},
                 "reason":{"type":"string","minLength":1,"maxLength":512}}}
                """).getAsJsonObject();
    }
}
