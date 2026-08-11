package com.ikunkk02.wishingwillow.execution.action;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.FallingBlockShowerProgress;
import com.ikunkk02.wishingwillow.execution.WishActionResult;
import com.ikunkk02.wishingwillow.execution.WishExecutionContext;
import com.ikunkk02.wishingwillow.execution.WishExecutionSavedData;
import com.ikunkk02.wishingwillow.planning.WishPlanBudget;
import com.ikunkk02.wishingwillow.research.RegistryEntryType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates and tracks real FallingBlockEntity instances in bounded per-tick batches. */
final class FallingBlockShowerExecutor implements WishActionExecutor {
    private static final int MAX_ACTIVE_PER_SHOWER = 48;
    private static final Set<String> LANDING_MODES = Set.of(
            "PLACE", "DROP_ITEM", "PLACE_OR_DROP", "DELIVER_TO_PLAYER");

    @Override
    public WishActionResult validate(WishExecutionContext context) {
        if (context.player() == null) return WishActionResult.retry("PLAYER_OFFLINE");
        Block block = block(context);
        if (block == null) return WishActionResult.stale("BLOCK_NOT_FOUND");
        if (!fallableResource(block)) return WishActionResult.failed("BLOCK_CANNOT_FALL_SAFELY");
        String landing = string(context, "landing_mode");
        return LANDING_MODES.contains(landing) ? WishActionResult.success(0)
                : WishActionResult.failed("INVALID_LANDING_MODE");
    }

    @Override
    public WishActionResult execute(WishExecutionContext context) {
        ServerPlayer player = context.player();
        if (player == null) return WishActionResult.retry("PLAYER_OFFLINE");
        Block block = block(context);
        if (block == null) return WishActionResult.stale("BLOCK_NOT_FOUND");
        if (!fallableResource(block)) return WishActionResult.failed("BLOCK_CANNOT_FALL_SAFELY");

        int stepIndex = context.step().stepIndex();
        int requested = integer(context, "count");
        int interval = integer(context, "interval_ticks");
        FallingBlockShowerProgress progress = context.execution().fallingBlockShower(stepIndex);
        if (progress.spawned() == 0 && progress.activeCount() == 0) {
            WishingWillow.LOGGER.info("Falling block shower started session={} requested={}",
                    context.plan().wishSessionId(), requested);
        }

        settleFinished(context, block, progress);
        long gameTime = context.level().getGameTime();
        if (progress.spawned() < requested && progress.activeCount() < MAX_ACTIVE_PER_SHOWER
                && globalActive(context) < WishPlanBudget.MAX_ACTIVE_FALLING_BLOCKS
                && Math.floorMod(gameTime, interval) == 0) {
            int available = Math.min(WishPlanBudget.MAX_FALLING_BLOCKS_PER_TICK,
                    Math.min(requested - progress.spawned(), MAX_ACTIVE_PER_SHOWER - progress.activeCount()));
            for (int index = 0; index < available; index++) {
                BlockPos position = spawnPosition(context);
                if (position == null || !spawn(context, block.defaultBlockState(), position, progress)) break;
            }
        }

        if (progress.spawned() >= requested && progress.activeCount() == 0) {
            WishingWillow.LOGGER.info(
                    "Falling block shower completed session={} spawned={} delivered={} failed={}",
                    context.plan().wishSessionId(), progress.spawned(), progress.delivered(), progress.failed());
            return progress.delivered() >= requested
                    ? WishActionResult.success(progress.delivered())
                    : WishActionResult.partial("FALLING_BLOCK_DELIVERY_INCOMPLETE", progress.delivered());
        }
        return WishActionResult.retry("BLOCK_BATCH_CONTINUE");
    }

    private static void settleFinished(WishExecutionContext context, Block block,
                                       FallingBlockShowerProgress progress) {
        for (Map.Entry<UUID, BlockPos> tracked : progress.active().entrySet()) {
            Entity entity = context.level().getEntity(tracked.getKey());
            if (entity instanceof FallingBlockEntity falling && !falling.isRemoved()) {
                progress.update(tracked.getKey(), falling.blockPosition());
                continue;
            }
            String mode = string(context, "landing_mode");
            boolean delivered = switch (mode) {
                case "DELIVER_TO_PLAYER" -> deliverToPlayer(context.player(), block);
                case "DROP_ITEM" -> dropItem(context, block, tracked.getValue());
                case "PLACE_OR_DROP" -> placedNearby(context.level(), block, tracked.getValue())
                        || dropItem(context, block, tracked.getValue());
                case "PLACE" -> placedNearby(context.level(), block, tracked.getValue());
                default -> false;
            };
            progress.settle(tracked.getKey(), delivered);
        }
    }

    private static boolean spawn(WishExecutionContext context, BlockState state, BlockPos position,
                                 FallingBlockShowerProgress progress) {
        ServerLevel level = context.level();
        if (!level.setBlock(position, state, Block.UPDATE_CLIENTS)) return false;
        FallingBlockEntity entity = FallingBlockEntity.fall(level, position, state);
        entity.dropItem = false;
        String mode = string(context, "landing_mode");
        if ("DELIVER_TO_PLAYER".equals(mode) || "DROP_ITEM".equals(mode)) entity.disableDrop();
        progress.track(entity.getUUID(), position);
        context.execution().bindEntity(context.step().stepIndex(), entity.getUUID());
        return true;
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

    private static boolean placedNearby(ServerLevel level, Block block, BlockPos center) {
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-1, -2, -1), center.offset(1, 1, 1))) {
            if (level.hasChunkAt(position) && level.getBlockState(position).is(block)) return true;
        }
        return false;
    }

    private static boolean deliverToPlayer(ServerPlayer player, Block block) {
        ItemStack stack = new ItemStack(block.asItem());
        if (stack.isEmpty()) return false;
        return player.addItem(stack) || player.drop(stack, false) != null;
    }

    private static boolean dropItem(WishExecutionContext context, Block block, BlockPos preferred) {
        ItemStack stack = new ItemStack(block.asItem());
        if (stack.isEmpty()) return false;
        BlockPos position = context.level().hasChunkAt(preferred)
                && context.level().getWorldBorder().isWithinBounds(preferred)
                ? preferred : context.player().blockPosition();
        ItemEntity item = new ItemEntity(context.level(), position.getX() + .5,
                position.getY() + .5, position.getZ() + .5, stack);
        item.setDefaultPickUpDelay();
        return context.level().addFreshEntity(item);
    }

    private static int globalActive(WishExecutionContext context) {
        return WishExecutionSavedData.get(context.level().getServer()).all().stream()
                .flatMap(record -> record.fallingBlockShowers().values().stream())
                .mapToInt(FallingBlockShowerProgress::activeCount).sum();
    }

    private static Block block(WishExecutionContext context) {
        if (context.candidate().registryResource() == null
                || context.candidate().registryResource().type() != RegistryEntryType.BLOCK) return null;
        ResourceLocation id = ResourceLocation.tryParse(context.candidate().registryResource().id());
        return id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
    }

    private static boolean fallableResource(Block block) {
        BlockState state = block.defaultBlockState();
        return !state.isAir() && block.asItem() != Items.AIR && !state.hasBlockEntity();
    }

    private static int integer(WishExecutionContext context, String key) {
        return context.step().parameters().get(key).getAsInt();
    }

    private static String string(WishExecutionContext context, String key) {
        return context.step().parameters().get(key).getAsString();
    }
}
