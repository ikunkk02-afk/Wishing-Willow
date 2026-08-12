package com.ikunkk02.wishingwillow.omen;

import com.ikunkk02.wishingwillow.ai.WishCapability;
import com.ikunkk02.wishingwillow.ai.WishDelivery;
import com.ikunkk02.wishingwillow.ai.WishInterpretation;
import com.ikunkk02.wishingwillow.planning.WishActionType;
import com.ikunkk02.wishingwillow.planning.WishPlan;
import com.ikunkk02.wishingwillow.planning.WishPlanStep;
import com.ikunkk02.wishingwillow.ai.FulfillmentStyle;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Selects one deliberately vague, localised omen from the already accepted plan.
 * No free-form interpretation or plan text is read here, so the wire payload cannot
 * disclose the twisted outcome, loophole, summary, parameters, or selection reason.
 */
public final class WishOmenGenerator {
    private static final Map<WishOmenCategory, Integer> PRIORITY = priorityMap();
    private static final Map<WishOmenCategory, Integer> POOL_SIZES = poolSizes();

    private WishOmenGenerator() {
    }

    public static WishOmen generate(UUID sessionId, WishInterpretation interpretation, WishPlan plan) {
        WishOmenCategory category = interpretation.schemaVersion() >= 2
                ? categoryForContract(interpretation) : selectCategory(interpretation.requiredCapabilities(), plan.steps());
        WishDelivery delivery = plan.delivery();
        long seed = stableSeed(sessionId, category, delivery);
        boolean useDelivery = delivery == WishDelivery.HIDDEN
                || (delivery != WishDelivery.IMMEDIATE && Math.floorMod(seed >>> 9, 3L) != 0L);
        String pool;
        int size;
        if (useDelivery) {
            pool = "delivery." + delivery.name().toLowerCase();
            size = delivery == WishDelivery.IMMEDIATE ? POOL_SIZES.get(category) : 4;
        } else {
            pool = "capability." + category.name().toLowerCase();
            size = POOL_SIZES.get(category);
        }
        // Tone affects only which already-vague local line is chosen. Its ordinal is never sent.
        int variant = 1 + (int) Math.floorMod(seed ^ ((long) interpretation.tone().ordinal() << 32), size);
        int delay = 40 + (int) Math.floorMod(seed >>> 17, 61L);
        return new WishOmen(sessionId, category, "omen.wishing_willow." + pool + "." + variant, delay);
    }

    public static WishOmenCategory selectCategory(List<WishCapability> required, List<WishPlanStep> steps) {
        WishOmenCategory best = WishOmenCategory.GENERAL;
        int bestPriority = priority(best);
        int bestRequired = Integer.MAX_VALUE;
        int bestStep = Integer.MAX_VALUE;
        for (WishPlanStep step : steps) {
            WishOmenCategory candidate = categoryFor(step.capability(), step.action());
            int candidatePriority = priority(candidate);
            int requiredIndex = required.indexOf(step.capability());
            if (requiredIndex < 0) {
                requiredIndex = Integer.MAX_VALUE;
            }
            if (candidatePriority < bestPriority
                    || (candidatePriority == bestPriority && requiredIndex < bestRequired)
                    || (candidatePriority == bestPriority && requiredIndex == bestRequired
                    && step.stepIndex() < bestStep)) {
                best = candidate;
                bestPriority = candidatePriority;
                bestRequired = requiredIndex;
                bestStep = step.stepIndex();
            }
        }
        return best;
    }

    public static int poolSize(WishOmenCategory category) {
        return POOL_SIZES.get(category);
    }

    private static WishOmenCategory categoryForContract(WishInterpretation interpretation) {
        return switch (interpretation.contract().type()) {
            case OBTAIN_RESOURCE -> interpretation.fulfillment().styles().contains(FulfillmentStyle.SPATIAL_ABSURDITY)
                    || interpretation.fulfillment().styles().contains(FulfillmentStyle.PHYSICAL_ABSURDITY)
                    ? WishOmenCategory.WORLD : WishOmenCategory.GIFT;
            case CREATE_STRUCTURE, CHANGE_WORLD_STATE -> WishOmenCategory.WORLD;
            case CHANGE_PLAYER_STATE, PERSISTENT_CONDITION, RESURRECTION -> WishOmenCategory.POWER;
            case SPAWN_COMPANION -> WishOmenCategory.STALKING;
            case REMOVE_THREAT -> WishOmenCategory.HOSTILE;
            case TRAVEL -> WishOmenCategory.TELEPORT;
            case SOCIAL_RELATION -> WishOmenCategory.REPUTATION;
            case KNOWLEDGE, OTHER -> WishOmenCategory.GENERAL;
        };
    }

