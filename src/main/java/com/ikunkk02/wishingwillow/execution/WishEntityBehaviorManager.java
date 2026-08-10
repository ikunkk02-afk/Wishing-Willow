package com.ikunkk02.wishingwillow.execution;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class WishEntityBehaviorManager {
    public enum Mode { FOLLOW, AVOID, TARGET }
    private record Behavior(UUID execution,UUID entity,UUID player,Mode mode,double speed,double minDistance,long expires){}
    private static final Map<UUID,Behavior> ACTIVE=new HashMap<>();
    private WishEntityBehaviorManager(){}
    public static void bind(UUID execution,UUID entity,UUID player,Mode mode,double speed,double minDistance,long expires){ACTIVE.put(entity,new Behavior(execution,entity,player,mode,speed,minDistance,expires));}
    public static void tick(MinecraftServer server,long now){if(now%10!=0)return;Iterator<Behavior> iterator=ACTIVE.values().iterator();while(iterator.hasNext()){Behavior b=iterator.next();if(now>=b.expires){iterator.remove();continue;}ServerPlayer player=server.getPlayerList().getPlayer(b.player);if(player==null)continue;ServerLevel level=player.serverLevel();Entity value=level.getEntity(b.entity);if(!(value instanceof Mob mob)){if(value!=null)iterator.remove();continue;}try{double distance=mob.distanceTo(player);switch(b.mode){case TARGET->mob.setTarget(player);case FOLLOW->{if(distance>b.minDistance)mob.getNavigation().moveTo(player,b.speed);mob.getLookControl().setLookAt(player);}case AVOID->{if(distance<b.minDistance){Vec3 away=mob.position().subtract(player.position()).normalize().scale(b.minDistance*1.5);mob.getNavigation().moveTo(mob.getX()+away.x,mob.getY(),mob.getZ()+away.z,b.speed);}}}}catch(Throwable ignored){iterator.remove();}}}
    public static void clear(){ACTIVE.clear();}
}
