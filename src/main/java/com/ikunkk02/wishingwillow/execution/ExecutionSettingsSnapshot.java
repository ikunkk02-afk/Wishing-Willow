package com.ikunkk02.wishingwillow.execution;

public record ExecutionSettingsSnapshot(boolean enabled,boolean thirdPartyEntities,
        boolean blockModification,boolean explosions,boolean destructiveExplosions,
        boolean crossDimensionTeleport,boolean debugSafeMode,int maximumDestructiveSeverity,
        boolean canEdit) {
    public static ExecutionSettingsSnapshot current(boolean canEdit){return new ExecutionSettingsSnapshot(
            WishExecutionConfig.ENABLED.get(),WishExecutionConfig.THIRD_PARTY_ENTITIES.get(),
            WishExecutionConfig.BLOCK_MODIFICATION.get(),WishExecutionConfig.EXPLOSIONS.get(),
            WishExecutionConfig.DESTRUCTIVE_EXPLOSIONS.get(),WishExecutionConfig.CROSS_DIMENSION_TELEPORT.get(),
            WishExecutionConfig.DEBUG_SAFE_MODE.get(),WishExecutionConfig.MAX_DESTRUCTIVE_SEVERITY.get(),canEdit);}
    public static ExecutionSettingsSnapshot planning(){return current(false);}
    public static ExecutionSettingsSnapshot permissive(){return new ExecutionSettingsSnapshot(
            true,true,true,true,true,true,false,100,false);}
}
