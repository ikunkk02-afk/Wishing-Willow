package com.ikunkk02.wishingwillow.execution;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class WishExecutionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue THIRD_PARTY_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue BLOCK_MODIFICATION;
    public static final ForgeConfigSpec.BooleanValue EXPLOSIONS;
    public static final ForgeConfigSpec.BooleanValue DESTRUCTIVE_EXPLOSIONS;
    public static final ForgeConfigSpec.BooleanValue CROSS_DIMENSION_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_SAFE_MODE;
    public static final ForgeConfigSpec.IntValue MAX_DESTRUCTIVE_SEVERITY;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_INITIAL_COUNT;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_MINIMUM_COUNT;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_TARGET_COUNT;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_TELEPORT_DISTANCE;
    public static final ForgeConfigSpec.IntValue NEVER_ALONE_REFILL_INTERVAL;
    static {
        ForgeConfigSpec.Builder b=new ForgeConfigSpec.Builder();b.push("wish_execution");
        ENABLED=b.comment("Master switch. Planning still works when disabled.").define("enabled",true);
        THIRD_PARTY_ENTITIES=b.define("allowThirdPartyEntities",true);
        BLOCK_MODIFICATION=b.define("allowBlockModification",true);
        EXPLOSIONS=b.define("allowExplosions",true);
        DESTRUCTIVE_EXPLOSIONS=b.define("allowDestructiveExplosions",false);
        CROSS_DIMENSION_TELEPORT=b.define("allowCrossDimensionTeleport",false);
        MAX_DESTRUCTIVE_SEVERITY=b.defineInRange("maximumDestructiveSeverity",80,0,100);
        DEBUG_SAFE_MODE=b.define("debugSafeMode",false);
        NEVER_ALONE_INITIAL_COUNT=b.defineInRange("neverAloneInitialCount",24,1,40);
        NEVER_ALONE_MINIMUM_COUNT=b.defineInRange("neverAloneMinimumCount",12,1,24);
        NEVER_ALONE_TARGET_COUNT=b.defineInRange("neverAloneTargetCount",24,1,32);
        NEVER_ALONE_SEARCH_RADIUS=b.defineInRange("neverAloneSearchRadius",128,32,256);
        NEVER_ALONE_TELEPORT_DISTANCE=b.defineInRange("neverAloneTeleportDistance",40,16,64);
        NEVER_ALONE_REFILL_INTERVAL=b.defineInRange("neverAloneRefillInterval",100,20,1200);
        b.pop();SPEC=b.build();
    }
    private WishExecutionConfig(){}
}
