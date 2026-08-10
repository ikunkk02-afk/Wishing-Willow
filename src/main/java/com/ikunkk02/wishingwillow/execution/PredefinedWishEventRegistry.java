package com.ikunkk02.wishingwillow.execution;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public final class PredefinedWishEventRegistry {
    public static final String STALKER="wishing_willow:stalker_sequence";
    public static final String ENDLESS_NIGHT="wishing_willow:endless_night";
    public static final String OMINOUS_STORM="wishing_willow:ominous_storm";
    private static final Set<String> IDS=Set.of(STALKER,ENDLESS_NIGHT,OMINOUS_STORM);
    private PredefinedWishEventRegistry(){}
    public static boolean contains(String id){return ResourceLocation.tryParse(id)!=null&&IDS.contains(id);}
    public static String stalkerLease(int stepIndex){return STALKER+"@"+stepIndex;}
    public static boolean isStalkerLease(String id){return id!=null&&id.startsWith(STALKER+"@");}
    public static int stalkerStep(String id){try{return Integer.parseInt(id.substring(id.lastIndexOf('@')+1));}catch(RuntimeException ignored){return -1;}}
    public static Set<String> ids(){return IDS;}
}
