package com.ikunkk02.wishingwillow.wish;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.execution.WishExecutionAcceptError;
import com.ikunkk02.wishingwillow.execution.WishExecutionManager;
import com.ikunkk02.wishingwillow.execution.WishExecutionRecord;
import com.ikunkk02.wishingwillow.execution.WishExecutionSavedData;
import com.ikunkk02.wishingwillow.execution.WishExecutionState;
import com.ikunkk02.wishingwillow.planning.WishPlanState;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

public final class WishPipelineConsistencyChecker {
    private static boolean registered;

    private WishPipelineConsistencyChecker() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(WishPipelineConsistencyChecker::onStarted);
        MinecraftForge.EVENT_BUS.addListener(WishPipelineConsistencyChecker::onTick);
    }

    public static void check(MinecraftServer server) {
        WishSavedData wishes=WishSavedData.get(server);
        WishExecutionSavedData executions=WishExecutionSavedData.get(server);
        for(WishRecord wish:wishes.allRecords()){
            if((wish.planState()!=WishPlanState.READY&&wish.planState()!=WishPlanState.PARTIAL)||wish.plan()==null)continue;
            if(wish.executionId()!=null)continue;
            WishExecutionRecord existing=executions.byPlan(wish.plan().planId());
            if(existing==null)existing=executions.bySession(wish.sessionId());
            if(existing!=null&&existing.planId().equals(wish.plan().planId())
                    &&existing.wishSessionId().equals(wish.sessionId())&&existing.ownerId().equals(wish.playerId())){
                wishes.update(wish.withExecution(existing.executionId(),existing.state(),WishExecutionAcceptError.NONE,""));
                WishPipelineAudit.success(wish.sessionId(),"CONSISTENCY_RECOVERY",
                        "backfilled execution="+existing.executionId());
                continue;
            }
            if(wish.executionState()!=WishExecutionState.NOT_ACCEPTED)continue;
            WishingWillow.LOGGER.warn("Wish pipeline session={} error=PIPELINE_ORPHANED_READY_PLAN plan={} state={}",
                    wish.sessionId(),wish.plan().planId(),wish.planState());
            WishExecutionManager.acceptStored(server,wish);
        }
    }

    private static void onStarted(ServerStartedEvent event) {
        check(event.getServer());
    }

    private static void onTick(TickEvent.ServerTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.getServer().overworld().getGameTime()%200L==0L)
            check(event.getServer());
    }
}
