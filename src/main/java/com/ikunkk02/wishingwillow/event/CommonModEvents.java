package com.ikunkk02.wishingwillow.event;

import com.ikunkk02.wishingwillow.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

public final class CommonModEvents {
    private CommonModEvents() {
    }

    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(ModItems.WISHING_WILLOW);
        }
    }
}
