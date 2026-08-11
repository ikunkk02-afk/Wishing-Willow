package com.ikunkk02.wishingwillow.contract;

import org.junit.jupiter.api.Test;

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
}
