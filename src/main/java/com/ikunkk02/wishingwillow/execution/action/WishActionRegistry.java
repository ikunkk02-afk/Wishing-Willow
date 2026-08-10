package com.ikunkk02.wishingwillow.execution.action;

import com.ikunkk02.wishingwillow.planning.WishActionType;
import java.util.*;

public final class WishActionRegistry {
    private static final WishActionRegistry DEFAULT=createDefault();
    private final EnumMap<WishActionType,WishActionExecutor> executors;
    private WishActionRegistry(EnumMap<WishActionType,WishActionExecutor> executors){this.executors=executors;}
    public static WishActionRegistry defaults(){return DEFAULT;}
    public boolean contains(WishActionType type){return executors.containsKey(type);}
    public WishActionExecutor get(WishActionType type){return executors.get(type);}
    public Set<WishActionType> registered(){return Collections.unmodifiableSet(executors.keySet());}
    private static WishActionRegistry createDefault(){EnumMap<WishActionType,WishActionExecutor> map=new EnumMap<>(WishActionType.class);
        map.put(WishActionType.GIVE_ITEM,StandardWishActionExecutors.giveItem());map.put(WishActionType.REMOVE_ITEM,StandardWishActionExecutors.removeItem());
        map.put(WishActionType.SPAWN_ENTITY,StandardWishActionExecutors.spawnEntity());map.put(WishActionType.DESPAWN_ENTITY,StandardWishActionExecutors.despawnEntity());
        map.put(WishActionType.APPLY_EFFECT,StandardWishActionExecutors.applyEffect());map.put(WishActionType.REMOVE_EFFECT,StandardWishActionExecutors.removeEffect());
        map.put(WishActionType.TELEPORT,StandardWishActionExecutors.teleport());map.put(WishActionType.CHANGE_TIME,StandardWishActionExecutors.changeTime());
        map.put(WishActionType.CHANGE_WEATHER,StandardWishActionExecutors.changeWeather());map.put(WishActionType.PLAY_SOUND,StandardWishActionExecutors.playSound());
        map.put(WishActionType.SPAWN_PARTICLE,StandardWishActionExecutors.spawnParticle());map.put(WishActionType.LIGHTNING,StandardWishActionExecutors.lightning());
        map.put(WishActionType.EXPLOSION,StandardWishActionExecutors.explosion());map.put(WishActionType.CHANGE_BLOCK,StandardWishActionExecutors.changeBlock());
        map.put(WishActionType.REPLACE_BLOCK_AREA,StandardWishActionExecutors.replaceBlockArea());map.put(WishActionType.MODIFY_HEALTH,StandardWishActionExecutors.modifyHealth());
        map.put(WishActionType.MODIFY_HUNGER,StandardWishActionExecutors.modifyHunger());map.put(WishActionType.MODIFY_ATTRIBUTE,StandardWishActionExecutors.modifyAttribute());
        map.put(WishActionType.CHANGE_MOB_TARGET,StandardWishActionExecutors.changeMobTarget());map.put(WishActionType.FOLLOW_PLAYER,StandardWishActionExecutors.followPlayer());
        map.put(WishActionType.AVOID_PLAYER,StandardWishActionExecutors.avoidPlayer());map.put(WishActionType.CHANGE_REPUTATION,StandardWishActionExecutors.changeReputation());
        map.put(WishActionType.START_PREDEFINED_EVENT,StandardWishActionExecutors.predefinedEvent());return new WishActionRegistry(map);}
}
