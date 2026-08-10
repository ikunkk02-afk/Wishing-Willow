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
    static {
        ForgeConfigSpec.Builder b=new ForgeConfigSpec.Builder();b.push("wish_execution");
        ENABLED=b.comment("Master switch. Planning still works when disabled.").define("enabled",true);
        THIRD_PARTY_ENTITIES=b.define("allowThirdPartyEntities",true);
        BLOCK_MODIFICATION=b.define("allowBlockModification",true);
        EXPLOSIONS=b.define("allowExplosions",true);
        DESTRUCTIVE_EXPLOSIONS=b.define("allowDestructiveExplosions",false);
        CROSS_DIMENSION_TELEPORT=b.define("allowCrossDimensionTeleport",false);
        MAX_DESTRUCTIVE_SEVERITY=b.defineInRange("maximumDestructiveSeverity",80,0,100);
        DEBUG_SAFE_MODE=b.define("debugSafeMode",!FMLEnvironment.production);
        b.pop();SPEC=b.build();
    }
    private WishExecutionConfig(){}
}
