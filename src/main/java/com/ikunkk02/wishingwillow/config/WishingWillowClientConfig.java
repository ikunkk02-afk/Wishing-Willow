package com.ikunkk02.wishingwillow.config;

import com.ikunkk02.wishingwillow.ai.WishFulfillmentMode;
import com.ikunkk02.wishingwillow.client.cinematic.CinematicFilterIntensity;
import net.minecraftforge.common.ForgeConfigSpec;

public final class WishingWillowClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<WishFulfillmentMode> FULFILLMENT_MODE;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC_MUSIC;
    public static final ForgeConfigSpec.IntValue MUSIC_VOLUME;
    public static final ForgeConfigSpec.BooleanValue TRADE_REVEAL_MUSIC;
    public static final ForgeConfigSpec.BooleanValue WISH_SEQUENCE_MUSIC;
    public static final ForgeConfigSpec.BooleanValue CINEMATIC_TRADE_FILTER;
    public static final ForgeConfigSpec.EnumValue<CinematicFilterIntensity> CINEMATIC_FILTER_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue SHOW_WISH_PROCESSING_HINT;
    public static final ForgeConfigSpec.IntValue WISH_PROCESSING_HINT_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue WISH_STAY_NEARBY_WARNING;
    public static final ForgeConfigSpec.IntValue WISH_STAY_NEARBY_DISTANCE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("experience");
        FULFILLMENT_MODE = builder.comment("All modes preserve the Wish Contract; only the fulfillment style changes.")
                .defineEnum("wishFulfillmentMode", WishFulfillmentMode.ABSURD);
        builder.push("cinematic_music");
        CINEMATIC_MUSIC = builder.define("enabled", true);
        MUSIC_VOLUME = builder.defineInRange("volumePercent", 70, 0, 100);
        TRADE_REVEAL_MUSIC = builder.define("tradeReveal", true);
        WISH_SEQUENCE_MUSIC = builder.define("wishSequence", true);
        builder.pop();
        builder.push("cinematic_filter");
        CINEMATIC_TRADE_FILTER = builder.define("cinematicTradeFilter", true);
        CINEMATIC_FILTER_INTENSITY = builder.defineEnum("cinematicFilterIntensity",
                CinematicFilterIntensity.NORMAL);
        builder.pop();
        builder.push("wish_processing_hints");
        SHOW_WISH_PROCESSING_HINT = builder
                .comment("Show action-bar hints while a wish is being processed by AI.")
                .define("showWishProcessingHint", true);
        WISH_PROCESSING_HINT_INTERVAL = builder
                .comment("Ticks between hint refreshes (20 ticks = 1 second).")
                .defineInRange("wishProcessingHintInterval", 60, 20, 200);
        WISH_STAY_NEARBY_WARNING = builder
                .comment("Warn the player when they wander too far from the wish submission spot.")
                .define("wishStayNearbyWarning", true);
        WISH_STAY_NEARBY_DISTANCE = builder
                .comment("Maximum distance (blocks) from the wish spot before a warning appears.")
                .defineInRange("wishStayNearbyDistance", 20, 8, 64);
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private WishingWillowClientConfig() {}
}
