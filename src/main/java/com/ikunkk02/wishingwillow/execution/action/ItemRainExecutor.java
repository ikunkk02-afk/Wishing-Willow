package com.ikunkk02.wishingwillow.execution.action;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.ItemRainProgress;
import com.ikunkk02.wishingwillow.execution.WishActionResult;
import com.ikunkk02.wishingwillow.execution.WishExecutionSavedData;
import com.ikunkk02.wishingwillow.planning.WishPlanBudget;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.UUID;

/** Spawns real ItemEntity objects above the target in bounded, resumable batches. */
final class ItemRainExecutor implements WishActionExecutor {
    private static final Set<String> DELIVERY_MODES = Set.of("WORLD_ITEMS", "DELIVER_TO_PLAYER");
    private static final long DELIVERY_WINDOW_TICKS = 100L;

    @Override
    public WishActionResult validate(WishExecutionContext context) {
        if (context.player() == null) return WishActionResult.retry("PLAYER_OFFLINE");
        Item item = item(context);
        if (item == null || item == Items.AIR) return WishActionResult.stale("ITEM_NOT_FOUND");
        String delivery = string(context, "delivery_mode");
        if (!DELIVERY_MODES.contains(delivery)) return WishActionResult.failed("INVALID_DELIVERY_MODE");
        int requested = integer(context, "count");
        if (requested < 1 || requested > WishPlanBudget.MAX_ITEM_UNITS) {
            return WishActionResult.failed("ITEM_UNIT_BUDGET_EXCEEDED");
        }
        int minimumEntities = divideRoundUp(requested, Math.max(1, item.getMaxStackSize()));
        return minimumEntities <= WishPlanBudget.MAX_ACTIVE_ITEM_RAIN_ENTITIES
                ? WishActionResult.success(0)
                : WishActionResult.failed("ITEM_ENTITY_BUDGET_EXCEEDED");
    }

    @Override
    public WishActionResult execute(WishExecutionContext context) {
        ServerPlayer player = context.player();
        if (player == null) return WishActionResult.retry("PLAYER_OFFLINE");
        Item item = item(context);
        if (item == null || item == Items.AIR) return WishActionResult.stale("ITEM_NOT_FOUND");
        int requested = integer(context, "count");
        int bundleSize = bundleSize(requested, item.getMaxStackSize());
        if (divideRoundUp(requested, Math.max(1, item.getMaxStackSize()))
                > WishPlanBudget.MAX_ACTIVE_ITEM_RAIN_ENTITIES) {
            return WishActionResult.failed("ITEM_ENTITY_BUDGET_EXCEEDED");
        }

        ItemRainProgress progress = context.execution().itemRain(context.stepIndex());
        if (progress.start(player.getInventory().countItem(item))) {
            WishingWillow.LOGGER.info("Item rain started session={} item={} requestedUnits={}",
                    context.wishSessionId(), ForgeRegistries.ITEMS.getKey(item), requested);
        }

        int batchEntities = 0;
        int batchUnits = 0;
        long gameTime = context.level().getGameTime();
        int interval = integer(context, "interval_ticks");
        if (progress.spawnedUnits() < requested && Math.floorMod(gameTime, interval) == 0) {
            int capacity = Math.max(0, WishPlanBudget.MAX_ACTIVE_ITEM_RAIN_ENTITIES - globalActive(context));
            int limit = Math.min(WishPlanBudget.MAX_ITEM_ENTITIES_PER_TICK, capacity);
            for (int index = 0; index < limit && progress.spawnedUnits() < requested; index++) {
                BlockPos position = spawnPosition(context);
                if (position == null) break;
                int units = Math.min(bundleSize, requested - progress.spawnedUnits());
                ItemEntity entity = new ItemEntity(context.level(), position.getX() + .5,
                        position.getY() + .5, position.getZ() + .5, new ItemStack(item, units));
                entity.getPersistentData().putUUID(ItemRainProgress.ENTITY_SESSION_TAG,
                        context.wishSessionId());
                if ("WORLD_ITEMS".equals(string(context, "delivery_mode"))) entity.setPickUpDelay(40);
                else {
                    entity.setDefaultPickUpDelay();
                    entity.setTarget(player.getUUID());
                }
                entity.setDeltaMovement((context.level().random.nextDouble() - .5) * .08, 0,
                        (context.level().random.nextDouble() - .5) * .08);
                if (!context.level().addFreshEntity(entity)) break;
                progress.track(entity.getUUID(), units);
                context.execution().bindEntity(context.stepIndex(), entity.getUUID());
                batchEntities++;
                batchUnits += units;
            }
        }
        if (batchEntities > 0) {
            WishingWillow.LOGGER.debug(
                    "Item rain batch session={} spawnedUnits={} batchUnits={} batchEntities={} activeEntities={} remainingUnits={}",
                    context.wishSessionId(), progress.spawnedUnits(), batchUnits, batchEntities,
                    globalActive(context), requested - progress.spawnedUnits());
        }

        if (progress.spawnedUnits() < requested) return WishActionResult.retryNextTick();
        progress.allSpawned(gameTime);
        if ("WORLD_ITEMS".equals(string(context, "delivery_mode"))) {
            progress.complete(0, 0);
            WishingWillow.LOGGER.info(
                    "Item rain completed session={} requestedUnits={} spawnedUnits={} deliveredUnits=0 failedUnits=0",
                    context.wishSessionId(), requested, progress.spawnedUnits());
            return WishActionResult.success(progress.spawnedUnits());
        }
        if (gameTime - progress.allSpawnedGameTime() < DELIVERY_WINDOW_TICKS) {
            return WishActionResult.retryNextTick();
        }
        int delivered = deliverRemaining(context, item, requested, progress);
        int failed = Math.max(0, requested - delivered);
        progress.complete(delivered, failed);
        WishingWillow.LOGGER.info(
                "Item rain completed session={} requestedUnits={} spawnedUnits={} deliveredUnits={} failedUnits={}",
                context.wishSessionId(), requested, progress.spawnedUnits(), delivered, failed);
        return failed == 0 ? WishActionResult.success(delivered)
                : WishActionResult.partial("ITEM_RAIN_DELIVERY_INCOMPLETE", delivered);
    }

