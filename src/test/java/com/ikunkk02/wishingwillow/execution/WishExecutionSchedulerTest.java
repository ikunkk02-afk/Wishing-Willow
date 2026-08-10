package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.planning.WishTriggerType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WishExecutionSchedulerTest {
    @Test void ordersDelaysAndIndexesTriggers(){WishExecutionScheduler scheduler=new WishExecutionScheduler();var late=new WishExecutionScheduler.StepKey(UUID.randomUUID(),1);var early=new WishExecutionScheduler.StepKey(UUID.randomUUID(),0);scheduler.delay(late,200);scheduler.delay(early,100);assertEquals(early,scheduler.due(100,8).get(0));assertTrue(scheduler.due(199,8).isEmpty());assertEquals(late,scheduler.due(200,8).get(0));scheduler.trigger(early,WishTriggerType.PLAYER_SLEEP);assertEquals(1,scheduler.waiting(WishTriggerType.PLAYER_SLEEP).size());assertEquals(early,scheduler.fire(WishTriggerType.PLAYER_SLEEP,k->true).get(0));assertEquals(0,scheduler.scheduledCount());}
}
