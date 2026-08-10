package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.planning.WishActionType;

public final class WishExecutionAudit {
    private WishExecutionAudit(){}
    public static void transition(WishExecutionRecord record,int step,WishActionType action,String status,String code,int affected){
        WishingWillow.LOGGER.info("Wish pipeline session={} stage=STEP execution={} step={} action={} status={} code={} affected={}",
                record.wishSessionId(),record.executionId(),step,action,status,code==null?"":code,affected);
    }
}
