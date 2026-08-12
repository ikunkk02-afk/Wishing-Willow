package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Manages permanent entity attraction auras applied by wishes.
 *
 * <p>Rules are global: every loaded {@link LivingEntity} is checked against active
 * attraction rules and steered toward the owning player. The check is batched
 * (max ~16 entities/tick) to avoid pathfinding storms. Newly-joined entities
 * are picked up via {@link EntityJoinLevelEvent}.</p>
 *
 * <p>Rules persist across world saves and server restarts via {@link WishAttractionSavedData}.</p>
 */
public final class WishAttractionSavedData extends net.minecraft.world.level.saveddata.SavedData {
    private static final String DATA_NAME = "wishing_willow_attractions";
    private static final int ENTITIES_PER_TICK = 16;
    private static int scanOffset;

    private final Map<UUID, AttractionRule> rules = new LinkedHashMap<>();
    private final List<LivingEntity> pendingEntities = new ArrayList<>();

    private WishAttractionSavedData() { }

    public static WishAttractionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WishAttractionSavedData::fromNbt,
                WishAttractionSavedData::new,
                DATA_NAME);
    }

    public void addRule(AttractionRule rule) {
        rules.put(rule.ownerId(), rule);
        WishingWillow.LOGGER.info("Attraction rule added owner={} radius={} permanent={}",
                rule.ownerId(), rule.radius(), rule.permanent());
    }

    public void removeRule(UUID ownerId) {
        if (rules.remove(ownerId) != null) {
            WishingWillow.LOGGER.info("Attraction rule removed owner={}", ownerId);
        }
    }

    @Nullable
    public AttractionRule findByOwner(UUID ownerId) {
        return rules.get(ownerId);
    }

    public boolean hasActiveRule(UUID ownerId) {
        return rules.containsKey(ownerId);
    }

    public void onEntityJoin(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        pendingEntities.add(entity);
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new AttractionTickHandler());
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (AttractionRule rule : rules.values()) {
            list.add(rule.toNbt());
        }
        tag.put("Rules", list);
        return tag;
    }

    private static WishAttractionSavedData fromNbt(CompoundTag tag) {
        WishAttractionSavedData data = new WishAttractionSavedData();
        if (tag.contains("Rules")) {
            for (Tag item : tag.getList("Rules", Tag.TAG_COMPOUND)) {
                AttractionRule rule = AttractionRule.fromNbt((CompoundTag) item);
                if (rule != null) data.rules.put(rule.ownerId(), rule);
            }
        }
        return data;
    }

    public record AttractionRule(
            UUID ownerId,
            String sourceSession,
            double radius,
            double strength,
            boolean includeHostile,
            boolean includePassive,
            boolean includeVillagers,
            boolean includeModded,
            boolean permanent,
            long createdGameTime
    ) {
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("OwnerId", ownerId);
            tag.putString("SourceSession", sourceSession);
            tag.putDouble("Radius", radius);
            tag.putDouble("Strength", strength);
            tag.putBoolean("IncludeHostile", includeHostile);
            tag.putBoolean("IncludePassive", includePassive);
            tag.putBoolean("IncludeVillagers", includeVillagers);
            tag.putBoolean("IncludeModded", includeModded);
            tag.putBoolean("Permanent", permanent);
            tag.putLong("CreatedGameTime", createdGameTime);
            return tag;
        }

        @Nullable
        public static AttractionRule fromNbt(CompoundTag tag) {
            if (!tag.hasUUID("OwnerId")) return null;
            return new AttractionRule(
                    tag.getUUID("OwnerId"),
                    tag.getString("SourceSession"),
                    tag.getDouble("Radius"),
                    tag.contains("Strength") ? tag.getDouble("Strength") : 1.0,
                    tag.getBoolean("IncludeHostile"),
                    tag.getBoolean("IncludePassive"),
                    tag.contains("IncludeVillagers") ? tag.getBoolean("IncludeVillagers") : true,
                    tag.contains("IncludeModded") ? tag.getBoolean("IncludeModded") : true,
                    tag.getBoolean("Permanent"),
                    tag.getLong("CreatedGameTime")
            );
        }
    }

    static final class AttractionTickHandler {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
            WishAttractionSavedData data = get(server);
            if (data.rules.isEmpty() && data.pendingEntities.isEmpty()) return;

            // Process newly joined entities
            List<LivingEntity> newlyJoined = List.copyOf(data.pendingEntities);
            data.pendingEntities.clear();
            for (LivingEntity entity : newlyJoined) {
                if (!entity.isAlive()) continue;
                applyRules(server, data, entity);
            }

            // Batch scan loaded entities
            int processed = 0;
            for (ServerLevel level : server.getAllLevels()) {
                var allEntities = level.getEntities().getAll();
                java.util.List<net.minecraft.world.entity.Entity> entityList = new java.util.ArrayList<>();
                allEntities.forEach(entityList::add);
                if (entityList.isEmpty()) continue;
                int start = scanOffset % entityList.size();
                for (int i = 0; i < Math.min(ENTITIES_PER_TICK, entityList.size()); i++) {
                    int idx = (start + i) % entityList.size();
                    if (entityList.get(idx) instanceof LivingEntity living && living.isAlive()) {
                        applyRules(server, data, living);
                        processed++;
                    }
                }
                if (processed >= ENTITIES_PER_TICK) break;
            }
            scanOffset = (scanOffset + ENTITIES_PER_TICK) % Integer.MAX_VALUE;
        }

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (event.getEntity() instanceof LivingEntity living) {
                WishAttractionSavedData data = get(event.getLevel().getServer());
                if (data != null && !data.rules.isEmpty()) {
                    data.onEntityJoin(living);
                }
            }
        }
    }

    private static void applyRules(MinecraftServer server, WishAttractionSavedData data, LivingEntity entity) {
        ServerPlayer player = server.getPlayerList().getPlayer(entity.getUUID());
        if (player != null) return; // Don't attract the player themselves

        for (AttractionRule rule : data.rules.values()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(rule.ownerId());
            if (owner == null) continue;

            BlockPos ownerPos = owner.blockPosition();
            double distance = entity.distanceToSqr(ownerPos.getX(), ownerPos.getY(), ownerPos.getZ());

            // Check if entity is within attraction radius
            double radiusSq = rule.radius() * rule.radius();
            if (distance > radiusSq) continue;

            // Filter by entity type
            if (!shouldAttract(entity, rule)) continue;

            // Near range: strong attraction (32 blocks)
            if (distance <= 1024) { // 32 * 32
                steerToward(entity, owner, rule.strength());
            } else if (distance <= 9216) { // 96 * 96
                // Medium range: gentle steering every few ticks
                if (entity.tickCount % 40 == 0) {
                    steerToward(entity, owner, rule.strength() * 0.5);
                }
            }
            // Far loaded: low frequency
            else if (entity.tickCount % 100 == 0) {
                steerToward(entity, owner, rule.strength() * 0.25);
            }
        }
    }

    private static boolean shouldAttract(LivingEntity entity, AttractionRule rule) {
        var type = entity.getType();
        String category = type.getCategory().getName();
        // Hostile check: if includeHostile is true, attract hostile mobs (monsters)
        if ("monster".equals(category)) return rule.includeHostile();
        // Passive check: animals, water creatures, etc.
        if ("creature".equals(category) || "water_creature".equals(category) || "ambient".equals(category)) return rule.includePassive();
        // Villagers and NPCs
        if ("misc".equals(category)) return rule.includeVillagers();
        // Modded / unknown: include if modded flag is on
        return rule.includeModded();
    }

    private static void steerToward(LivingEntity entity, ServerPlayer target, double strength) {
        if (entity instanceof Mob mob) {
            double dx = target.getX() - mob.getX();
            double dy = target.getY() - mob.getY();
            double dz = target.getZ() - mob.getZ();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1) {
                dx /= len;
                dy /= len;
                dz /= len;
            }
            // Apply gentle velocity steering
            mob.setDeltaMovement(
                    mob.getDeltaMovement().add(dx * strength * 0.05, dy * strength * 0.02, dz * strength * 0.05));
            mob.getLookControl().setLookAt(target, 10, mob.getMaxHeadXRot());
            // Try navigation for mobs that can pathfind
            if (len > 3 && mob.getNavigation().isDone() && entity.tickCount % 40 == 0) {
                mob.getNavigation().moveTo(target, strength * 1.2);
            }
        } else {
            // For non-Mob living entities, use simple velocity steer
            double dx = target.getX() - entity.getX();
            double dy = target.getY() - entity.getY();
            double dz = target.getZ() - entity.getZ();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1) {
                dx /= len;
                dy /= len;
                dz /= len;
            }
            entity.setDeltaMovement(
                    entity.getDeltaMovement().add(dx * strength * 0.03, dy * strength * 0.01, dz * strength * 0.03));
        }
        entity.hurtMarked = true;
    }
}
