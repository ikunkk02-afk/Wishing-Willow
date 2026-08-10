package com.ikunkk02.wishingwillow.execution;

import com.ikunkk02.wishingwillow.WishingWillow;
import com.ikunkk02.wishingwillow.planning.WishActionType;

public final class WishExecutionAudit {
    private WishExecutionAudit(){}
    public static void transition(WishExecutionRecord record,int step,WishActionType action,String status,String code){
        WishingWillow.LOGGER.info("Wish execution audit execution={} wish={} step={} action={} status={} code={}",
                record.executionId(),record.wishSessionId(),step,action,status,code==null?"":code);
    }
}
