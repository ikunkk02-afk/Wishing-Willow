package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent rules that affect only loaded/future Mob entities and never load chunks or dimensions. */
public final class WishEntitySuppressionSavedData extends SavedData {
    private static final String DATA_NAME = "wishing_willow_entity_suppression";
    private static final int ENTITIES_PER_TICK = 64;
    private static final ArrayDeque<UUID> PENDING = new ArrayDeque<>();
    private final Map<UUID, Rule> rules = new LinkedHashMap<>();

    public static WishEntitySuppressionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                WishEntitySuppressionSavedData::load, WishEntitySuppressionSavedData::new, DATA_NAME);
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WishEntitySuppressionSavedData::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(WishEntitySuppressionSavedData::onServerTick);
    }

    public void add(Rule rule) {
        rules.put(rule.ruleId(), rule.withExcludePlayers(true));
        setDirty();
        WishingWillow.LOGGER.info("Entity suppression created rule={} owner={} group={} scope={}",
                rule.ruleId(), rule.ownerId(), rule.group(), rule.scope());
    }

    public Collection<Rule> rules() { return List.copyOf(rules.values()); }

    public int applyLoaded(MinecraftServer server, Rule rule) {
        int affected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (!rule.appliesTo(level)) continue;
            List<Entity> loaded = new ArrayList<>();
            level.getEntities().getAll().forEach(loaded::add);
            for (Entity entity : loaded) if (apply(rule, entity)) affected++;
        }
        return affected;
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        WishEntitySuppressionSavedData data = get(level.getServer());
        for (Rule rule : data.rules.values()) {
            if (rule.enabled() && rule.preventFuture() && rule.appliesTo(level) && matches(rule.group(), mob)) {
                if (rule.disappearanceMode() == DisappearanceMode.DISCARD) {
                    event.setCanceled(true);
                } else {
                    PENDING.add(mob.getUUID());
                }
                WishingWillow.LOGGER.debug("Entity suppression applied entity={} rule={}", mob.getUUID(), rule.ruleId());
                return;
            }
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        int remaining = ENTITIES_PER_TICK;
        while (remaining-- > 0 && !PENDING.isEmpty()) {
            UUID id = PENDING.removeFirst();
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity != null) { entity.kill(); break; }
            }
        }
    }

    private static boolean apply(Rule rule, Entity entity) {
        if (!(entity instanceof Mob mob) || !matches(rule.group(), mob)) return false;
        if (rule.disappearanceMode() == DisappearanceMode.DISCARD) mob.discard(); else mob.kill();
        WishingWillow.LOGGER.debug("Entity suppression applied entity={} rule={}", mob.getUUID(), rule.ruleId());
        return true;
    }

    static boolean matches(Group group, Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        return switch (group) {
            case ALL_MOBS -> true;
            case HOSTILE, MONSTERS -> mob instanceof Monster || mob.getType().getCategory() == MobCategory.MONSTER;
            case PASSIVE -> mob.getType().getCategory() == MobCategory.CREATURE
                    || mob.getType().getCategory() == MobCategory.WATER_CREATURE
                    || mob.getType().getCategory() == MobCategory.AMBIENT;
            case ANIMALS -> mob instanceof Animal;
            case VILLAGERS -> mob instanceof Villager;
            case NEUTRAL -> !(mob instanceof Monster) && !(mob instanceof Animal) && !(mob instanceof Villager);
        };
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag(); rules.values().stream().map(Rule::save).forEach(list::add);
        tag.put("Rules", list); return tag;
    }

    public static WishEntitySuppressionSavedData load(CompoundTag tag) {
        WishEntitySuppressionSavedData data = new WishEntitySuppressionSavedData();
        for (Tag value : tag.getList("Rules", Tag.TAG_COMPOUND)) {
            Rule rule = Rule.load((CompoundTag) value);
            if (rule != null) data.rules.put(rule.ruleId(), rule.withExcludePlayers(true));
        }
        return data;
    }

    public enum Group { ALL_MOBS, HOSTILE, PASSIVE, NEUTRAL, ANIMALS, MONSTERS, VILLAGERS }
    public enum Scope { CURRENT_DIMENSION, ALL_DIMENSIONS }
    public enum DisappearanceMode { DISCARD, KILL }

    public record Rule(UUID ruleId, UUID ownerId, UUID sourceSessionId, Group group, Scope scope,
                       ResourceLocation dimension, boolean removeExisting, boolean preventFuture,
                       DisappearanceMode disappearanceMode, long createdAt, boolean permanent,
                       boolean enabled, boolean excludePlayers) {
        public Rule withExcludePlayers(boolean value) {
            return new Rule(ruleId, ownerId, sourceSessionId, group, scope, dimension, removeExisting,
                    preventFuture, disappearanceMode, createdAt, permanent, enabled, true);
        }
        boolean appliesTo(ServerLevel level) {
            return enabled && (scope == Scope.ALL_DIMENSIONS || level.dimension().location().equals(dimension));
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putUUID("RuleId", ruleId); tag.putUUID("OwnerId", ownerId);
            tag.putUUID("SourceSessionId", sourceSessionId); tag.putString("Group", group.name());
            tag.putString("Scope", scope.name()); tag.putString("Dimension", dimension.toString());
            tag.putBoolean("RemoveExisting", removeExisting); tag.putBoolean("PreventFuture", preventFuture);
            tag.putString("DisappearanceMode", disappearanceMode.name()); tag.putLong("CreatedAt", createdAt);
            tag.putBoolean("Permanent", permanent); tag.putBoolean("Enabled", enabled); tag.putBoolean("ExcludePlayers", true);
            return tag;
        }
        static Rule load(CompoundTag tag) {
            try { return new Rule(tag.getUUID("RuleId"), tag.getUUID("OwnerId"), tag.getUUID("SourceSessionId"),
                    Group.valueOf(tag.getString("Group")), Scope.valueOf(tag.getString("Scope")),
                    ResourceLocation.tryParse(tag.getString("Dimension")), tag.getBoolean("RemoveExisting"),
                    tag.getBoolean("PreventFuture"), DisappearanceMode.valueOf(tag.getString("DisappearanceMode")),
                    tag.getLong("CreatedAt"), tag.getBoolean("Permanent"), tag.getBoolean("Enabled"), true);
            } catch (RuntimeException error) { return null; }
        }
    }
}
