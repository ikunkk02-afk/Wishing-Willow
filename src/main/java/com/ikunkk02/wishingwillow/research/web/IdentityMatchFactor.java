package com.ikunkk02.wishingwillow.research.web;

public record IdentityMatchFactor(String name, double contribution, String detail) {
    public IdentityMatchFactor {
        name = name == null ? "" : name.strip();
        detail = detail == null ? "" : detail.strip();
    }
}
