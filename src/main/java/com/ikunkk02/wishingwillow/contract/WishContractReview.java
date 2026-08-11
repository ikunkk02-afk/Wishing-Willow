package com.ikunkk02.wishingwillow.contract;

public record WishContractReview(String contractHash, String planHash, WishContractReviewVerdict verdict, String reason) {
    public boolean fulfilled() { return verdict == WishContractReviewVerdict.FULFILLED; }
}
