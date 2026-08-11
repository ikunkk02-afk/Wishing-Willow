package com.ikunkk02.wishingwillow.contract;

public record WishContractValidation(WishContractValidationState state, String code, int promisedQuantity) {
    public boolean fulfilled() { return state == WishContractValidationState.CONTRACT_FULFILLED; }
}
