package com.ikunkk02.wishingwillow.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WishContextCollector {
    private static final double NEARBY_RADIUS = 64.0;

    private WishContextCollector() { }

    /** Must be called on the logical server thread. Exact X/Z coordinates are never retained. */
    public static WishContextSnapshot collect(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        Map<String, Integer> counts = new HashMap<>();
        int hostile = 0;
        int passive = 0;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(NEARBY_RADIUS),
                entity -> entity != player)) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (id != null) counts.merge(id.toString(), 1, Integer::sum);
            if (entity instanceof Enemy) hostile++;
            if (entity instanceof AgeableMob) passive++;
        }
        List<WishContextSnapshot.NearbyEntitySummary> nearby = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(20).map(entry -> new WishContextSnapshot.NearbyEntitySummary(entry.getKey(), entry.getValue()))
                .toList();
        List<String> armor = new ArrayList<>();
        for (ItemStack stack : player.getArmorSlots()) armor.add(itemId(stack));
        String biome = level.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        long dayTime = level.getDayTime() % 24000L;
        String dayPhase = dayTime < 1000 ? "DAWN" : dayTime < 12000 ? "DAY"
                : dayTime < 13000 ? "DUSK" : "NIGHT";
        String weather = level.isThundering() ? "THUNDER" : level.isRaining() ? "RAIN" : "CLEAR";
        int approximateY = Math.floorDiv(pos.getY(), 8) * 8;
        String dimension = level.dimension().location().toString();
        String environment = dimension.equals("minecraft:the_nether") ? "NETHER"
                : dimension.equals("minecraft:the_end") ? "END"
                : !level.canSeeSky(pos) ? (pos.getY() < level.getSeaLevel() - 16 ? "CAVE" : "UNDERGROUND")
                : "SURFACE";
        return new WishContextSnapshot(dimension, level.getGameTime(), dayPhase, weather,
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(),
                player.experienceLevel, player.gameMode.getGameModeForPlayer().getName(), biome,
                approximateY, environment, itemId(player.getMainHandItem()), armor, nearby, hostile, passive);
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }
}
