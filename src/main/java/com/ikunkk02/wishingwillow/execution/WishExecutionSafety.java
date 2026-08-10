package com.ikunkk02.wishingwillow.execution;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class WishExecutionSafety {
    private WishExecutionSafety(){}
    public static boolean validExplosionPower(double power){return Double.isFinite(power)&&power>=0.1&&power<=8.0;}
    public static boolean validBlockLimit(int radius,int blocks){return radius>=1&&radius<=16&&blocks>=1&&blocks<=2048;}
    public static boolean validItemCount(int count){return count>=1&&count<=64;}
    public static UUID stableAttributeModifierId(UUID executionId,int stepIndex,String attribute){return UUID.nameUUIDFromBytes((executionId+":"+stepIndex+":"+attribute).getBytes(StandardCharsets.UTF_8));}
}