    private static WishOmenCategory categoryFor(WishCapability capability, WishActionType action) {
        if (capability != null) {
            return switch (capability) {
                case STALKING_ENTITY, PERSISTENT_FOLLOWER, MIMIC_ENTITY, IMITATION -> WishOmenCategory.STALKING;
                case HOSTILE_ENTITY, POWERFUL_ENEMY, ENTITY_RECREATION -> WishOmenCategory.HOSTILE;
                case HALLUCINATION, VISUAL_EVENT, MEMORY_RELATED_EVENT -> WishOmenCategory.HALLUCINATION;
                case DAMAGE, POWER_DEBUFF -> WishOmenCategory.DAMAGE;
                case REMOVE_ITEM, INVENTORY_CHANGE -> WishOmenCategory.LOSS;
                case TELEPORT, DIMENSION_TRAVEL, SPACE_TRAVEL -> WishOmenCategory.TELEPORT;
                case CHANGE_TIME, DARKNESS -> WishOmenCategory.DARKNESS;
                case CHANGE_WEATHER, LIGHTNING -> WishOmenCategory.WEATHER;
                case GIVE_ITEM, STRONG_WEAPON -> WishOmenCategory.GIFT;
                case POWER_BUFF, PLAYER_ATTRIBUTE, IMMORTALITY, HEALING -> WishOmenCategory.POWER;
                case REPUTATION -> WishOmenCategory.REPUTATION;
                case WORLD_EVENT, STRUCTURE, EXPLOSION, BLOCK_CHANGE, SOUND_EVENT -> WishOmenCategory.WORLD;
                default -> categoryForAction(action);
            };
        }
        return categoryForAction(action);
    }

    private static WishOmenCategory categoryForAction(WishActionType action) {
        if (action == null) {
            return WishOmenCategory.GENERAL;
        }
        return switch (action) {
            case GIVE_ITEM -> WishOmenCategory.GIFT;
            case REMOVE_ITEM -> WishOmenCategory.LOSS;
            case TELEPORT -> WishOmenCategory.TELEPORT;
            case CHANGE_TIME -> WishOmenCategory.DARKNESS;
            case CHANGE_WEATHER, LIGHTNING -> WishOmenCategory.WEATHER;
            case MODIFY_HEALTH, REMOVE_EFFECT -> WishOmenCategory.DAMAGE;
            case APPLY_EFFECT, APPLY_EFFECT_CATEGORY, CLEAR_EFFECTS, MODIFY_ATTRIBUTE -> WishOmenCategory.POWER;
            case CHANGE_REPUTATION -> WishOmenCategory.REPUTATION;
            case FOLLOW_PLAYER, CHANGE_MOB_TARGET, SPAWN_ENTITY -> WishOmenCategory.HOSTILE;
            case SPAWN_PARTICLE -> WishOmenCategory.HALLUCINATION;
            case EXPLOSION, CHANGE_BLOCK, REPLACE_BLOCK_AREA, PLACE_BLOCK_PATTERN, CREATE_STRUCTURE, START_PREDEFINED_EVENT, ENTITY_ATTRACTION_AURA -> WishOmenCategory.WORLD;
            default -> WishOmenCategory.GENERAL;
        };
    }

    private static int priority(WishOmenCategory category) {
        return PRIORITY.get(category);
    }

    private static long stableSeed(UUID sessionId, WishOmenCategory category, WishDelivery delivery) {
        long value = sessionId.getMostSignificantBits() ^ Long.rotateLeft(sessionId.getLeastSignificantBits(), 23);
        value ^= 0x9E3779B97F4A7C15L * (category.ordinal() + 1L);
        value ^= 0xC2B2AE3D27D4EB4FL * (delivery.ordinal() + 1L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static Map<WishOmenCategory, Integer> priorityMap() {
        EnumMap<WishOmenCategory, Integer> priorities = new EnumMap<>(WishOmenCategory.class);
        WishOmenCategory[] ordered = {
                WishOmenCategory.STALKING, WishOmenCategory.HOSTILE, WishOmenCategory.HALLUCINATION,
                WishOmenCategory.DAMAGE, WishOmenCategory.LOSS, WishOmenCategory.TELEPORT,
                WishOmenCategory.DARKNESS, WishOmenCategory.WEATHER, WishOmenCategory.GIFT,
                WishOmenCategory.POWER, WishOmenCategory.REPUTATION, WishOmenCategory.WORLD,
                WishOmenCategory.GENERAL
        };
        for (int index = 0; index < ordered.length; index++) {
            priorities.put(ordered[index], index);
        }
        return Map.copyOf(priorities);
    }

    private static Map<WishOmenCategory, Integer> poolSizes() {
        EnumMap<WishOmenCategory, Integer> sizes = new EnumMap<>(WishOmenCategory.class);
        for (WishOmenCategory category : WishOmenCategory.values()) {
            sizes.put(category, category == WishOmenCategory.GENERAL ? 5 : 4);
        }
        return Map.copyOf(sizes);
    }
}
