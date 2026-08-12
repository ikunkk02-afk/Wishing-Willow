package com.ikunkk02.wishingwillow.ai.prompt;

import java.util.List;

/** Cached invariant AI context. Dynamic player/world fields are never cached here. */
public record WishAiContextSnapshot(String minecraftVersion, String loader,
                                    List<String> installedMods, String installedModsDigest,
                                    String actionsDigest, String skillsDigest) {
    public WishAiContextSnapshot {
        installedMods = List.copyOf(installedMods);
    }
}