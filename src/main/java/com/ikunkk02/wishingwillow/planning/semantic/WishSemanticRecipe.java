package com.ikunkk02.wishingwillow.planning.semantic;

import com.ikunkk02.wishingwillow.planning.WishActionType;

/** A controlled high-level semantic that compiles to one server-authoritative Minecraft primitive. */
public enum WishSemanticRecipe {
    FALLING_BLOCK_SHOWER(WishActionType.FALLING_BLOCK_SHOWER);

    private final WishActionType action;

    WishSemanticRecipe(WishActionType action) {
        this.action = action;
    }

    public WishActionType action() {
        return action;
    }
}