    private static int deliverRemaining(WishExecutionContext context, Item item, int requested,
                                        ItemRainProgress progress) {
        ServerPlayer player = context.player();
        int natural = Math.max(0, player.getInventory().countItem(item) - progress.initialInventoryUnits());
        int delivered = Math.min(requested, natural);
        int needed = requested - delivered;
        for (UUID id : progress.active().keySet()) {
            if (needed <= 0) break;
            ItemEntity entity = findItemEntity(context.level().getServer(), id);
            if (entity == null || entity.isRemoved() || !entity.getItem().is(item)) continue;
            int offered = Math.min(needed, entity.getItem().getCount());
            int accepted = deliverStack(context, item, offered, progress);
            if (accepted <= 0) continue;
            entity.getItem().shrink(accepted);
            if (entity.getItem().isEmpty()) entity.discard();
            delivered += accepted;
            needed -= accepted;
        }
        while (needed > 0) {
            int offered = Math.min(needed, item.getMaxStackSize());
            int accepted = deliverStack(context, item, offered, progress);
            if (accepted <= 0) break;
            delivered += accepted;
            needed -= accepted;
        }
        return delivered;
    }

    private static int deliverStack(WishExecutionContext context, Item item, int units,
                                    ItemRainProgress progress) {
        ServerPlayer player = context.player();
        ItemStack stack = new ItemStack(item, units);
        player.getInventory().add(stack);
        int delivered = units - stack.getCount();
        if (!stack.isEmpty()) {
            ItemEntity dropped = player.drop(stack.copy(), false);
            if (dropped != null) {
                dropped.getPersistentData().putUUID(ItemRainProgress.ENTITY_SESSION_TAG,
                        context.wishSessionId());
                progress.trackReplacement(dropped.getUUID(), stack.getCount());
                context.execution().bindEntity(context.stepIndex(), dropped.getUUID());
                delivered += stack.getCount();
            }
        }
        return delivered;
    }

    private static BlockPos spawnPosition(WishExecutionContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        int height = integer(context, "spawn_height");
        int radius = integer(context, "radius");
        int y = Math.min(level.getMaxBuildHeight() - 2,
                Math.max(level.getMinBuildHeight() + 2, player.blockPosition().getY() + height));
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = player.blockPosition().getX() + level.random.nextInt(radius * 2 + 1) - radius;
            int z = player.blockPosition().getZ() + level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos candidate = new BlockPos(x, y, z);
            if (level.getWorldBorder().isWithinBounds(candidate) && level.hasChunkAt(candidate)
                    && level.isEmptyBlock(candidate)) return candidate;
        }
        return null;
    }

    private static int globalActive(WishExecutionContext context) {
        MinecraftServer server = context.level().getServer();
        return (int) WishExecutionSavedData.get(server).all().stream()
                .flatMap(record -> record.itemRains().values().stream())
                .flatMap(progress -> progress.active().keySet().stream())
                .filter(id -> {
                    ItemEntity entity = findItemEntity(server, id);
                    return entity != null && !entity.isRemoved();
                }).count();
    }

    private static ItemEntity findItemEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof ItemEntity item) return item;
        }
        return null;
    }

    private static Item item(WishExecutionContext context) {
        if (context.candidate() == null || context.candidate().registryResource() == null
                || context.candidate().registryResource().type() != RegistryEntryType.ITEM) return null;
        ResourceLocation id = ResourceLocation.tryParse(context.candidate().registryResource().id());
        return id == null ? null : ForgeRegistries.ITEMS.getValue(id);
    }

    private static int bundleSize(int requested, int maxStackSize) {
        if (requested <= 64) return 1;
        return Math.min(Math.max(1, maxStackSize),
                Math.max(1, divideRoundUp(requested, WishPlanBudget.MAX_ACTIVE_ITEM_RAIN_ENTITIES)));
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int integer(WishExecutionContext context, String key) {
        return context.parameters().get(key).getAsInt();
    }

    private static String string(WishExecutionContext context, String key) {
        return context.parameters().get(key).getAsString();
    }
}