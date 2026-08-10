package com.ikunkk02.wishingwillow.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

public final class SafeSpawnPositionFinder {
    private SafeSpawnPositionFinder(){}
    @Nullable public static Vec3 find(ServerLevel level,Entity entity,Vec3 origin,int min,int max,boolean behind,float yaw,long seed){
        Random random=new Random(seed);int low=Math.max(2,min),high=Math.max(low,Math.min(128,max));
        for(int attempt=0;attempt<12;attempt++){
            double angle=behind?Math.toRadians(yaw+180+(random.nextDouble()-0.5)*100):random.nextDouble()*Math.PI*2;
            double distance=low+random.nextDouble()*(high-low);
            int x=(int)Math.floor(origin.x+Math.sin(-angle)*distance),z=(int)Math.floor(origin.z+Math.cos(angle)*distance);
            BlockPos probe=new BlockPos(x,(int)origin.y,z);
            if(!level.hasChunkAt(probe))continue;
            int start=Math.min(level.getMaxBuildHeight()-2,(int)origin.y+6),bottom=Math.max(level.getMinBuildHeight()+1,(int)origin.y-10);
            for(int y=start;y>=bottom;y--){BlockPos feet=new BlockPos(x,y,z);if(!level.getBlockState(feet.below()).isCollisionShapeFullBlock(level,feet.below()))continue;AABB box=entity.getType().getDimensions().makeBoundingBox(x+0.5,y,z+0.5);if(level.noCollision(entity,box)&&level.getFluidState(feet).isEmpty())return new Vec3(x+0.5,y,z+0.5);}
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);if(y<=level.getMinBuildHeight()+1||y>=level.getMaxBuildHeight()-2)continue;BlockPos feet=new BlockPos(x,y,z);AABB box=entity.getType().getDimensions().makeBoundingBox(x+0.5,y,z+0.5);if(level.getBlockState(feet.below()).isCollisionShapeFullBlock(level,feet.below())&&level.noCollision(entity,box)&&level.getFluidState(feet).isEmpty())return new Vec3(x+0.5,y,z+0.5);
        }
        return null;
    }

    @Nullable public static Vec3 findPlayer(ServerLevel level,Vec3 origin,int min,int max,long seed){
        Random random=new Random(seed);int low=Math.max(2,min),high=Math.max(low,Math.min(4096,max));
        for(int attempt=0;attempt<12;attempt++){
            double angle=random.nextDouble()*Math.PI*2,distance=low+random.nextDouble()*(high-low);
            int x=(int)Math.floor(origin.x+Math.cos(angle)*distance),z=(int)Math.floor(origin.z+Math.sin(angle)*distance);
            BlockPos probe=new BlockPos(x,(int)origin.y,z);if(!level.hasChunkAt(probe))continue;
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);BlockPos feet=new BlockPos(x,y,z);
            if(y>level.getMinBuildHeight()&&y<level.getMaxBuildHeight()-2&&level.getFluidState(feet).isEmpty()
                    &&level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
                    &&level.getBlockState(feet.above()).getCollisionShape(level,feet.above()).isEmpty())return new Vec3(x+0.5,y,z+0.5);
        }return null;
    }
}
