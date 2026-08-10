package com.ikunkk02.wishingwillow.client.planning;

import com.ikunkk02.wishingwillow.planning.WishContextSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientWishContextCollector {
    private ClientWishContextCollector() { }

    public static WishContextSnapshot collect() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) throw new IllegalStateException("NO_WORLD");
        Map<String,Integer> counts=new HashMap<>(); int hostile=0,passive=0;
        for(Entity entity:level.getEntities(player,player.getBoundingBox().inflate(64),e->e!=player)) {
            ResourceLocation id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()); if(id!=null)counts.merge(id.toString(),1,Integer::sum);
            if(entity instanceof Enemy)hostile++; if(entity instanceof AgeableMob)passive++;
        }
        List<WishContextSnapshot.NearbyEntitySummary> nearby=counts.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).limit(20)
                .map(e->new WishContextSnapshot.NearbyEntitySummary(e.getKey(),e.getValue())).toList();
        List<String> armor=new ArrayList<>(); for(ItemStack stack:player.getArmorSlots())armor.add(item(stack));
        var pos=player.blockPosition(); long day=level.getDayTime()%24000L;
        String phase=day<1000?"DAWN":day<12000?"DAY":day<13000?"DUSK":"NIGHT";
        String dimension=level.dimension().location().toString();
        String environment=dimension.equals("minecraft:the_nether")?"NETHER":dimension.equals("minecraft:the_end")?"END"
                :!level.canSeeSky(pos)?(pos.getY()<level.getSeaLevel()-16?"CAVE":"UNDERGROUND"):"SURFACE";
        String biome=level.getBiome(pos).unwrapKey().map(k->k.location().toString()).orElse("unknown");
        String mode=minecraft.gameMode==null?"unknown":minecraft.gameMode.getPlayerMode().getName();
        return new WishContextSnapshot(dimension,level.getGameTime(),phase,level.getThunderLevel(1)>0.5?"THUNDER":level.getRainLevel(1)>0.1?"RAIN":"CLEAR",
                player.getHealth(),player.getMaxHealth(),player.getFoodData().getFoodLevel(),player.experienceLevel,mode,biome,
                Math.floorDiv(pos.getY(),8)*8,environment,item(player.getMainHandItem()),armor,nearby,hostile,passive);
    }

    private static String item(ItemStack stack){if(stack.isEmpty())return "empty";ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());return id==null?"unknown":id.toString();}
}
